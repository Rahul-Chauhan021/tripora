import { Navigate, Outlet } from 'react-router-dom';

export const VERIFIED_FLAG_KEY = 'tripora_email_verified';

/**
 * UX-level guard for /welcome. It only checks the in-memory verification
 * flag that the OTP page sets after the backend confirms verification.
 *
 * NOTE: this is convenience only — real security is enforced by the Spring
 * Boot backend on every protected API. Frontend route protection is never
 * treated as a security boundary.
 */
export default function ProtectedRoute() {
  const verified = sessionStorage.getItem(VERIFIED_FLAG_KEY) === 'true';
  return verified ? <Outlet /> : <Navigate to="/register" replace />;
}
