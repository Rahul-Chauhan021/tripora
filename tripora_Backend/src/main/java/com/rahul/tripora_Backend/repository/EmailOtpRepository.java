package com.rahul.tripora_Backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rahul.tripora_Backend.entity.EmailOtp;

/**
 * JPA repository for the {@code email_otps} table. The modifying queries are
 * written as atomic conditional updates so that concurrent verification
 * attempts cannot exceed the attempt limit and an OTP can only be consumed
 * once.
 */
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

	/** Most recently created OTP for the given (normalized) email. */
	Optional<EmailOtp> findFirstByUserEmailOrderByCreatedAtDesc(String email);

	List<EmailOtp> findByUserEmail(String email);

	/** Invalidates every active OTP of a user when a new one is issued. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update EmailOtp o set o.used = true where o.user.id = :userId and o.used = false")
	int deactivateOtpsForUser(@Param("userId") Long userId);

	/**
	 * Atomically consumes one failed-attempt slot. Returns 0 when the OTP is
	 * already used or the attempt limit has been reached.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update EmailOtp o set o.attempts = o.attempts + 1 where o.id = :otpId and o.used = false and o.attempts < :maxAttempts")
	int incrementAttemptsIfActive(@Param("otpId") Long otpId, @Param("maxAttempts") int maxAttempts);

	/**
	 * Atomically marks an OTP as used. Returns 0 if another request already
	 * consumed it, which prevents OTP reuse under concurrency.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update EmailOtp o set o.used = true where o.id = :otpId and o.used = false")
	int markUsedIfActive(@Param("otpId") Long otpId);

	/** Number of OTPs issued for a user inside the given time window. */
	long countByUserEmailAndCreatedAtAfter(String email, Instant createdAfter);

	/** Removes all OTP rows of a user (used when a registration is rolled back). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from EmailOtp o where o.user.id = :userId")
	int deleteAllForUser(@Param("userId") Long userId);
}
