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

export async function getTeachers(signal: AbortSignal): Promise<Teacher[]> {
  const response = await fetch('/api/teachers', {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (!response.ok) {
    throw new Error('Could not load teachers.');
  }
  return response.json();
}

export async function getCourses(signal: AbortSignal): Promise<Course[]> {
  const response = await fetch('/api/courses', {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (!response.ok) {
    throw new Error('Could not load courses.');
  }
  return response.json();
}
