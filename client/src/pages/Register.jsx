import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import FormInput from '../components/FormInput.jsx';
import { extractErrorMessage, extractFieldErrors, registerUser } from '../api/authApi.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const PENDING_EMAIL_KEY = 'tripora_pending_email';
const VERIFIED_FLAG_KEY = 'tripora_email_verified';

/**
 * Registration page. Collects email + password, validates client-side for
 * quick feedback and always re-validates on the Spring Boot backend.
 */
export default function Register() {
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [banner, setBanner] = useState(null); // { type: 'success' | 'error', text }
  const [submitting, setSubmitting] = useState(false);

  const clearField = (name) =>
    setFieldErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });

  const validate = () => {
    const errors = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      errors.email = 'Email is required.';
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      errors.email = 'Please enter a valid email address.';
    }

    if (!password) {
      errors.password = 'Password is required.';
    } else if (password.length < 8) {
      errors.password = 'Password must be at least 8 characters long.';
    } else if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      errors.password = 'Password must contain at least one letter and one number.';
    }

    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password.';
    } else if (password !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match.';
    }

    return errors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return; // guard against double submission

    setBanner(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await registerUser({ email: email.trim(), password, confirmPassword });
      // Only the email is kept for the verification page — never the password.
      sessionStorage.setItem(PENDING_EMAIL_KEY, email.trim());
      sessionStorage.removeItem(VERIFIED_FLAG_KEY);
      navigate('/verify-otp', { state: { email: email.trim() } });
    } catch (error) {
      setFieldErrors(extractFieldErrors(error));
      setBanner({ type: 'error', text: extractErrorMessage(error) });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-shell">
      <div className="auth-deco auth-deco-1" aria-hidden="true" />
      <div className="auth-deco auth-deco-2" aria-hidden="true" />

      <main className="auth-card">
        <header className="auth-brand">
          <span className="brand-mark" aria-hidden="true">
            ✈
          </span>
          <h1 className="brand-name">Tripora</h1>
          <p className="brand-tagline">Travel &amp; Tourism, made simple</p>
        </header>

        <h2 className="auth-title">Create your account</h2>
        <p className="auth-subtitle">
          Start planning your next adventure. We&apos;ll send a verification code to confirm your email.
        </p>

        {banner && (
          <div className={`banner banner-${banner.type}`} role="alert">
            {banner.text}
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <FormInput
            label="Email address"
            id="email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              clearField('email');
            }}
            error={fieldErrors.email}
            placeholder="you@example.com"
            autoComplete="email"
            required
          />

          <FormInput
            label="Password"
            id="password"
            name="password"
            type="password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              clearField('password');
            }}
            error={fieldErrors.password}
            placeholder="At least 8 characters"
            autoComplete="new-password"
            enableToggle
            required
          />

          <FormInput
            label="Confirm password"
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            value={confirmPassword}
            onChange={(e) => {
              setConfirmPassword(e.target.value);
              clearField('confirmPassword');
            }}
            error={fieldErrors.confirmPassword}
            placeholder="Re-enter your password"
            autoComplete="new-password"
            enableToggle
            required
          />

          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? (
              <>
                <span className="spinner" aria-hidden="true" /> Creating account…
              </>
            ) : (
              'Create account'
            )}
          </button>
        </form>

        <p className="auth-note">
          By creating an account you agree to keep your verification code private. Never share codes or
          passwords with anyone.
        </p>
      </main>
    </div>
  );
}
