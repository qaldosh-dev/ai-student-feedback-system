import StatusBadge from './StatusBadge';
export default function AnalyticsPrivacyNotice() {
  return <section aria-labelledby="analytics-privacy-heading">
    <h2 id="analytics-privacy-heading">Analytics Privacy</h2>
    <StatusBadge status="PRIVACY_PROTECTED" />
    <p className="state-message" role="status">Not enough published feedback to display analytics while protecting student privacy.</p>
  </section>;
}
