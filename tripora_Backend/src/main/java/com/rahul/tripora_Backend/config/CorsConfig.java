package com.rahul.tripora_Backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the React dev server (and any configured production origin) to call
 * the Tripora REST API.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Value("${app.cors.allowed-origins}")
	private String allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toArray(String[]::new);
		registry.addMapping("/api/**")
				.allowedOrigins(origins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.maxAge(3600);
	}
}
