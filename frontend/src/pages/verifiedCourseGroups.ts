import type { VerifiedCourse } from '../api/scheduleUploads';

export function groupVerifiedCourses(rows: readonly VerifiedCourse[]) {
  const groups = new Map<string, { key: string; course: VerifiedCourse; uploadIds: number[]; groupSections: string[] }>();
  for (const row of rows) {
    const key = JSON.stringify([row.courseOfferingId, row.academicTerm]);
    const section = row.groupSection.trim().replace(/\s+/g, ' ');
    const group = groups.get(key);
    if (!group) {
      groups.set(key, { key, course: row, uploadIds: [row.uploadId], groupSections: [section] });
    } else {
      if (!group.uploadIds.includes(row.uploadId)) group.uploadIds.push(row.uploadId);
      if (!group.groupSections.includes(section)) group.groupSections.push(section);
    }
  }
  for (const group of groups.values()) group.groupSections.sort();
  return [...groups.values()];
}
