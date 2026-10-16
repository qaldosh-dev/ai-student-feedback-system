import { Link } from 'react-router';

export default function EmptyState({ title, description, action }: {
  title: string; description: string; action?: { to: string; label: string };
}) {
  return <div className="state-message empty-state">
    <h2>{title}</h2><p>{description}</p>
    {action && <Link className="student-link-button" to={action.to}>{action.label}</Link>}
  </div>;
}
