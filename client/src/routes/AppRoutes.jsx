import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from '../components/ProtectedRoute.jsx';
import Register from '../pages/Register.jsx';
import VerifyOtp from '../pages/VerifyOtp.jsx';
import Welcome from '../pages/Welcome.jsx';

/**
 * Application routes for the registration + OTP verification module.
 */
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/register" replace />} />
      <Route path="/register" element={<Register />} />
      <Route path="/verify-otp" element={<VerifyOtp />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/welcome" element={<Welcome />} />
      </Route>
      <Route path="*" element={<Navigate to="/register" replace />} />
    </Routes>
  );
}
