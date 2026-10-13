package com.rahul.tripora_Backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single-use, hashed email verification OTP. The plaintext OTP is never
 * persisted; only its SHA-256 hash is stored.
 */
@Entity
@Table(name = "email_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOtp {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "otp_hash", nullable = false, length = 128)
	private String otpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "used", nullable = false)
	private boolean used;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Cooldown marker: a new OTP may not be issued before this instant. */
	@Column(name = "resend_available_at", nullable = false)
	private Instant resendAvailableAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
