package com.rahul.tripora_Backend.enums;

/**
 * Lifecycle status of a Tripora user account.
 */
public enum UserStatus {

	/** Registration submitted, email OTP not verified yet. */
	PENDING_VERIFICATION,

	/** Email OTP verified; account is fully usable. */
	ACTIVE
}
