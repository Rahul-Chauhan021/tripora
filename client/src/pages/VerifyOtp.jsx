import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { errorStatus, extractErrorMessage, resendOtpRequest, verifyOtpRequest } from '../api/authApi.js';

const PENDING_EMAIL_KEY = 'tripora_pending_email';
const VERIFIED_FLAG_KEY = 'tripora_email_verified';
const RESEND_COOLDOWN_SECONDS = 60;

/** Masks the email so it is only partially revealed on screen. */
function maskEmail(email) {
  const [local, domain] = email.split('@');
  if (!domain) return email;
  const visible = local.slice(0, Math.min(2, local.length));
  return `${visible}${'*'.repeat(Math.max(1, local.length - visible.length))}@${domain}`;
}

/**
 * OTP verification page. The backend performs ALL OTP validation — the
 * frontend never verifies a code on its own.
 */
export default function VerifyOtp() {
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState(() => location.state?.email || sessionStorage.getItem(PENDING_EMAIL_KEY) || '');
  const [otp, setOtp] = useState('');
  const [fieldError, setFieldError] = useState('');
  const [banner, setBanner] = useState(null); // { type, text }
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(RESEND_COOLDOWN_SECONDS);
  const bannerRef = useRef(null);

  // Without an email to verify there is nothing to do on this page.
  useEffect(() => {
    if (!email) navigate('/register', { replace: true });
  }, [email, navigate]);

  // Resend countdown ticker.
  useEffect(() => {
    if (secondsLeft <= 0) return undefined;
    const timer = setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000);
    return () => clearInterval(timer);
  }, [secondsLeft]);

  useEffect(() => {
    bannerRef.current?.focus();
  }, [banner]);

  const markVerified = useCallback(() => {
    sessionStorage.setItem(VERIFIED_FLAG_KEY, 'true');
    sessionStorage.removeItem(PENDING_EMAIL_KEY);
  }, []);

  const handleVerify = async (event) => {
    event.preventDefault();
    if (verifying) return;

    setBanner(null);
    const digits = otp.trim();
    if (!/^\d{6}$/.test(digits)) {
      setFieldError('Enter the 6-digit code from your email.');
      return;
    }

    setVerifying(true);
    try {
      await verifyOtpRequest({ email, otp: digits });
      markVerified();
      navigate('/welcome');
    } catch (error) {
      if (errorStatus(error) === 409) {
        // Already verified — proceed to the welcome page.
        markVerified();
        navigate('/welcome');
        return;
      }
      if (errorStatus(error) === 410 || errorStatus(error) === 429) {
        // Expired or locked: restart the resend countdown so a new code can
        // be requested right away.
        setSecondsLeft(0);
      }
      setBanner({ type: 'error', text: extractErrorMessage(error) });
    } finally {
      setVerifying(false);
    }
  };

  const handleResend = async () => {
    if (resending || secondsLeft > 0) return;

    setBanner(null);
    setResending(true);
    try {
      await resendOtpRequest({ email });
      setOtp('');
      setSecondsLeft(RESEND_COOLDOWN_SECONDS);
      setBanner({ type: 'success', text: 'A new verification code has been sent. Check your inbox.' });
    } catch (error) {
      if (errorStatus(error) === 429) {
        setSecondsLeft(RESEND_COOLDOWN_SECONDS);
      }
      setBanner({ type: 'error', text: extractErrorMessage(error) });
    } finally {
      setResending(false);
    }
  };

  if (!email) return null;

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

        <h2 className="auth-title">Verify your email</h2>
        <p className="auth-subtitle">
          We sent a 6-digit code to <strong className="masked-email">{maskEmail(email)}</strong>. It expires in 5
          minutes.
        </p>

        <div ref={bannerRef} tabIndex={-1} className="banner-anchor">
          {banner && (
            <div className={`banner banner-${banner.type}`} role="alert">
              {banner.text}
            </div>
          )}
        </div>

        <form className="auth-form" onSubmit={handleVerify} noValidate>
          <div className={`form-field${fieldError ? ' has-error' : ''}`}>
            <label htmlFor="otp">Verification code</label>
            <input
              id="otp"
              name="otp"
              className="otp-input"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={otp}
              onChange={(e) => {
                setOtp(e.target.value.replace(/\D/g, '').slice(0, 6));
                setFieldError('');
              }}
              placeholder="000000"
              aria-invalid={fieldError ? 'true' : 'false'}
              aria-describedby={fieldError ? 'otp-error' : undefined}
            />
            {fieldError && (
              <p className="field-error" id="otp-error" role="alert">
                {fieldError}
              </p>
            )}
          </div>

          <button type="submit" className="btn btn-primary" disabled={verifying || resending}>
            {verifying ? (
              <>
                <span className="spinner" aria-hidden="true" /> Verifying…
              </>
            ) : (
              'Verify email'
            )}
          </button>

          <button
            type="button"
            className="btn btn-ghost"
            onClick={handleResend}
            disabled={resending || verifying || secondsLeft > 0}
          >
            {resending
              ? 'Sending…'
              : secondsLeft > 0
                ? `Resend code in ${secondsLeft}s`
                : 'Resend code'}
          </button>
        </form>

        <p className="auth-note">
          For your security, never share your verification code with anyone — even Tripora staff will never
          ask for it.
        </p>
      </main>
    </div>
  );
}
