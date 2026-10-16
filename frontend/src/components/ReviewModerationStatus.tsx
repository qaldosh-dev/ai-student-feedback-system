import StatusBadge from './StatusBadge';
import type { ReviewStatusResponse } from '../api/reviews';

export default function ReviewModerationStatus({ status, moderationReasons }: Pick<ReviewStatusResponse, 'status' | 'moderationReasons'>) {
  return <>
    <p><strong>Submission Status:</strong> <StatusBadge status={status} /></p>
    {status === 'SUBMITTED' && <p>Your feedback is awaiting moderation. Moderation is not complete.</p>}
    {status === 'PUBLISHED' && <p>Your feedback passed moderation.</p>}
    {status === 'NEEDS_CORRECTION' && <>
      <p>Your feedback needs correction before publication.</p>
      <ul>{moderationReasons.map(reason => <li key={reason.category}>{reason.message}</li>)}</ul>
    </>}
  </>;
}
