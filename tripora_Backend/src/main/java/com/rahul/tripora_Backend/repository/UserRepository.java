package com.rahul.tripora_Backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rahul.tripora_Backend.entity.User;

/**
 * JPA repository for the {@code users} table. Emails are always queried in
 * their normalized form (trimmed, lower-cased).
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
