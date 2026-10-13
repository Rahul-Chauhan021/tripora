package com.rahul.tripora_Backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.tripora_Backend.entity.EmailOtp;
import com.rahul.tripora_Backend.entity.User;
import com.rahul.tripora_Backend.enums.UserStatus;
import com.rahul.tripora_Backend.exception.OtpException;
import com.rahul.tripora_Backend.exception.OtpException.ErrorCode;
import com.rahul.tripora_Backend.exception.ResendCooldownException;
import com.rahul.tripora_Backend.repository.EmailOtpRepository;
import com.rahul.tripora_Backend.repository.UserRepository;
import com.rahul.tripora_Backend.service.OtpService;

/**
 * OTP lifecycle implementation.
 *
 * Security properties:
 * - The OTP is a cryptographically secure six-digit random value.
 * - Only its SHA-256 hash is persisted; the plaintext leaves this class only
 *   through the return value that the caller passes to the email service.
 * - Issuing a new OTP atomically invalidates every previous active OTP.
 * - Failed attempts are counted with an atomic conditional UPDATE, so
 *   concurrent requests cannot exceed the attempt limit.
 * - Marking an OTP as used is also a conditional UPDATE, so an OTP can be
 *   redeemed exactly once even under concurrency.
 * - Verification and account activation happen inside one transaction.
 */
@Service
public class OtpServiceImpl implements OtpService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final EmailOtpRepository emailOtpRepository;
	private final UserRepository userRepository;

	private final long expiryMinutes;
	private final int maxAttempts;
	private final long resendCooldownSeconds;
	private final long maxResendsPerHour;

	public OtpServiceImpl(EmailOtpRepository emailOtpRepository,
			UserRepository userRepository,
			@Value("${app.otp.expiry-minutes}") long expiryMinutes,
			@Value("${app.otp.max-attempts}") int maxAttempts,
			@Value("${app.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
			@Value("${app.otp.max-resends-per-hour}") long maxResendsPerHour) {
		this.emailOtpRepository = emailOtpRepository;
		this.userRepository = userRepository;
		this.expiryMinutes = expiryMinutes;
		this.maxAttempts = maxAttempts;
		this.resendCooldownSeconds = resendCooldownSeconds;
		this.maxResendsPerHour = maxResendsPerHour;
	}

	@Override
	@Transactional
	public String issueOtp(User user) {
		emailOtpRepository.deactivateOtpsForUser(user.getId());
		String otp = generateOtp();
		Instant now = Instant.now();
		EmailOtp emailOtp = EmailOtp.builder()
				.user(user)
				.otpHash(hashOtp(otp))
				.expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
				.attempts(0)
				.used(false)
				.createdAt(now)
				.resendAvailableAt(now.plus(resendCooldownSeconds, ChronoUnit.SECONDS))
				.build();
		emailOtpRepository.save(emailOtp);
		return otp;
	}

	@Override
	@Transactional
	public void verifyAndActivate(String email, String rawOtp) {
		String genericMessage = "Invalid verification code. Please check the code and try again.";

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new OtpException(ErrorCode.INVALID_OTP, genericMessage));

		if (user.isEmailVerified() || user.getStatus() == UserStatus.ACTIVE) {
			throw new OtpException(ErrorCode.ALREADY_VERIFIED, "This email address is already verified.");
		}

		EmailOtp otp = emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc(email)
				.filter(candidate -> !candidate.isUsed())
				.orElseThrow(() -> new OtpException(ErrorCode.INVALID_OTP, genericMessage));

		if (otp.getExpiresAt().isBefore(Instant.now())) {
			throw new OtpException(ErrorCode.OTP_EXPIRED,
					"This verification code has expired. Please request a new one.");
		}

		// Atomically consume one attempt slot; 0 rows means the OTP is used
		// up or the attempt limit has been reached.
		int consumed = emailOtpRepository.incrementAttemptsIfActive(otp.getId(), maxAttempts);
		if (consumed == 0) {
			throw new OtpException(ErrorCode.TOO_MANY_ATTEMPTS,
					"Too many incorrect attempts. Please request a new verification code.");
		}

		if (!constantTimeEquals(hashOtp(rawOtp), otp.getOtpHash())) {
			throw new OtpException(ErrorCode.INVALID_OTP, genericMessage);
		}

		// Conditional update: only one concurrent request can win this race.
		int marked = emailOtpRepository.markUsedIfActive(otp.getId());
		if (marked == 0) {
			throw new OtpException(ErrorCode.INVALID_OTP, genericMessage);
		}

		user.setEmailVerified(true);
		user.setStatus(UserStatus.ACTIVE);
		user.setVerifiedAt(Instant.now());
		userRepository.save(user);
	}

	@Override
	@Transactional(readOnly = true)
	public void enforceResendCooldown(User user) {
		emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc(user.getEmail())
				.filter(candidate -> !candidate.isUsed())
				.ifPresent(candidate -> {
					long waitSeconds = Duration.between(Instant.now(), candidate.getResendAvailableAt()).getSeconds();
					if (waitSeconds > 0) {
						throw new ResendCooldownException(waitSeconds,
								"Please wait " + waitSeconds + " seconds before requesting a new code.");
					}
				});
	}

	@Override
	@Transactional(readOnly = true)
	public void enforceResendRateLimit(User user) {
		Instant windowStart = Instant.now().minus(1, ChronoUnit.HOURS);
		long issuedInWindow = emailOtpRepository.countByUserEmailAndCreatedAtAfter(user.getEmail(), windowStart);
		if (issuedInWindow >= maxResendsPerHour) {
			throw new ResendCooldownException(60,
					"Too many verification codes were requested for this account. Please try again later.");
		}
	}

	@Override
	@Transactional
	public void deleteOtpsForUser(Long userId) {
		emailOtpRepository.deleteAllForUser(userId);
	}

	/** Generates a cryptographically secure six-digit numeric OTP. */
	String generateOtp() {
		return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
	}

	/** SHA-256 hex digest of the OTP; only the digest is ever stored. */
	String hashOtp(String otp) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(otp.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available on this JVM", ex);
		}
	}

	private boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}
}
