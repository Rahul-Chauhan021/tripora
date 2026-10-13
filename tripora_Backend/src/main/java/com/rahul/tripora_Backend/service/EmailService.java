package com.rahul.tripora_Backend.service;

/**
 * Delivers the six-digit OTP to the user's inbox as a branded HTML email.
 * Implementations must never log the OTP or include it in exceptions.
 */
public interface EmailService {

	/**
	 * @return true when a usable SMTP host has been configured.
	 */
	boolean isConfigured();

	/**
	 * Sends the verification email.
	 *
	 * @throws com.rahul.tripora_Backend.exception.MailNotConfiguredException
	 *                 when SMTP is not configured.
	 * @throws com.rahul.tripora_Backend.exception.MailSendFailedException
	 *                 when the SMTP server could not deliver the message.
	 */
	void sendOtpEmail(String email, String otp);
}
