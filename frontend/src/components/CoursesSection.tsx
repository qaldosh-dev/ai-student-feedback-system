import type { Course } from '../api/catalog';

interface CoursesSectionProps {
  courses: Course[];
  loading: boolean;
  error: boolean;
  query: string;
  onQueryChange: (query: string) => void;
  onRetry: () => void;
}

export default function CoursesSection({ courses, loading, error, query, onQueryChange, onRetry }: CoursesSectionProps) {
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
          {query.trim() ? 'No courses found.' : 'No courses in the catalog yet.'}
        </div>
      ) : (
        <ul className="card-grid course-grid">
          {courses.map((course) => (
            <li key={course.id} className="course-card">
              <span className="course-code">{course.code}</span>
              <h3>{course.name}</h3>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
