import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../services/auth.service';
import { LearningService, QuizResult, StudentQuiz } from '../../services/learning.service';

interface QuestionCorrection {
  question: string;
  correct: boolean;
  student_answer: string;
  expected: string;
  explanation?: string;
}

/** Quiz QCM de l'étudiant : réponses, note, puis correction question par question. */
@Component({
  selector: 'app-quiz-player',
  standalone: true,
  imports: [
    CommonModule, FormsModule, AlertComponent, BadgeComponent, ButtonDirective, CardComponent, CardBodyComponent,
    SpinnerComponent, IconDirective
  ],
  templateUrl: './quiz-player.component.html'
})
export class QuizPlayerComponent implements OnInit {
  quiz = signal<StudentQuiz | null>(null);
  result = signal<QuizResult | null>(null);
  loading = signal(true);
  submitting = signal(false);
  error = signal('');
  // Clé = position de la question (1, 2, ...), valeur = texte de la réponse choisie
  answers: Record<string, string> = {};

  corrections = computed<QuestionCorrection[]>(() => {
    try {
      return JSON.parse(this.result()?.detailedExplanation || '[]');
    } catch {
      return [];
    }
  });

  constructor(
    private route: ActivatedRoute,
    private learningService: LearningService,
    private authService: AuthService,
    public location: Location
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.learningService.getQuiz(id).subscribe({
      next: (q) => {
        q.questions.sort((a, b) => (a.questionOrder ?? 0) - (b.questionOrder ?? 0));
        q.questions.forEach(question => question.answers.sort((a, b) => (a.answerOrder ?? 0) - (b.answerOrder ?? 0)));
        this.quiz.set(q);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger ce quiz.');
      }
    });
  }

  get answeredCount(): number {
    return Object.values(this.answers).filter(Boolean).length;
  }

  submit(): void {
    const q = this.quiz();
    const user = this.authService.currentUserSignal();
    if (!q || !user || this.submitting()) return;
    this.submitting.set(true);
    this.error.set('');
    this.learningService.submitQuiz(q.id, user.id, this.answers).subscribe({
      next: (res) => {
        this.submitting.set(false);
        if (!res.success) {
          this.error.set("La correction n'a pas abouti. Vérifiez que le service IA est démarré.");
          return;
        }
        this.result.set(res);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Erreur lors de la soumission du quiz.');
      }
    });
  }

  retry(): void {
    this.answers = {};
    this.result.set(null);
  }
}
