package com.rahul.tripora_Backend.exception;

/**
 * OTP verification failure. The message is intentionally generic so that the
 * API never reveals whether a submitted code was close to correct.
 */
public class OtpException extends RuntimeException {

	public enum ErrorCode {
		/** Unknown email, no active OTP, wrong code or already-used code. */
		INVALID_OTP,
		/** The OTP exists but its five-minute window has passed. */
		OTP_EXPIRED,
		/** The maximum number of incorrect attempts was reached. */
		TOO_MANY_ATTEMPTS,
		/** The account was already verified earlier. */
		ALREADY_VERIFIED
	}

	private final ErrorCode code;

	public OtpException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
