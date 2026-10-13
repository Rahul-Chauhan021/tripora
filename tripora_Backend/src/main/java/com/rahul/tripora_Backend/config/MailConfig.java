package com.rahul.tripora_Backend.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Builds the SMTP sender purely from environment-driven properties. The
 * sender is always created as a bean so the application context can start,
 * but {@code EmailServiceImpl} refuses to send anything when the host is
 * empty and reports a clear configuration error instead.
 */
@Configuration
public class MailConfig {

	@Bean
	public JavaMailSenderImpl mailSender(
			@Value("${app.mail.host}") String host,
			@Value("${app.mail.port}") int port,
			@Value("${app.mail.username}") String username,
			@Value("${app.mail.password}") String password) {

		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		sender.setUsername(username);
		sender.setPassword(password);

		Properties props = sender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.connectiontimeout", "10000");
		props.put("mail.smtp.timeout", "10000");
		props.put("mail.smtp.writetimeout", "10000");
		return sender;
	}
}
