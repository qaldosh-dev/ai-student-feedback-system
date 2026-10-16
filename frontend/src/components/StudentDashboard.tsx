import { Link } from 'react-router';

const actions = [
  { to: '/student/schedules', title: 'Upload Schedule', description: 'Upload your SDU schedule and verify your courses.' },
  { to: '/student/verified-courses', title: 'Verified Courses', description: 'View courses that were verified from your schedule.' },
  { to: '/student/reviews/new', title: 'Review a Course', description: 'Leave or manage feedback for your verified courses.' },
  { to: '/student/reviews', title: 'My Reviews', description: 'View your submitted feedback and current statuses.' },
];

export default function StudentDashboard() {
  return <nav className="student-actions" aria-label="Student actions">
    {actions.map(action => <Link key={action.to} to={action.to} className="student-action-card">
      <h2>{action.title}</h2><p>{action.description}</p><span aria-hidden="true">Open →</span>
    </Link>)}
  </nav>;
}
