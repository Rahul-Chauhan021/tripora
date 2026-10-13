package com.rahul.tripora_Backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.rahul.tripora_Backend.dto.ApiResponse;
import com.rahul.tripora_Backend.dto.RegistrationRequest;
import com.rahul.tripora_Backend.dto.VerifyOtpRequest;
import com.rahul.tripora_Backend.exception.BadRequestException;
import com.rahul.tripora_Backend.exception.DuplicateEmailException;
import com.rahul.tripora_Backend.exception.GlobalExceptionHandler;
import com.rahul.tripora_Backend.service.RegistrationService;

/**
 * HTTP-layer tests: bean validation runs here, and the global exception
 * handler maps business exceptions to consistent status codes and bodies.
 */
class RegistrationControllerTest {

	private RegistrationService registrationService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		registrationService = mock(RegistrationService.class);
		RegistrationController controller = new RegistrationController(registrationService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void register_validBody_returns201WithSafeResponse() throws Exception {
		when(registrationService.register(any())).thenReturn(ApiResponse.success("Verification code sent."));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com","password":"StrongPassword123!","confirmPassword":"StrongPassword123!"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void register_invalidEmail_returns400WithFieldErrorAndNeverCallsService() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"not-an-email","password":"StrongPassword123!","confirmPassword":"StrongPassword123!"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.email").exists());

		verify(registrationService, never()).register(any());
	}

	@Test
	void register_missingFields_returns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists())
				.andExpect(jsonPath("$.fieldErrors.confirmPassword").exists());
	}

	@Test
	void register_duplicateEmail_returns409() throws Exception {
		when(registrationService.register(any()))
				.thenThrow(new DuplicateEmailException("An account with this email address already exists."));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com","password":"StrongPassword123!","confirmPassword":"StrongPassword123!"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("An account with this email address already exists."));
	}

	@Test
	void register_passwordMismatchFromService_returns400() throws Exception {
		when(registrationService.register(any()))
				.thenThrow(new BadRequestException("Password and confirm password do not match."));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com","password":"StrongPassword123!","confirmPassword":"OtherPassword123!"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Password and confirm password do not match."));
	}

	@Test
	void verifyOtp_wrongShape_returns400WithoutCallingService() throws Exception {
		mockMvc.perform(post("/api/auth/verify-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com","otp":"123"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.otp").exists());

		verify(registrationService, never()).verifyOtp(any());
	}

	@Test
	void verifyOtp_validBody_delegatesAndReturns200() throws Exception {
		when(registrationService.verifyOtp(any())).thenReturn(ApiResponse.success("Email verified successfully."));

		mockMvc.perform(post("/api/auth/verify-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com","otp":"123456"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(registrationService).verifyOtp(new VerifyOtpRequest("user@example.com", "123456"));
	}

	@Test
	void resendOtp_validBody_delegatesAndReturns200() throws Exception {
		when(registrationService.resendOtp(any())).thenReturn(ApiResponse.success("A new code was sent."));

		mockMvc.perform(post("/api/auth/resend-otp")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void register_response_neverEchoesPasswordOrConfirmPassword() throws Exception {
		when(registrationService.register(any())).thenReturn(ApiResponse.success("ok"));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@example.com","password":"StrongPassword123!","confirmPassword":"StrongPassword123!"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.confirmPassword").doesNotExist())
				.andExpect(jsonPath("$.otp").doesNotExist());

		verify(registrationService).register(new RegistrationRequest("user@example.com", "StrongPassword123!",
				"StrongPassword123!"));
	}
}
