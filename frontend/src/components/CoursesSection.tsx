import type { Course } from '../api/catalog';
import type { RelationshipContext } from '../pages/relationshipHighlights';
import VerifiedRelationships from './VerifiedRelationships';

interface CoursesSectionProps {
  courses: Course[];
  relationships?: Map<number, RelationshipContext[]>;
  loading: boolean;
  error: boolean;
  query: string;
  hasActiveSearch: boolean;
  onQueryChange: (query: string) => void;
  onRetry: () => void;
}

export default function CoursesSection({ courses, relationships, loading, error, query, hasActiveSearch, onQueryChange, onRetry }: CoursesSectionProps) {
  return (
    <section id="courses" className="catalog-section" aria-labelledby="courses-title">
      <div className="section-heading">
        <div>
          <h2 id="courses-title">Courses</h2>
          <p>Get to know the subjects in the catalog.</p>
        </div>
        {!loading && !error && <span className="count">{courses.length} courses</span>}
      </div>

      <div className="section-search">
        <label htmlFor="course-search">Search courses</label>
        <input
          id="course-search"
          type="search"
          placeholder="Search course by code or name..."
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
      </div>

      {loading ? (
        <div className="state-message" role="status">Loading courses…</div>
      ) : error ? (
        <div className="state-message error-message" role="alert">
          <p>We couldn’t load courses. Please try again.</p>
          <button type="button" onClick={onRetry}>Try again</button>
        </div>
      ) : courses.length === 0 ? (
        <div className="state-message" role="status">
          {hasActiveSearch ? 'No courses found.' : 'No courses in the catalog yet.'}
        </div>
      ) : (
        <ul className="card-grid course-grid">
          {courses.map((course) => (
            <li key={course.id} className={`course-card${relationships?.has(course.id) ? ' has-verified-relationship' : ''}`}>
              <div className="catalog-card-main">
                <span className="course-code">{course.code}</span>
                <h3>{course.name}</h3>
              </div>
              <VerifiedRelationships relationships={relationships?.get(course.id)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
