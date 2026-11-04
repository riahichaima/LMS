import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LmsAiQuestionRequest {
  contentId?: number;
  question: string;
  level?: string;
}

export interface LmsAiQuestionResponse {
  answer: string;
  sources?: any[];
  citations?: string[];
  recommendation?: string;
}

export interface AssessmentGenerationRequest {
  chapterIds: number[];
  generateQuiz: boolean;
  generateAssignment: boolean;
  quizQuestionCount: number;
  assignmentExerciseCount: number;
  difficultyLevels: string[];
  teacherId?: number;
  // Types de ressources utilisés comme source (PDF, VIDEO, AUDIO, YOUTUBE_VIDEO, WEB_PAGE, TEXT)
  resourceTypes?: string[];
  /** Rendu : date limite (ISO) et temps de travail en minutes (null = sans limite) */
  dueAt?: string | null;
  quizDurationMinutes?: number | null;
  assignmentDurationMinutes?: number | null;
}

export interface SubmissionCorrectionRequest {
  submissionId?: number;
  assignmentId: number;
  studentAnswer: string;
  assignmentContent?: string;
  studentId: number;
}

export interface SubmissionCorrectionResponse {
  score: number;
  feedback: string;
  suggestions: string[];
}

// Vue enseignant : quiz et TP d'un chapitre avec leurs corrections
export interface ReviewAnswer {
  id: number;
  answerText: string;
  isCorrect: boolean;
  answerOrder: number;
}

export interface ReviewQuestion {
  id: number;
  questionText: string;
  correctAnswer: string;
  explanation: string;
  questionOrder: number;
  answers: ReviewAnswer[];
}

export interface ReviewQuiz {
  id: number;
  title: string;
  difficultyLevel: string;
  questionCount: number;
  timeLimitMinutes: number;
  passingScore: number;
  questions: ReviewQuestion[];
  /** true : généré par l'IA ; false : créé à la main par l'enseignant */
  generatedByAi?: boolean;
  dueAt?: string | null;
  durationMinutes?: number | null;
}

export interface ReviewAssignment {
  id: number;
  title: string;
  difficultyLevel: string;
  exerciseCount: number;
  maxPoints: number;
  timeLimitHours: number;
  createdAt: string;
  statement: string;
  expectedSolution: string;
  generatedByAi?: boolean;
  dueAt?: string | null;
  durationMinutes?: number | null;
}

export interface ChapterReview {
  quizzes: ReviewQuiz[];
  assignments: ReviewAssignment[];
}

export interface ChapterAssessments extends ChapterReview {
  chapterId: number;
  chapterTitle: string;
}

export interface CourseAssessments {
  courseId: number;
  courseTitle: string;
  chapters: ChapterAssessments[];
}

export interface AiModelStatus {
  status: string;
  model_available: boolean;
  error?: string | null;
  provider?: 'azure';
  chat_model?: string;
  transcription_model?: string;
  video_vision?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AiService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getModelStatus(): Observable<AiModelStatus> {
    return this.http.get<AiModelStatus>(`${this.apiUrl}/api/ai/status`);
  }

  uploadTextFile(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/api/ai/text/upload`, formData);
  }

  askTextAgent(request: LmsAiQuestionRequest): Observable<LmsAiQuestionResponse> {
    return this.http.post<LmsAiQuestionResponse>(`${this.apiUrl}/api/ai/text/ask`, request);
  }

  uploadVideoFile(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/api/ai/video/upload`, formData);
  }

  ingestVideoUrl(url: string, title?: string): Observable<any> {
    const formData = new FormData();
    formData.append('url', url);
    if (title) formData.append('title', title);
    return this.http.post(`${this.apiUrl}/api/ai/video/upload`, formData);
  }

  askVideoAgent(request: LmsAiQuestionRequest): Observable<LmsAiQuestionResponse> {
    return this.http.post<LmsAiQuestionResponse>(`${this.apiUrl}/api/ai/video/ask`, request);
  }

  generateAssessments(request: AssessmentGenerationRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/assessments/generate`, request);
  }

  getChapterReview(chapterId: number): Observable<ChapterReview> {
    return this.http.get<ChapterReview>(`${this.apiUrl}/api/assessments/chapter/${chapterId}/review`);
  }

  // Tous les quiz/TP de l'enseignant, par cours et par chapitre
  getTeacherAssessmentsOverview(): Observable<CourseAssessments[]> {
    return this.http.get<CourseAssessments[]>(`${this.apiUrl}/api/assessments/teacher/overview`);
  }

  submitQuiz(quizId: number, studentId: number, answers: Record<string, string>): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/assessments/quizzes/${quizId}/submit?studentId=${studentId}`, answers);
  }

  correctAssignment(request: SubmissionCorrectionRequest): Observable<SubmissionCorrectionResponse> {
    return this.http.post<SubmissionCorrectionResponse>(`${this.apiUrl}/api/assessments/submissions/correct`, request);
  }

  analyzeStudentProgress(studentId: number, courseId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/api/progress/analyze/${studentId}/course/${courseId}`);
  }
}
