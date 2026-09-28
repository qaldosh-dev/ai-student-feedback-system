import type { RelationshipContext } from '../pages/relationshipHighlights';
import { Link } from 'react-router';
import { reviewTargetQuery } from '../pages/reviewTargetQuery';

export default function VerifiedRelationships({ relationships = [] }: { relationships?: RelationshipContext[] }) {
  if (!relationships.length) return null;
  return <div className="verified-relationships">
    <strong>Verified with your schedule</strong>
    <ul>{relationships.map(row => <li key={row.key}>
      {row.label}<span>{row.term}</span>
      <Link className="leave-review-link" to={`/student/reviews/new?${reviewTargetQuery(row.target)}`}
        aria-label={`Leave review for ${row.target.courseCode} with ${row.target.teacherName}, ${row.term}`}>
        Leave Review
      </Link>
    </li>)}</ul>
  </div>;
}
