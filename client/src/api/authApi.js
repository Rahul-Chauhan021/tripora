import axios from 'axios';

/**
 * Axios client for the Tripora auth endpoints.
 *
 * The API is called through a relative base URL so no environment variable
 * is needed in the frontend: during development the Vite dev server proxies
 * /api to the Spring Boot backend (see vite.config.js), and in production
 * the app should be served behind the same origin (or a reverse proxy).
 */
const authApi = axios.create({
  baseURL: '/api/auth',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

/**
 * Extracts a human-readable message from a backend ApiError body, falling
 * back to a network-error explanation when the server is unreachable.
 */
export function extractErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (error?.response?.data) {
    const { fieldErrors, message } = error.response.data;
    if (fieldErrors && Object.keys(fieldErrors).length > 0) {
      return Object.values(fieldErrors).join(' ');
    }
    if (message) return message;
  }
  if (error?.request && !error?.response) {
    return 'Unable to reach the Tripora server. Please check your connection and try again.';
  }
  return error?.message || fallback;
}

/**
 * Returns the backend field-error map ({ email: '...', password: '...' })
 * so each input can display its own message.
 */
export function extractFieldErrors(error) {
  const fieldErrors = error?.response?.data?.fieldErrors;
  return fieldErrors && Object.keys(fieldErrors).length > 0 ? { ...fieldErrors } : {};
}

/** Returns the HTTP status of a backend error, or 0 for network failures. */
export function errorStatus(error) {
  return error?.response?.status ?? 0;
}

export const registerUser = async (payload) => (await authApi.post('/register', payload)).data;

export const verifyOtpRequest = async (payload) => (await authApi.post('/verify-otp', payload)).data;

export const resendOtpRequest = async (payload) => (await authApi.post('/resend-otp', payload)).data;

export default authApi;
