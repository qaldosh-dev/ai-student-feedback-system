import { Link } from 'react-router';

export function AccessDenied() {
  return <div className="error-page">
    <p className="error-code">403</p><h1>Access Denied</h1>
    <p>You do not have access to this area.</p>
    <Link className="student-link-button" to="/">Go Home</Link>
  </div>;
}

export default function ErrorPage() {
  return <main id="main" className="container role-area error-page">
    <p className="error-code">404</p><h1>Page not found</h1>
    <p>The page you are looking for does not exist.</p>
    <Link className="student-link-button" to="/">Go Home</Link>
  </main>;
}
