package com.rahul.tripora_Backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/resend-otp.
 */
public record ResendOtpRequest(

		@NotBlank(message = "Email is required.")
		@Email(message = "Please enter a valid email address.")
		String email) {
}
