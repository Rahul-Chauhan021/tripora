package com.rahul.tripora_Backend.service.impl;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.rahul.tripora_Backend.dto.ApiResponse;
import com.rahul.tripora_Backend.dto.RegistrationRequest;
import com.rahul.tripora_Backend.dto.ResendOtpRequest;
import com.rahul.tripora_Backend.dto.VerifyOtpRequest;
import com.rahul.tripora_Backend.entity.User;
import com.rahul.tripora_Backend.enums.UserStatus;
import com.rahul.tripora_Backend.exception.BadRequestException;
import com.rahul.tripora_Backend.exception.DuplicateEmailException;
import com.rahul.tripora_Backend.repository.UserRepository;
import com.rahul.tripora_Backend.service.EmailService;
import com.rahul.tripora_Backend.service.OtpService;
import com.rahul.tripora_Backend.service.RegistrationService;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registration orchestration.
 *
 * The email is sent <em>outside</em> the database transaction. If delivery
 * fails for a brand-new registration, the pending user and its OTP are
 * removed again in a compensation transaction, so no account is ever left
 * stuck in an unusable pending state.
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final OtpService otpService;
	private final EmailService emailService;
	private final TransactionTemplate transactionTemplate;

	public RegistrationServiceImpl(UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			OtpService otpService,
			EmailService emailService,
			PlatformTransactionManager transactionManager) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.otpService = otpService;
		this.emailService = emailService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public ApiResponse register(RegistrationRequest request) {
		String email = normalizeEmail(request.email());
		validateEmail(email);
		validatePasswordStrength(request.password());
		if (!request.password().equals(request.confirmPassword())) {
			throw new BadRequestException("Password and confirm password do not match.");
		}

		String passwordHash = passwordEncoder.encode(request.password());

		// Transaction 1: persist the pending user and the OTP, then commit.
		IssueResult issue = transactionTemplate.execute(status -> persistRegistration(email, passwordHash));

		// Transaction 2 (no DB transaction held): deliver the email.
		try {
			emailService.sendOtpEmail(email, issue.otp());
		} catch (RuntimeException ex) {
			compensate(issue);
			throw ex;
		}

		return ApiResponse.success(
				"Registration successful! A 6-digit verification code was sent to " + email + ". It expires in 5 minutes.");
	}

	@Override
	public ApiResponse verifyOtp(VerifyOtpRequest request) {
		String email = normalizeEmail(request.email());
		validateEmail(email);
		otpService.verifyAndActivate(email, request.otp().trim());
		return ApiResponse.success("Email verified successfully.");
	}

	@Override
	public ApiResponse resendOtp(ResendOtpRequest request) {
		String email = normalizeEmail(request.email());
		validateEmail(email);

		String genericMessage = "If a pending registration exists for this email, a new verification code has been sent.";

		Optional<User> found = userRepository.findByEmail(email);
		if (found.isEmpty() || found.get().isEmailVerified()
				|| found.get().getStatus() == UserStatus.ACTIVE) {
			// Same response for unknown or already-verified emails avoids
			// revealing which addresses have accounts.
			return ApiResponse.success(genericMessage);
		}

		User user = found.get();
		String otp = transactionTemplate.execute(status -> {
			otpService.enforceResendCooldown(user);
			otpService.enforceResendRateLimit(user);
			return otpService.issueOtp(user);
		});

		emailService.sendOtpEmail(email, otp);

		return ApiResponse.success("A new verification code was sent to " + email + ". It expires in 5 minutes.");
	}

	/**
	 * Transactional core of the registration: creates or refreshes a pending
	 * user and issues its first OTP. Runs inside the caller's transaction.
	 */
	private IssueResult persistRegistration(String email, String passwordHash) {
		Optional<User> existing = userRepository.findByEmail(email);
		if (existing.isPresent()) {
			User user = existing.get();
			if (user.isEmailVerified() || user.getStatus() == UserStatus.ACTIVE) {
				throw new DuplicateEmailException(
						"An account with this email address already exists. Please sign in instead.");
			}
			// Unverified leftover from an earlier attempt: refresh the
			// password and issue a fresh OTP instead of creating a duplicate.
			user.setPasswordHash(passwordHash);
			user.setUpdatedAt(Instant.now());
			userRepository.save(user);
			return new IssueResult(user, otpService.issueOtp(user), true);
		}

		User user = User.builder()
				.email(email)
				.passwordHash(passwordHash)
				.emailVerified(false)
				.status(UserStatus.PENDING_VERIFICATION)
				.build();
		user = userRepository.save(user);
		return new IssueResult(user, otpService.issueOtp(user), false);
	}

	/**
	 * Rolls back the persisted registration when the OTP email could not be
	 * delivered, so the same email can be registered again immediately.
	 */
	private void compensate(IssueResult issue) {
		transactionTemplate.executeWithoutResult(status -> {
			if (issue.reusedExistingUser()) {
				// Keep the pending account; only drop the undelivered OTP so
				// that the user can simply retry register or resend.
				otpService.deleteOtpsForUser(issue.user().getId());
			} else {
				otpService.deleteOtpsForUser(issue.user().getId());
				userRepository.delete(issue.user());
			}
		});
	}

	private String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private void validateEmail(String email) {
		if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches() || email.length() > 320) {
			throw new BadRequestException("Please enter a valid email address.");
		}
	}

	private void validatePasswordStrength(String password) {
		if (password == null || password.length() < 8) {
			throw new BadRequestException("Password must be at least 8 characters long.");
		}
		boolean hasLetter = password.chars().anyMatch(Character::isLetter);
		boolean hasDigit = password.chars().anyMatch(Character::isDigit);
		if (!hasLetter || !hasDigit) {
			throw new BadRequestException("Password must contain at least one letter and one number.");
		}
	}

	/** Outcome of the registration transaction. */
	private record IssueResult(User user, String otp, boolean reusedExistingUser) {
	}
}
