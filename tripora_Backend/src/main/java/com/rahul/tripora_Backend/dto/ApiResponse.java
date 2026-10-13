package com.rahul.tripora_Backend.dto;

import java.time.Instant;

/**
 * Safe, generic API envelope. Never carries OTPs, passwords or hashes.
 */
public record ApiResponse(boolean success, String message, Instant timestamp) {

	public static ApiResponse success(String message) {
		return new ApiResponse(true, message, Instant.now());
	}

	public static ApiResponse failure(String message) {
		return new ApiResponse(false, message, Instant.now());
	}
}
