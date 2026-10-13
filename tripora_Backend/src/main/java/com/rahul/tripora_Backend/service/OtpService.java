package com.rahul.tripora_Backend.service;

import com.rahul.tripora_Backend.entity.User;

/**
 * Owns OTP generation, hashing, expiry, attempt limits, cooldowns and the
 * transactional verification + account activation step.
 */
public interface OtpService {

	/**
	 * Invalidates every previous OTP of the user, stores a new hashed OTP and
	 * returns the plaintext value. The plaintext must only ever be handed to
	 * {@link EmailService} — never to an API response.
	 */
	String issueOtp(User user);

	/**
	 * Verifies the OTP for the given (normalized) email and, on success,
	 * marks the OTP used and activates the account in one transaction.
	 */
	void verifyAndActivate(String email, String rawOtp);

	/**
	 * Throws {@code ResendCooldownException} while the current OTP is still
	 * inside its resend cooldown window.
	 */
	void enforceResendCooldown(User user);

	/**
	 * Throws {@code ResendCooldownException} when too many OTPs were requested
	 * for the user inside the rate-limit window.
	 */
	void enforceResendRateLimit(User user);

	/**
	 * Deletes every OTP row belonging to the user (registration rollback).
	 */
	void deleteOtpsForUser(Long userId);
}
