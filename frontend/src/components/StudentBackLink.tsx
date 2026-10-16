import { Link } from 'react-router';

export default function StudentBackLink({ to = '/student', children = 'Back to Student Area' }: { to?: string; children?: string }) {
  return <Link className="student-back-link" to={to}><span aria-hidden="true">← </span>{children}</Link>;
}
