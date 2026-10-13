package com.rahul.tripora_Backend.exception;

/**
 * The SMTP server could not be reached or rejected the message (HTTP 502).
 */
public class MailSendFailedException extends RuntimeException {

	public MailSendFailedException(String message) {
		super(message);
	}

	public MailSendFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
