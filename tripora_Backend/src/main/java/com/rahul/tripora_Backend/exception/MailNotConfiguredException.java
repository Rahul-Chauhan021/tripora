package com.rahul.tripora_Backend.exception;

/**
 * SMTP is not configured on the server (HTTP 503). The API never pretends an
 * email was delivered when no mail server is available.
 */
public class MailNotConfiguredException extends RuntimeException {

	public MailNotConfiguredException(String message) {
		super(message);
	}
}
