package com.rahul.tripora_Backend.service;

import com.rahul.tripora_Backend.dto.ApiResponse;
import com.rahul.tripora_Backend.dto.RegistrationRequest;
import com.rahul.tripora_Backend.dto.ResendOtpRequest;
import com.rahul.tripora_Backend.dto.VerifyOtpRequest;

/**
 * Registration, OTP verification and resend orchestration.
 */
public interface RegistrationService {

	ApiResponse register(RegistrationRequest request);

	ApiResponse verifyOtp(VerifyOtpRequest request);

	ApiResponse resendOtp(ResendOtpRequest request);
}
