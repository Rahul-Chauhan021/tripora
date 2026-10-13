package com.rahul.tripora_Backend.service.impl;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.rahul.tripora_Backend.exception.MailNotConfiguredException;
import com.rahul.tripora_Backend.exception.MailSendFailedException;
import com.rahul.tripora_Backend.service.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends the Tripora verification email. The OTP is placed only into the email
 * body and is never written to any log.
 */
@Service
public class EmailServiceImpl implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

	private final JavaMailSenderImpl mailSender;
	private final String host;
	private final String fromAddress;
	private final int expiryMinutes;

	public EmailServiceImpl(JavaMailSenderImpl mailSender,
			@Value("${app.mail.host}") String host,
			@Value("${app.mail.from}") String fromAddress,
			@Value("${app.otp.expiry-minutes}") int expiryMinutes) {
		this.mailSender = mailSender;
		this.host = host;
		this.fromAddress = fromAddress;
		this.expiryMinutes = expiryMinutes;
	}

	@Override
	public boolean isConfigured() {
		return host != null && !host.isBlank();
	}

	@Override
	public void sendOtpEmail(String email, String otp) {
		if (!isConfigured()) {
			throw new MailNotConfiguredException(
					"Email delivery is not configured on this server. Set the MAIL_HOST, MAIL_PORT, "
							+ "MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM environment variables and restart the backend.");
		}
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(fromAddress);
			helper.setTo(email);
			helper.setSubject("Your Tripora verification code");
			helper.setText(buildHtml(otp), true);
			mailSender.send(message);
			log.info("Verification email dispatched to {} (message id {})", email, message.getMessageID());
		} catch (MessagingException | org.springframework.mail.MailException ex) {
			// The OTP is deliberately not included in the log or the exception message.
			log.error("Failed to send verification email to {}: {}", email, ex.getMessage());
			throw new MailSendFailedException(
					"We could not send the verification email right now. Please try again in a moment.", ex);
		}
	}

	private String buildHtml(String otp) {
		return """
				<!DOCTYPE html>
				<html lang="en">
				<head>
				  <meta charset="UTF-8" />
				  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
				  <title>Tripora verification code</title>
				</head>
				<body style="margin:0;padding:0;background-color:#f0f5f9;font-family:'Segoe UI',Arial,sans-serif;">
				  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f5f9;padding:32px 16px;">
				    <tr>
				      <td align="center">
				        <table role="presentation" width="520" cellpadding="0" cellspacing="0" style="max-width:520px;width:100%%;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 24px rgba(15,76,92,0.12);">
				          <tr>
				            <td style="background:linear-gradient(135deg,#0f4c5c 0deg,#1b7f8c 100%%);padding:28px 36px;">
				              <h1 style="margin:0;color:#ffffff;font-size:26px;letter-spacing:1px;">Tripora</h1>
				              <p style="margin:6px 0 0;color:#cfe8ec;font-size:13px;">Travel &amp; Tourism, made simple</p>
				            </td>
				          </tr>
				          <tr>
				            <td style="padding:36px;">
				              <h2 style="margin:0 0 12px;color:#0f2e38;font-size:20px;">Verify your email address</h2>
				              <p style="margin:0 0 24px;color:#4a6670;font-size:15px;line-height:1.6;">
				                Welcome to Tripora! Use the verification code below to confirm your email and activate your account.
				              </p>
				              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
				                <tr>
				                  <td align="center" style="background-color:#e8f3f5;border:2px dashed #1b7f8c;border-radius:12px;padding:20px;">
				                    <span style="font-size:36px;font-weight:bold;letter-spacing:12px;color:#0f4c5c;">%s</span>
				                  </td>
				                </tr>
				              </table>
				              <p style="margin:24px 0 0;color:#4a6670;font-size:14px;line-height:1.6;">
				                This code expires in <strong>%d minutes</strong>. It can be used only once.
				              </p>
				              <p style="margin:16px 0 0;color:#b04a3a;font-size:13px;line-height:1.6;">
				                For your security, never share this code with anyone. Tripora staff will never ask for it.
				              </p>
				            </td>
				          </tr>
				          <tr>
				            <td style="background-color:#f4f8f9;padding:20px 36px;">
				              <p style="margin:0;color:#7c949c;font-size:12px;">
				                If you did not create a Tripora account, you can safely ignore this email.
				              </p>
				            </td>
				          </tr>
				        </table>
				      </td>
				    </tr>
				  </table>
				</body>
				</html>
				""".formatted(otp, expiryMinutes);
	}
}
