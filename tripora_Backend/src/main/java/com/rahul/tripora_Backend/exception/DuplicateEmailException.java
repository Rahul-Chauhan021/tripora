package com.rahul.tripora_Backend.exception;

/**
 * The email already belongs to a verified/active account (HTTP 409).
 */
public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String message) {
		super(message);
	}
}
