import { useState } from 'react';

/**
 * Landing page after successful email verification.
 */
export default function Welcome() {
  const [notice, setNotice] = useState('');

  return (
    <div className="auth-shell">
      <div className="auth-deco auth-deco-1" aria-hidden="true" />
      <div className="auth-deco auth-deco-2" aria-hidden="true" />

      <main className="auth-card welcome-card">
        <header className="auth-brand">
          <span className="brand-mark" aria-hidden="true">
            ✈
          </span>
          <h1 className="brand-name">Tripora</h1>
          <p className="brand-tagline">Travel &amp; Tourism, made simple</p>
        </header>

        <div className="welcome-badge" aria-hidden="true">
          ✓
        </div>

        <h2 className="auth-title">Email verified successfully!</h2>
        <p className="auth-subtitle">
          Welcome aboard, traveller! Your Tripora account is now active and ready for your next adventure.
        </p>

        {notice && (
          <div className="banner banner-info" role="status">
            {notice}
          </div>
        )}

        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setNotice('The travel dashboard will be available in a future development phase. Stay tuned!')}
        >
          Continue
        </button>
      </main>
    </div>
  );
}
