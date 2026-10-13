package com.rahul.tripora_Backend.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent error body returned by the global exception handler.
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
		Map<String, String> fieldErrors) {

	public ApiError(int status, String error, String message, String path, Map<String, String> fieldErrors) {
		this(Instant.now(), status, error, message, path, fieldErrors);
	}

	public ApiError(int status, String error, String message, String path) {
		this(status, error, message, path, Map.of());
	}
}
