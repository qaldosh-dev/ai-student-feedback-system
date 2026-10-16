import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router';
import AccountArea from './AccountArea';

export default function SiteLayout() {
  const { pathname } = useLocation();
  const catalogPath = pathname === '/' ? '' : '/';
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  useEffect(() => { setMenuOpen(false); }, [pathname]);

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="site-header">
        <div className="container header-content" onKeyDown={event => {
          if (event.key === 'Escape' && menuOpen) { setMenuOpen(false); menuButton.current?.focus(); }
        }}>
          <Link className="brand" to="/" aria-label="Student Feedback home">
            <span className="brand-mark" aria-hidden="true">sdu</span>
            <span className="brand-text">Student Feedback<span>University project</span></span>
          </Link>
          <button className="account-button mobile-nav-toggle" ref={menuButton} aria-expanded={menuOpen}
            aria-controls="main-navigation" onClick={() => setMenuOpen(!menuOpen)}>Menu</button>
          <nav id="main-navigation" className={`main-navigation${menuOpen ? ' is-open' : ''}`} aria-label="Main navigation" onClick={() => setMenuOpen(false)}>
            <a href={`${catalogPath}#teachers`}>Teachers</a>
            <a href={`${catalogPath}#courses`}>Courses</a>
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
