import { useState } from 'react';

/**
 * Reusable labelled text field with an optional show/hide toggle for
 * password inputs and accessible error wiring.
 */
export default function FormInput({
  label,
  id,
  name,
  type = 'text',
  value,
  onChange,
  error,
  placeholder,
  autoComplete,
  required = false,
  enableToggle = false,
  inputMode,
  maxLength,
}) {
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword && enableToggle ? (revealed ? 'text' : 'password') : type;

  return (
    <div className={`form-field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>
        {label}
        {required && (
          <span className="required-mark" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      <div className="input-wrap">
        <input
          id={id}
          name={name}
          type={inputType}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          autoComplete={autoComplete}
          inputMode={inputMode}
          maxLength={maxLength}
          aria-invalid={error ? 'true' : 'false'}
          aria-describedby={error ? `${id}-error` : undefined}
        />
        {isPassword && enableToggle && (
          <button
            type="button"
            className="toggle-visibility"
            onClick={() => setRevealed((current) => !current)}
            aria-label={revealed ? 'Hide password' : 'Show password'}
            title={revealed ? 'Hide password' : 'Show password'}
          >
            {revealed ? '🙈' : '👁'}
          </button>
        )}
      </div>
      {error && (
        <p className="field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
