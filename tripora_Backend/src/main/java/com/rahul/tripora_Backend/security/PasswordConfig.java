package com.rahul.tripora_Backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing configuration. Only spring-security-crypto is used, so no
 * security filter chain or login mechanism is introduced by this module.
 */
@Configuration
public class PasswordConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(10);
	}
}
