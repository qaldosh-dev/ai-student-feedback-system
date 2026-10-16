export default function LoadingState({ label, cards = 3 }: { label: string; cards?: number }) {
  return <div className="loading-state" role="status" aria-live="polite">
    <p>{label}</p>
    <div className="skeleton-grid" aria-hidden="true">
      {Array.from({ length: cards }, (_, index) => <div className="skeleton-card" key={index}>
        <span className="skeleton-line" /><span className="skeleton-line short" />
      </div>)}
    </div>
  </div>;
}
