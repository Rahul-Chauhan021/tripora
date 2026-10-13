import { BrowserRouter } from 'react-router-dom';
import AppRoutes from './routes/AppRoutes.jsx';
import './styles/auth.css';

/**
 * Root component: wires React Router around the application routes.
 */
function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;
