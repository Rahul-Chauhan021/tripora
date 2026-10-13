package com.rahul.tripora_Backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahul.tripora_Backend.dto.ApiResponse;
import com.rahul.tripora_Backend.dto.RegistrationRequest;
import com.rahul.tripora_Backend.dto.ResendOtpRequest;
import com.rahul.tripora_Backend.dto.VerifyOtpRequest;
import com.rahul.tripora_Backend.service.RegistrationService;

import jakarta.validation.Valid;

/**
 * Public registration endpoints. Contains no business logic — it only
 * validates input and delegates to the service layer.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

	private final RegistrationService registrationService;

	public RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
	}

	@PostMapping("/verify-otp")
	public ResponseEntity<ApiResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
		return ResponseEntity.ok(registrationService.verifyOtp(request));
	}

	@PostMapping("/resend-otp")
	public ResponseEntity<ApiResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
		return ResponseEntity.ok(registrationService.resendOtp(request));
	}
}
