import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AssessmentFile } from './assessment-file.service';
import { Course } from './course.service';
import { ScheduleSummary } from './assessment-schedule.service';

export interface AssessmentSummary {
  id: number;
  title: string;
  difficultyLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  questionCount?: number;
  exerciseCount?: number;
  bestScore: number | null; // meilleur résultat de l'étudiant, en %
  /** Date limite, durée et état du passage de l'étudiant */
  schedule?: ScheduleSummary;
}

export interface ChapterAssessments {
  chapterId: number;
  quizzes: AssessmentSummary[];
  assignments: AssessmentSummary[];
}

export interface LearningView {
  course: Course;
  enrollmentId: number;
  progressPercentage: number;
  completedLessonIds: number[];
  /** Avancement réel de chaque leçon et de chaque ressource (en %) */
  lessonProgress: Record<number, number>;
  resourceProgress: Record<number, number>;
  assessments: ChapterAssessments[];
  /** Quiz / TP importés en fichier par l'enseignant, avec la copie de l'étudiant */
  files: AssessmentFile[];
  /** Heure du serveur (chrono des sujets minutés) */
  serverNow?: string;
}

export interface LessonCompletion {
  progressPercentage: number;
  completedLessonIds: number[];
  lessonProgress: Record<number, number>;
  resourceProgress: Record<number, number>;
  isCompleted: boolean;
}

export interface AssignmentSubmission {
  answer: string;
  points: number;
  maxPoints: number;
  percent: number;
  feedback: string;
  aiCorrection: string;
  expectedSolution: string;
  submittedAt: string;
  errors?: string[];
}

export interface StudentAssignment {
  id: number;
  title: string;
  description: string; // Markdown : énoncés avec blocs de code
  difficultyLevel: string;
  exerciseCount: number;
  maxPoints: number;
  courseId: number;
  courseTitle: string;
  lastSubmission?: AssignmentSubmission;
}

export interface StudentQuiz {
  id: number;
  title: string;
  description?: string;
  difficultyLevel: string;
  questionCount: number;
  timeLimitMinutes?: number;
  passingScore?: number;
  questions: {
    id: number;
    questionText: string;
    questionOrder: number;
    answers: { id: number; answerText: string; answerOrder: number }[];
  }[];
}

export interface QuizResult {
  success: boolean;
  score: number;
  maxScore: number;
  feedback: string;
  detailedExplanation: string; // JSON : [{question, correct, student_answer, expected, explanation}]
  recommendedLevel?: string;
}

export interface CourseStats {
  courseId: number;
  title: string;
  pricingType: string;
  progressPercentage: number;
  isCompleted: boolean;
  lessonsCompleted: number;
  totalLessons: number;
  quizAverage: number | null;
  assignmentAverage: number | null;
  quizzesTotal: number;
  quizzesDone: number;
  tpsTotal: number;
  tpsDone: number;
  enrolledAt: string;
  lastAccessedAt: string | null;
}

export interface StudentStatistics {
  coursesEnrolled: number;
  coursesCompleted: number;
  lessonsCompleted: number;
  totalLessons: number;
  averageProgress: number;
  quizzesTaken: number;
  assignmentsSubmitted: number;
  quizAverage: number | null;
  assignmentAverage: number | null;
  /** Quiz et TP disponibles dans les cours suivis (IA et fichiers), et ceux déjà faits / rendus */
  quizzesTotal: number;
  quizzesDone: number;
  tpsTotal: number;
  tpsDone: number;
  courses: CourseStats[];
  recentResults: { type: 'QUIZ' | 'TP'; title: string; courseTitle: string; percent: number; submittedAt: string }[];
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

@Injectable({ providedIn: 'root' })
export class LearningService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getLearningView(courseId: number): Observable<LearningView> {
    return this.http.get<LearningView>(`${this.apiUrl}/api/student/courses/${courseId}/learn`);
  }

  completeLesson(lessonId: number): Observable<LessonCompletion> {
    return this.http.post<LessonCompletion>(`${this.apiUrl}/api/student/lessons/${lessonId}/complete`, {});
  }

  /** Avancement mesuré d'une ressource : pages PDF lues, part regardée / écoutée, lien ouvert (100 %). */
  updateResourceProgress(resourceId: number, percent: number): Observable<LessonCompletion> {
    return this.http.put<LessonCompletion>(`${this.apiUrl}/api/student/resources/${resourceId}/progress`, { percent: Math.round(percent) });
  }

  // Fichier d'une ressource (PDF, vidéo...) : récupéré avec le jeton puis affiché via une URL blob
  getResourceFile(resourceId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/api/lessons/resources/${resourceId}/download`, { responseType: 'blob' });
  }

  getAssignment(id: number): Observable<StudentAssignment> {
    return this.http.get<StudentAssignment>(`${this.apiUrl}/api/student/assignments/${id}`);
  }

  submitAssignment(id: number, answer: string): Observable<AssignmentSubmission> {
    return this.http.post<AssignmentSubmission>(`${this.apiUrl}/api/student/assignments/${id}/submit`, { answer });
  }

  getQuiz(id: number): Observable<StudentQuiz> {
    return this.http.get<StudentQuiz>(`${this.apiUrl}/api/assessments/quizzes/${id}/view`);
  }

  // Réponses : { "1": "texte de la réponse", ... } dans l'ordre des questions
  submitQuiz(id: number, studentId: number, answers: Record<string, string>): Observable<QuizResult> {
    return this.http.post<QuizResult>(`${this.apiUrl}/api/assessments/quizzes/${id}/submit?studentId=${studentId}`, answers);
  }

  getStatistics(): Observable<StudentStatistics> {
    return this.http.get<StudentStatistics>(`${this.apiUrl}/api/student/statistics`);
  }

  chat(courseId: number, messages: ChatMessage[], action: 'chat' | 'summary', lessonId?: number): Observable<{ answer: string }> {
    return this.http.post<{ answer: string }>(`${this.apiUrl}/api/student/courses/${courseId}/chat`, { messages, action, lessonId });
  }
}
