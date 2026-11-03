import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  ColComponent,
  FormSelectDirective,
  RowComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AiService, CourseAssessments, ReviewAssignment, ReviewQuiz } from '../../../services/ai.service';
import { LEVEL_COLORS, LEVEL_LABELS, TpExercise, splitExercises } from '../exercise-utils';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
// Un quiz ou un TP accompagné du cours et du chapitre auxquels il appartient
interface QuizItem {
  courseTitle: string;
  chapterTitle: string;
  quiz: ReviewQuiz;
}

interface TpItem {
  courseTitle: string;
  chapterTitle: string;
  tp: ReviewAssignment;
  exercises: TpExercise[];
}

@Component({
  selector: 'app-teacher-ai-content',
  standalone: true,
  imports: [
    RouterLink,
    CommonModule,
    FormsModule,
    RowComponent,
    ColComponent,
    CardComponent,
    CardBodyComponent,
    ButtonDirective,
    BadgeComponent,
    AlertComponent,
    SpinnerComponent,
    FormSelectDirective,
    IconDirective,
    PageHeaderComponent
  ],
  templateUrl: './teacher-ai-content.component.html'
})
export class TeacherAiContentComponent implements OnInit {
  courses = signal<CourseAssessments[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  tab = signal<'quiz' | 'tp' | 'corrections'>('quiz');
  // Filtres : 0 = tous les cours, '' = tous les niveaux
  courseFilter = signal(0);
  levelFilter = signal('');

  private filteredCourses = computed(() => {
    const id = this.courseFilter();
    return id ? this.courses().filter(c => c.courseId === id) : this.courses();
  });

  quizzes = computed<QuizItem[]>(() =>
    this.filteredCourses().flatMap(c => c.chapters.flatMap(ch =>
      ch.quizzes
        .filter(q => !this.levelFilter() || q.difficultyLevel === this.levelFilter())
        .map(quiz => ({ courseTitle: c.courseTitle, chapterTitle: ch.chapterTitle, quiz }))
    ))
  );

  tps = computed<TpItem[]>(() =>
    this.filteredCourses().flatMap(c => c.chapters.flatMap(ch =>
      ch.assignments
        .filter(a => !this.levelFilter() || a.difficultyLevel === this.levelFilter())
        .map(tp => ({
          courseTitle: c.courseTitle,
          chapterTitle: ch.chapterTitle,
          tp,
          exercises: splitExercises(tp.statement, tp.expectedSolution)
        }))
    ))
  );

  exerciseCount = computed(() => this.tps().reduce((sum, t) => sum + t.exercises.length, 0));

  constructor(private aiService: AiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.aiService.getTeacherAssessmentsOverview().subscribe({
      next: (data) => {
        this.loading.set(false);
        this.courses.set(data);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger vos quiz et TP. Vérifiez que le backend est démarré.');
      }
    });
  }

  levelLabel(level: string): string {
    return LEVEL_LABELS[level] || level;
  }

  levelColor(level: string): string {
    return LEVEL_COLORS[level] || 'secondary';
  }

  letter(index: number): string {
    return 'ABCDEFGH'[index] || '?';
  }

  correctLetter(quiz: ReviewQuiz, questionIndex: number): string {
    const answers = quiz.questions[questionIndex].answers;
    const i = answers.findIndex(a => a.isCorrect);
    return i >= 0 ? this.letter(i) : '?';
  }

  correctText(quiz: ReviewQuiz, questionIndex: number): string {
    return quiz.questions[questionIndex].answers.find(a => a.isCorrect)?.answerText
      || quiz.questions[questionIndex].correctAnswer;
  }
}
