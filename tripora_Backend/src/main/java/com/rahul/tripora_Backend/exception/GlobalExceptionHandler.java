package com.rahul.tripora_Backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Maps every exception to a consistent, leak-free {@link ApiError} body.
 * Stack traces and secrets are never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		String message = fieldErrors.isEmpty() ? "Please correct the highlighted fields." : fieldErrors.values().iterator().next();
		return build(HttpStatus.BAD_REQUEST, message, request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "The request body is missing or malformed.", request);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(OtpException.class)
	public ResponseEntity<ApiError> handleOtpException(OtpException ex, HttpServletRequest request) {
		HttpStatus status = switch (ex.getCode()) {
			case OTP_EXPIRED -> HttpStatus.GONE;
			case TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
			case ALREADY_VERIFIED -> HttpStatus.CONFLICT;
			case INVALID_OTP -> HttpStatus.BAD_REQUEST;
		};
		return build(status, ex.getMessage(), request);
	}

	@ExceptionHandler(ResendCooldownException.class)
	public ResponseEntity<ApiError> handleCooldown(ResendCooldownException ex, HttpServletRequest request) {
		return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
	}

	@ExceptionHandler(MailNotConfiguredException.class)
	public ResponseEntity<ApiError> handleMailNotConfigured(MailNotConfiguredException ex, HttpServletRequest request) {
		return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
	}

	@ExceptionHandler(MailSendFailedException.class)
	public ResponseEntity<ApiError> handleMailSendFailed(MailSendFailedException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		// Log server-side only; never expose internals to the client.
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again.", request);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
		return build(status, message, request, Map.of());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
			Map<String, String> fieldErrors) {
		ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, request.getRequestURI(), fieldErrors);
		return ResponseEntity.status(status).body(body);
	}
}
