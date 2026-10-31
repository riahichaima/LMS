import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent,
  FormControlDirective,
  FormCheckComponent,
  FormCheckInputDirective,
  FormCheckLabelDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AiService, SubmissionCorrectionResponse } from '../../services/ai.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-assessments',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent,
    FormControlDirective,
    FormCheckComponent,
    FormCheckInputDirective,
    FormCheckLabelDirective,
    IconDirective
  ],
  templateUrl: './assessments.component.html'
})
export class AssessmentsComponent implements OnInit {
  activeTab: 'quiz' | 'tp' = 'quiz';

  // Quiz State
  quizId = 1;
  answers: Record<string, string> = {
    '1': '',
    '2': '',
    '3': ''
  };
  quizSubmitted = signal(false);
  quizResult = signal<any>(null);
  quizLoading = signal(false);
  quizError = signal('');

  // TP / Assignment State
  assignmentId = 1;
  assignmentSubject = 'Créer une méthode en Java qui vérifie si une chaîne de caractères est un palindrome.';
  studentTpAnswer = '';
  tpResult = signal<SubmissionCorrectionResponse | null>(null);
  tpLoading = signal(false);
  tpError = signal('');

  constructor(
    private route: ActivatedRoute,
    private aiService: AiService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      if (params['id']) {
        this.quizId = +params['id'];
        this.assignmentId = +params['id'];
      }
    });
  }

  submitQuiz(): void {
    const user = this.authService.currentUserSignal();
    if (!user) return;

    this.quizLoading.set(true);
    this.quizError.set('');

    this.aiService.submitQuiz(this.quizId, user.id, this.answers).subscribe({
      next: (res) => {
        this.quizLoading.set(false);
        this.quizSubmitted.set(true);
        this.quizResult.set(res);
      },
      error: () => {
        this.quizLoading.set(false);
        this.quizError.set('Erreur lors de la soumission du Quiz.');
      }
    });
  }

  submitTp(): void {
    const user = this.authService.currentUserSignal();
    if (!user || !this.studentTpAnswer.trim()) return;

    this.tpLoading.set(true);
    this.tpError.set('');
    this.tpResult.set(null);

    this.aiService.correctAssignment({
      assignmentId: this.assignmentId,
      studentAnswer: this.studentTpAnswer,
      assignmentContent: this.assignmentSubject,
      studentId: user.id
    }).subscribe({
      next: (res) => {
        this.tpLoading.set(false);
        this.tpResult.set(res);
      },
      error: () => {
        this.tpLoading.set(false);
        this.tpError.set('Erreur lors de la correction par l\'Agent IA.');
      }
    });
  }
}
