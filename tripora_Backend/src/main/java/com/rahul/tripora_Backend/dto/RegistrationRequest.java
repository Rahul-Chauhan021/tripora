package com.rahul.tripora_Backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/auth/register.
 */
public record RegistrationRequest(

		@NotBlank(message = "Email is required.")
		@Email(message = "Please enter a valid email address.")
		@Size(max = 320, message = "Email must not exceed 320 characters.")
		String email,

		@NotBlank(message = "Password is required.")
		@Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
		String password,

		@NotBlank(message = "Confirm password is required.")
		@Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
		String confirmPassword) {
}
