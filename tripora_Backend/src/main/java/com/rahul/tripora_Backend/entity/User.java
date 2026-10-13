package com.rahul.tripora_Backend.entity;

import java.time.Instant;

import com.rahul.tripora_Backend.enums.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Tripora account. The email is stored normalized (trimmed, lower-cased)
 * and is unique. Passwords are only ever stored as BCrypt hashes.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private UserStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Set when the email OTP was successfully verified. */
	@Column(name = "verified_at")
	private Instant verifiedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
