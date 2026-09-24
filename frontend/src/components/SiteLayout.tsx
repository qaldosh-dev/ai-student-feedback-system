import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import AccountArea from './AccountArea';

export default function SiteLayout() {
  const { currentUser, checking, sessionError } = useAuth();
  const { pathname } = useLocation();
  const catalogPath = pathname === '/' ? '' : '/';

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="site-header">
        <div className="container header-content">
          <Link className="brand" to="/" aria-label="Student Feedback home">
            <span className="brand-mark" aria-hidden="true">sdu</span>
            <span className="brand-text">Student Feedback<span>University project</span></span>
          </Link>
          <nav aria-label="Main navigation">
            <a href={`${catalogPath}#teachers`}>Teachers</a>
            <a href={`${catalogPath}#courses`}>Courses</a>
            {!checking && !sessionError && currentUser?.emailVerified && <NavLink to="/account-setup">Account setup</NavLink>}
            {!checking && !sessionError && currentUser?.roles.includes('STUDENT') && <NavLink to="/student">Student Area</NavLink>}
            {!checking && !sessionError && currentUser?.roles.includes('TEACHER') && <NavLink to="/teacher">Teacher Area</NavLink>}
            {!checking && !sessionError && currentUser?.roles.includes('ADMIN') && <NavLink to="/admin">Admin Area</NavLink>}
          </nav>
          <AccountArea />
        </div>
      </header>
      <Outlet />
      <footer className="container site-footer">
        <p>AI Student Feedback System</p>
        <p>SDU University · Student MVP</p>
      </footer>
    </>
  );
}
