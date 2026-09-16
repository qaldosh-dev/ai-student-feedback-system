export interface Teacher {
  id: number;
  name: string;
  department: string;
}

export interface Course {
  id: number;
  code: string;
  name: string;
}

export async function getTeachers(signal: AbortSignal, query = '', courseQuery = ''): Promise<Teacher[]> {
  const search = new URLSearchParams();
  if (query.trim()) search.set('q', query.trim());
  if (courseQuery.trim()) search.set('courseQ', courseQuery.trim());
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
