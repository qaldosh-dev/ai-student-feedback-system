import type { Teacher } from '../api/catalog';
import type { RelationshipContext } from '../pages/relationshipHighlights';
import VerifiedRelationships from './VerifiedRelationships';

interface TeachersSectionProps {
  teachers: Teacher[];
  relationships?: Map<number, RelationshipContext[]>;
  loading: boolean;
  error: boolean;
  query: string;
  hasActiveSearch: boolean;
  onQueryChange: (query: string) => void;
  onRetry: () => void;
}

export default function TeachersSection({ teachers, relationships, loading, error, query, hasActiveSearch, onQueryChange, onRetry }: TeachersSectionProps) {
  return (
    <section id="teachers" className="catalog-section" aria-labelledby="teachers-title">
      <div className="section-heading">
        <div>
          <h2 id="teachers-title">Teachers</h2>
          <p>Meet the people behind the courses.</p>
        </div>
        {!loading && !error && <span className="count">{teachers.length} teachers</span>}
      </div>

      <div className="section-search">
        <label htmlFor="teacher-search">Search teachers</label>
        <input
          id="teacher-search"
          type="search"
          placeholder="Search teacher by name..."
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
      </div>

      {loading ? (
        <div className="state-message" role="status">Loading teachers…</div>
      ) : error ? (
        <div className="state-message error-message" role="alert">
          <p>We couldn’t load teachers. Please try again.</p>
          <button type="button" onClick={onRetry}>Try again</button>
        </div>
      ) : teachers.length === 0 ? (
        <div className="state-message" role="status">
          {hasActiveSearch ? 'No teachers found.' : 'No teachers in the catalog yet.'}
        </div>
      ) : (
        <ul className="card-grid teacher-grid">
          {teachers.map((teacher) => (
            <li key={teacher.id} className={`teacher-card${relationships?.has(teacher.id) ? ' has-verified-relationship' : ''}`}>
              <div className="avatar" aria-hidden="true">
                {teacher.name.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]).join('')}
              </div>
              <div>
                <div className="catalog-card-main">
                  <h3>{teacher.name}</h3>
                  <p>{teacher.department}</p>
                </div>
                <VerifiedRelationships relationships={relationships?.get(teacher.id)} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
