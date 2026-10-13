package com.rahul.tripora_Backend.exception;

/**
 * The resend cooldown or the resend rate limit has not elapsed yet (HTTP 429).
 */
public class ResendCooldownException extends RuntimeException {

	private final long retryAfterSeconds;

	public ResendCooldownException(long retryAfterSeconds, String message) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
