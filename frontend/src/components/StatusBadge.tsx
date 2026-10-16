const statuses = {
  VERIFIED: ['Verified', 'success'], VALID: ['Valid', 'success'], PUBLISHED: ['Published', 'success'],
  PENDING: ['Pending', 'warning'], NEEDS_CORRECTION: ['Needs Correction', 'warning'],
  SUBMITTED: ['Submitted', 'neutral'], PRIVACY_PROTECTED: ['Privacy Protected', 'neutral'],
  AVAILABLE: ['Available', 'success'], UNAVAILABLE: ['Unavailable', 'neutral'],
  INVALID: ['Invalid', 'danger'], UPLOADED: ['Uploaded', 'neutral'], VALIDATING: ['Validating', 'neutral'], NOT_CHECKED: ['Not checked', 'neutral'],
} as const;

export default function StatusBadge({ status }: { status: keyof typeof statuses }) {
  const [label, tone] = statuses[status] ?? ['Status unavailable', 'neutral'];
  return <span className={`status-badge status-${tone}`}>{label}</span>;
}
