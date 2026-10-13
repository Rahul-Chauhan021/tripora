package com.rahul.tripora_Backend.exception;

/**
 * Generic validation failure for request data that passed bean validation but
 * violates a business rule (HTTP 400).
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
