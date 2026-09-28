export default function RatingInput({ value, onChange }: { value: number | null; onChange: (rating: number) => void }) {
  return <fieldset className="rating-input">
    <legend>Your rating</legend>
    <div className="rating-stars">
      {[1, 2, 3, 4, 5].map(rating => <button key={rating} type="button"
        className={`rating-star${value !== null && rating <= value ? ' is-filled' : ''}`}
        aria-label={`${rating} out of 5`} aria-pressed={value === rating}
        onClick={() => onChange(rating)}>
        <span aria-hidden="true">{value !== null && rating <= value ? '★' : '☆'}</span>
      </button>)}
    </div>
    <p aria-live="polite">{value === null ? 'No rating selected.' : `Your rating: ${value} / 5`}</p>
  </fieldset>;
}
