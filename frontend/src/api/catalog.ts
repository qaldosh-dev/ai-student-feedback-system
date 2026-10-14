export interface Teacher {
  id: number;
  name: string;
  department: string;
}

export type TeacherSort = 'NAME_ASC' | 'NAME_DESC' | 'RATING_DESC' | 'RATING_ASC'
  | 'REVIEW_COUNT_DESC' | 'HAS_FEEDBACK_FIRST';

export interface Course {
  id: number;
  code: string;
  name: string;
}

export interface TeacherProfile {
  id: number;
  name: string;
  courseOfferings: { courseId: number; courseCode: string; courseName: string; academicTerm: string }[];
}

export interface CourseDetails {
  courseId: number;
  courseCode: string;
  courseName: string;
  teacherOfferings: { teacherId: number; teacherName: string; academicTerm: string }[];
}

export async function getCourseDetails(courseId: string, signal: AbortSignal): Promise<CourseDetails | null> {
  if (!/^[1-9]\d*$/.test(courseId)) return null;
  const response = await fetch(`/api/courses/${encodeURIComponent(courseId)}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Could not load course details. Please try again.');
  return response.json();
}

export async function getTeacherProfile(teacherId: string, signal: AbortSignal): Promise<TeacherProfile | null> {
  if (!/^[1-9]\d*$/.test(teacherId)) return null;
  const response = await fetch(`/api/teachers/${encodeURIComponent(teacherId)}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Could not load teacher profile. Please try again.');
  return response.json();
}

export async function getTeachers(signal: AbortSignal, query = '', courseQuery = '', sort: TeacherSort = 'NAME_ASC'): Promise<Teacher[]> {
  const search = new URLSearchParams();
  if (query.trim()) search.set('q', query.trim());
  if (courseQuery.trim()) search.set('courseQ', courseQuery.trim());
  if (sort !== 'NAME_ASC') search.set('sort', sort);
  const response = await fetch(`/api/teachers${search.size ? `?${search}` : ''}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (!response.ok) {
    throw new Error('Could not load teachers.');
  }
  return response.json();
}

export async function getCourses(signal: AbortSignal, query = '', teacherQuery = ''): Promise<Course[]> {
  const search = new URLSearchParams();
  if (query.trim()) search.set('q', query.trim());
  if (teacherQuery.trim()) search.set('teacherQ', teacherQuery.trim());
  const response = await fetch(`/api/courses${search.size ? `?${search}` : ''}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (!response.ok) {
    throw new Error('Could not load courses.');
  }
  return response.json();
}
