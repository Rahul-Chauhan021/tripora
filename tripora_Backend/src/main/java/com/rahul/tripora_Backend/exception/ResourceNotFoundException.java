package com.rahul.tripora_Backend.exception;

/**
 * Thrown when a referenced entity does not exist (HTTP 404).
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}
}
