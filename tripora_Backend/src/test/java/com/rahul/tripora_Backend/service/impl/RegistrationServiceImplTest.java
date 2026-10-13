package com.rahul.tripora_Backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.rahul.tripora_Backend.dto.ApiResponse;
import com.rahul.tripora_Backend.dto.RegistrationRequest;
import com.rahul.tripora_Backend.dto.ResendOtpRequest;
import com.rahul.tripora_Backend.dto.VerifyOtpRequest;
import com.rahul.tripora_Backend.entity.User;
import com.rahul.tripora_Backend.enums.UserStatus;
import com.rahul.tripora_Backend.exception.BadRequestException;
import com.rahul.tripora_Backend.exception.DuplicateEmailException;
import com.rahul.tripora_Backend.exception.MailSendFailedException;
import com.rahul.tripora_Backend.exception.ResendCooldownException;
import com.rahul.tripora_Backend.repository.UserRepository;
import com.rahul.tripora_Backend.service.EmailService;
import com.rahul.tripora_Backend.service.OtpService;

/**
 * Unit tests for registration, duplicate handling, password hashing and the
 * mail-failure compensation path. The email service is always mocked.
 */
class RegistrationServiceImplTest {

	private static final String RAW_PASSWORD = "StrongPassword123!";

	private UserRepository userRepository;
	private PasswordEncoder passwordEncoder;
	private OtpService otpService;
	private EmailService emailService;
	private RegistrationServiceImpl registrationService;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = new BCryptPasswordEncoder(4); // low cost factor keeps tests fast
		otpService = mock(OtpService.class);
		emailService = mock(EmailService.class);
		PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
		registrationService = new RegistrationServiceImpl(userRepository, passwordEncoder, otpService,
				emailService, txManager);
	}

	private RegistrationRequest request(String email, String password, String confirm) {
		return new RegistrationRequest(email, password, confirm);
	}

	private User saveWithId(User user) {
		user.setId(42L);
		return user;
	}

	// ------------------------------------------------------------------
	// Valid registration
	// ------------------------------------------------------------------

	@Test
	void register_validRequest_createsPendingUserHashesPasswordAndSendsOtp() {
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(inv -> saveWithId(inv.getArgument(0)));
		when(otpService.issueOtp(any(User.class))).thenReturn("123456");

		ApiResponse response = registrationService.register(
				request("  User@Example.COM ", RAW_PASSWORD, RAW_PASSWORD));

		assertTrue(response.success());

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();
		assertEquals("user@example.com", saved.getEmail(), "Email must be normalized");
		assertNotEquals(RAW_PASSWORD, saved.getPasswordHash(), "Raw password must never be stored");
		assertTrue(saved.getPasswordHash().startsWith("$2"), "Password must be BCrypt hashed");
		assertFalse(saved.isEmailVerified());
		assertEquals(UserStatus.PENDING_VERIFICATION, saved.getStatus());

		// The OTP is only ever handed to the email service.
		verify(otpService).issueOtp(any(User.class));
		verify(emailService).sendOtpEmail("user@example.com", "123456");
	}

	@Test
	void register_pendingLeftoverFromEarlierAttempt_isReusedNotDuplicated() {
		User pending = User.builder()
				.id(7L)
				.email("user@example.com")
				.passwordHash("old-hash")
				.emailVerified(false)
				.status(UserStatus.PENDING_VERIFICATION)
				.build();
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
		when(otpService.issueOtp(any(User.class))).thenReturn("123456");

		ApiResponse response = registrationService.register(
				request("user@example.com", RAW_PASSWORD, RAW_PASSWORD));

		assertTrue(response.success());
		// The same pending row is refreshed (password hash updated) — no
		// second user row is created for the email.
		verify(userRepository).save(pending);
		verify(emailService).sendOtpEmail("user@example.com", "123456");
		assertNotEquals("old-hash", pending.getPasswordHash(), "Password should be refreshed");
	}

	// ------------------------------------------------------------------
	// Validation failures
	// ------------------------------------------------------------------

	@Test
	void register_invalidEmail_isRejectedWithoutTouchingPersistence() {
		BadRequestException ex = assertThrows(BadRequestException.class,
				() -> registrationService.register(request("not-an-email", RAW_PASSWORD, RAW_PASSWORD)));

		assertTrue(ex.getMessage().toLowerCase().contains("email"));
		verifyNoInteractions(userRepository, otpService, emailService);
	}

	@Test
	void register_passwordMismatch_isRejected() {
		BadRequestException ex = assertThrows(BadRequestException.class,
				() -> registrationService.register(request("user@example.com", RAW_PASSWORD, "DifferentPass123")));

		assertTrue(ex.getMessage().toLowerCase().contains("match"));
		verifyNoInteractions(emailService);
		verify(userRepository, never()).save(any());
	}

	@Test
	void register_shortPassword_isRejected() {
		assertThrows(BadRequestException.class,
				() -> registrationService.register(request("user@example.com", "Ab1!", "Ab1!")));
	}

	@Test
	void register_passwordWithoutDigit_isRejected() {
		BadRequestException ex = assertThrows(BadRequestException.class,
				() -> registrationService.register(request("user@example.com", "OnlyLetters", "OnlyLetters")));
		assertTrue(ex.getMessage().toLowerCase().contains("number"));
	}

	@Test
	void register_duplicateActiveEmail_isRejected() {
		User active = User.builder()
				.id(9L)
				.email("user@example.com")
				.passwordHash("$2a$10$hash")
				.emailVerified(true)
				.status(UserStatus.ACTIVE)
				.build();
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(active));

		assertThrows(DuplicateEmailException.class,
				() -> registrationService.register(request("user@example.com", RAW_PASSWORD, RAW_PASSWORD)));

		verify(otpService, never()).issueOtp(any());
		verifyNoInteractions(emailService);
		verify(userRepository, never()).save(any());
	}

	// ------------------------------------------------------------------
	// Email delivery failure compensation
	// ------------------------------------------------------------------

	@Test
	void register_emailDeliveryFailure_removesPendingUserSoRetryIsPossible() {
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(inv -> saveWithId(inv.getArgument(0)));
		when(otpService.issueOtp(any(User.class))).thenReturn("123456");
		doThrow(new MailSendFailedException("mail down"))
				.when(emailService).sendOtpEmail(anyString(), anyString());

		assertThrows(MailSendFailedException.class,
				() -> registrationService.register(request("user@example.com", RAW_PASSWORD, RAW_PASSWORD)));

		// Compensation: the undelivered pending registration is removed again.
		verify(otpService).deleteOtpsForUser(42L);
		verify(userRepository).delete(any(User.class));
	}

	// ------------------------------------------------------------------
	// Verify OTP
	// ------------------------------------------------------------------

	@Test
	void verifyOtp_delegatesToOtpServiceWithNormalizedEmail() {
		ApiResponse response = registrationService.verifyOtp(
				new VerifyOtpRequest(" User@Example.COM ", " 123456 "));

		assertTrue(response.success());
		verify(otpService).verifyAndActivate("user@example.com", "123456");
	}

	// ------------------------------------------------------------------
	// Resend OTP
	// ------------------------------------------------------------------

	@Test
	void resendOtp_pendingUser_issuesNewOtpAndSendsEmail() {
		User pending = User.builder()
				.id(7L)
				.email("user@example.com")
				.passwordHash("hash")
				.emailVerified(false)
				.status(UserStatus.PENDING_VERIFICATION)
				.build();
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
		when(otpService.issueOtp(pending)).thenReturn("654321");

		ApiResponse response = registrationService.resendOtp(new ResendOtpRequest("user@example.com"));

		assertTrue(response.success());
		verify(otpService).enforceResendCooldown(pending);
		verify(otpService).enforceResendRateLimit(pending);
		verify(otpService).issueOtp(pending);
		verify(emailService).sendOtpEmail("user@example.com", "654321");
	}

	@Test
	void resendOtp_insideCooldown_period_throwsAndDoesNotSendEmail() {
		User pending = User.builder()
				.id(7L)
				.email("user@example.com")
				.passwordHash("hash")
				.emailVerified(false)
				.status(UserStatus.PENDING_VERIFICATION)
				.build();
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
		doThrow(new ResendCooldownException(45, "Please wait 45 seconds before requesting a new code."))
				.when(otpService).enforceResendCooldown(pending);

		assertThrows(ResendCooldownException.class,
				() -> registrationService.resendOtp(new ResendOtpRequest("user@example.com")));

		verify(otpService, never()).issueOtp(any());
		verifyNoInteractions(emailService);
	}

	@Test
	void resendOtp_unknownEmail_returnsGenericSuccessWithoutSendingEmail() {
		when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		ApiResponse response = registrationService.resendOtp(new ResendOtpRequest("ghost@example.com"));

		assertTrue(response.success(), "Generic response avoids revealing which emails exist");
		verifyNoInteractions(emailService);
		verify(otpService, never()).issueOtp(any());
	}

	@Test
	void resendOtp_alreadyVerifiedUser_returnsGenericSuccessWithoutSendingEmail() {
		User active = User.builder()
				.id(9L)
				.email("user@example.com")
				.passwordHash("hash")
				.emailVerified(true)
				.status(UserStatus.ACTIVE)
				.build();
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(active));

		ApiResponse response = registrationService.resendOtp(new ResendOtpRequest("user@example.com"));

		assertTrue(response.success());
		verifyNoInteractions(emailService);
	}
}
