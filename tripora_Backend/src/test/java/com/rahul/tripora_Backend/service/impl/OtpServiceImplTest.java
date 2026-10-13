package com.rahul.tripora_Backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.rahul.tripora_Backend.entity.EmailOtp;
import com.rahul.tripora_Backend.entity.User;
import com.rahul.tripora_Backend.enums.UserStatus;
import com.rahul.tripora_Backend.exception.OtpException;
import com.rahul.tripora_Backend.exception.OtpException.ErrorCode;
import com.rahul.tripora_Backend.exception.ResendCooldownException;
import com.rahul.tripora_Backend.repository.EmailOtpRepository;
import com.rahul.tripora_Backend.repository.UserRepository;

/**
 * Unit tests for the OTP lifecycle: generation, expiry, attempt limiting,
 * single use, cooldown, rate limiting and account activation.
 */
class OtpServiceImplTest {

	private EmailOtpRepository emailOtpRepository;
	private UserRepository userRepository;
	private OtpServiceImpl otpService;

	private static final int MAX_ATTEMPTS = 5;

	@BeforeEach
	void setUp() {
		emailOtpRepository = mock(EmailOtpRepository.class);
		userRepository = mock(UserRepository.class);
		otpService = new OtpServiceImpl(emailOtpRepository, userRepository, 5, MAX_ATTEMPTS, 60, 5);
	}

	private User pendingUser() {
		return User.builder()
				.id(1L)
				.email("user@example.com")
				.passwordHash("$2a$10$hash")
				.emailVerified(false)
				.status(UserStatus.PENDING_VERIFICATION)
				.build();
	}

	private EmailOtp activeOtpFor(User user, String rawOtp, Instant expiresAt) {
		return EmailOtp.builder()
				.id(10L)
				.user(user)
				.otpHash(otpService.hashOtp(rawOtp))
				.expiresAt(expiresAt)
				.attempts(0)
				.used(false)
				.createdAt(Instant.now())
				.resendAvailableAt(Instant.now().plus(Duration.ofSeconds(60)))
				.build();
	}

	// ------------------------------------------------------------------
	// Generation
	// ------------------------------------------------------------------

	@Test
	void issueOtp_generatesSixDigitOtpStoresOnlyHashAndInvalidatesPrevious() {
		User user = pendingUser();
		when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

		String otp = otpService.issueOtp(user);

		assertTrue(otp.matches("\\d{6}"), "OTP must be exactly six digits, got: <redacted>");
		verify(emailOtpRepository).deactivateOtpsForUser(1L);

		ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
		verify(emailOtpRepository).save(captor.capture());
		EmailOtp saved = captor.getValue();
		assertEquals(otpService.hashOtp(otp), saved.getOtpHash());
		assertNotEquals(otp, saved.getOtpHash(), "Plaintext OTP must never be persisted");
		assertFalse(saved.isUsed());
		assertEquals(0, saved.getAttempts());

		long expiryMinutes = Duration.between(Instant.now(), saved.getExpiresAt()).toMinutes();
		assertTrue(expiryMinutes >= 4 && expiryMinutes <= 6, "OTP expiry should be about five minutes");
		long cooldownSeconds = Duration.between(Instant.now(), saved.getResendAvailableAt()).toSeconds();
		assertTrue(cooldownSeconds >= 55 && cooldownSeconds <= 65, "Resend cooldown should be about sixty seconds");
	}

	@Test
	void generateOtp_alwaysProducesSixDigits() {
		for (int i = 0; i < 200; i++) {
			assertTrue(otpService.generateOtp().matches("\\d{6}"));
		}
	}

	// ------------------------------------------------------------------
	// Successful verification
	// ------------------------------------------------------------------

	@Test
	void verifyAndActivate_success_activatesAccountAndConsumesOtp() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));
		when(emailOtpRepository.incrementAttemptsIfActive(10L, MAX_ATTEMPTS)).thenReturn(1);
		when(emailOtpRepository.markUsedIfActive(10L)).thenReturn(1);

		otpService.verifyAndActivate("user@example.com", "123456");

		assertTrue(user.isEmailVerified());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		org.junit.jupiter.api.Assertions.assertNotNull(user.getVerifiedAt());
		verify(userRepository).save(user);
	}

	// ------------------------------------------------------------------
	// Failures
	// ------------------------------------------------------------------

	@Test
	void verifyAndActivate_incorrectOtp_rejectsWithGenericErrorAndKeepsAccountPending() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));
		when(emailOtpRepository.incrementAttemptsIfActive(10L, MAX_ATTEMPTS)).thenReturn(1);

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("user@example.com", "654321"));

		assertEquals(ErrorCode.INVALID_OTP, ex.getCode());
		assertFalse(user.isEmailVerified());
		assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
		verify(userRepository, never()).save(any());
		verify(emailOtpRepository, never()).markUsedIfActive(anyLong());
	}

	@Test
	void verifyAndActivate_expiredOtp_rejectsAndDoesNotConsumeAttempt() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().minus(Duration.ofSeconds(1)));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("user@example.com", "123456"));

		assertEquals(ErrorCode.OTP_EXPIRED, ex.getCode());
		verify(emailOtpRepository, never()).incrementAttemptsIfActive(anyLong(), anyInt());
		verify(userRepository, never()).save(any());
	}

	@Test
	void verifyAndActivate_tooManyAttempts_blocksFurtherVerification() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));
		// The conditional UPDATE matched no row: attempts are exhausted.
		when(emailOtpRepository.incrementAttemptsIfActive(10L, MAX_ATTEMPTS)).thenReturn(0);

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("user@example.com", "123456"));

		assertEquals(ErrorCode.TOO_MANY_ATTEMPTS, ex.getCode());
		verify(emailOtpRepository, never()).markUsedIfActive(anyLong());
	}

	@Test
	void verifyAndActivate_reusedOtp_isRejected() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		otp.setUsed(true); // already redeemed once
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("user@example.com", "123456"));

		assertEquals(ErrorCode.INVALID_OTP, ex.getCode());
		verify(emailOtpRepository, never()).incrementAttemptsIfActive(anyLong(), anyInt());
	}

	@Test
	void verifyAndActivate_lostConcurrencyRace_isRejected() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));
		when(emailOtpRepository.incrementAttemptsIfActive(10L, MAX_ATTEMPTS)).thenReturn(1);
		// Another request marked the OTP as used first.
		when(emailOtpRepository.markUsedIfActive(10L)).thenReturn(0);

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("user@example.com", "123456"));

		assertEquals(ErrorCode.INVALID_OTP, ex.getCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	void verifyAndActivate_unknownEmail_getsSameGenericError() {
		when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("ghost@example.com", "123456"));

		assertEquals(ErrorCode.INVALID_OTP, ex.getCode());
	}

	@Test
	void verifyAndActivate_alreadyVerified_reportsConflict() {
		User user = pendingUser();
		user.setEmailVerified(true);
		user.setStatus(UserStatus.ACTIVE);
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		OtpException ex = assertThrows(OtpException.class,
				() -> otpService.verifyAndActivate("user@example.com", "123456"));

		assertEquals(ErrorCode.ALREADY_VERIFIED, ex.getCode());
	}

	// ------------------------------------------------------------------
	// Resend cooldown and rate limiting
	// ------------------------------------------------------------------

	@Test
	void enforceResendCooldown_insideWindow_throwsWithRetryAfter() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		otp.setResendAvailableAt(Instant.now().plus(Duration.ofSeconds(30)));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));

		ResendCooldownException ex = assertThrows(ResendCooldownException.class,
				() -> otpService.enforceResendCooldown(user));

		assertTrue(ex.getRetryAfterSeconds() > 0);
		assertTrue(ex.getRetryAfterSeconds() <= 30);
	}

	@Test
	void enforceResendCooldown_windowElapsed_allowsResend() {
		User user = pendingUser();
		EmailOtp otp = activeOtpFor(user, "123456", Instant.now().plus(Duration.ofMinutes(4)));
		otp.setResendAvailableAt(Instant.now().minus(Duration.ofSeconds(1)));
		when(emailOtpRepository.findFirstByUserEmailOrderByCreatedAtDesc("user@example.com"))
				.thenReturn(Optional.of(otp));

		otpService.enforceResendCooldown(user); // must not throw
	}

	@Test
	void enforceResendRateLimit_tooManyRecentOtpsWithinHour_throws() {
		User user = pendingUser();
		when(emailOtpRepository.countByUserEmailAndCreatedAtAfter(eq("user@example.com"), any(Instant.class)))
				.thenReturn(5L); // max-resends-per-hour is 5

		ResendCooldownException ex = assertThrows(ResendCooldownException.class,
				() -> otpService.enforceResendRateLimit(user));

		assertTrue(ex.getRetryAfterSeconds() > 0);
	}

	@Test
	void enforceResendRateLimit_fewRecentOtpsWithinHour_allows() {
		User user = pendingUser();
		when(emailOtpRepository.countByUserEmailAndCreatedAtAfter(eq("user@example.com"), any(Instant.class)))
				.thenReturn(2L);

		otpService.enforceResendRateLimit(user); // must not throw
	}

	@Test
	void issueOtp_invalidatesPreviousActiveOtp() {
		User user = pendingUser();
		when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

		otpService.issueOtp(user);
		otpService.issueOtp(user); // second issuance for the same user

		// Both issuances must have deactivated older codes first.
		verify(emailOtpRepository, org.mockito.Mockito.times(2)).deactivateOtpsForUser(1L);
	}
}
