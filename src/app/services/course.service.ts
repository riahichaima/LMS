import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type ResourceType = 'TEXT' | 'PDF' | 'VIDEO' | 'AUDIO' | 'YOUTUBE_VIDEO' | 'WEB_PAGE';

export interface LessonResource {
  id?: number;
  resourceType: ResourceType;
  title: string;
  externalUrl?: string;
  fileName?: string;
  filePath?: string;
  mimeType?: string;
  size?: number;
}

export interface Lesson {
  id?: number;
  title: string;
  description?: string;
  orderIndex: number;
  resources: LessonResource[];
}

export interface Chapter {
  id?: number;
  title: string;
  description?: string;
  orderIndex: number;
  lessons: Lesson[];
}

export interface Course {
  id?: number;
  title: string;
  description: string;
  status?: string;
  category?: string;
  level?: string;
  teacherName?: string;
  teacherId?: number;
  chaptersCount?: number;
  lessonsCount?: number;
  createdAt?: string;
  chapters?: Chapter[];
}

export interface Enrollment {
  id: number;
  course: Course;
  progressPercentage: number;
  completed: boolean;
  enrolledAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class CourseService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // Public / General Course List & Details
  getCourses(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/api/courses`);
  }

  getCourse(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.apiUrl}/api/courses/${id}`);
  }

  getCourseChapters(courseId: number): Observable<Chapter[]> {
    return this.http.get<Chapter[]>(`${this.apiUrl}/api/courses/${courseId}/chapters`);
  }

  getChapterLessons(chapterId: number): Observable<Lesson[]> {
    return this.http.get<Lesson[]>(`${this.apiUrl}/api/chapters/${chapterId}/lessons`);
  }

  // Teacher Endpoints
  getTeacherCourses(): Observable<Course[]> {
    return this.http.get<Course[]>(`${this.apiUrl}/api/teacher/courses`);
  }

  getTeacherCourseById(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.apiUrl}/api/teacher/courses/${id}`);
  }

  createCourse(course: Course): Observable<Course> {
    return this.http.post<Course>(`${this.apiUrl}/api/teacher/courses`, course);
  }

  createFullCourseWithFiles(courseJson: Course, files: File[]): Observable<Course> {
    const formData = new FormData();
    formData.append('course', JSON.stringify(courseJson));
    files.forEach(f => formData.append('files', f));
    return this.http.post<Course>(`${this.apiUrl}/api/teacher/courses/full`, formData);
  }

  // Modification complète : chapitres, leçons et ressources (nouveaux fichiers éventuels)
  updateFullCourseWithFiles(id: number, courseJson: Course, files: File[]): Observable<Course> {
    const formData = new FormData();
    formData.append('course', JSON.stringify(courseJson));
    files.forEach(f => formData.append('files', f));
    return this.http.put<Course>(`${this.apiUrl}/api/teacher/courses/${id}/full`, formData);
  }

  updateCourse(id: number, course: Course): Observable<Course> {
    return this.http.put<Course>(`${this.apiUrl}/api/teacher/courses/${id}`, course);
  }

  deleteCourse(id: number): Observable<string> {
    return this.http.delete(`${this.apiUrl}/api/teacher/courses/${id}`, { responseType: 'text' });
  }

  getCourseEnrollments(courseId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/api/teacher/courses/${courseId}/enrollments`);
  }

  // Student Endpoints
  getMyEnrollments(): Observable<Enrollment[]> {
    return this.http.get<Enrollment[]>(`${this.apiUrl}/api/student/enrollments`);
  }

  enrollInCourse(courseId: number): Observable<Enrollment> {
    return this.http.post<Enrollment>(`${this.apiUrl}/api/student/enroll/${courseId}`, {});
  }

  updateProgress(enrollmentId: number, progressPercentage: number): Observable<Enrollment> {
    return this.http.put<Enrollment>(
      `${this.apiUrl}/api/student/enrollments/${enrollmentId}/progress?progressPercentage=${progressPercentage}`,
      {}
    );
  }
}
