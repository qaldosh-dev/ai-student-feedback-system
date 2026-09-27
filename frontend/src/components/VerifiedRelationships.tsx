import type { RelationshipContext } from '../pages/relationshipHighlights';

export default function VerifiedRelationships({ relationships = [] }: { relationships?: RelationshipContext[] }) {
  if (!relationships.length) return null;
  return <div className="verified-relationships">
    <strong>Verified with your schedule</strong>
    <ul>{relationships.slice(0, 3).map(row => <li key={row.key}>
      {row.label}<span>{row.term}</span>
    </li>)}</ul>
    {relationships.length > 3 && <p>And {relationships.length - 3} more verified relationships.</p>}
  </div>;
}
