import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CardComponent,
  CardBodyComponent,
  ColComponent,
  RowComponent,
  ButtonDirective,
  FormControlDirective,
  SpinnerComponent,
  AlertComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AiService, CourseProgressAnalysis, LmsAiQuestionResponse, RagAnswer, RagStatus } from '../../services/ai.service';
import { Course, CourseService } from '../../services/course.service';
import { AuthService } from '../../services/auth.service';

import { PageHeaderComponent } from '../../shared/ui/page-header.component';
interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  recommendation?: string;
  sources?: any[];
}

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    CardBodyComponent,
    ColComponent,
    RowComponent,
    ButtonDirective,
    FormControlDirective,
    SpinnerComponent,
    AlertComponent,
    IconDirective,
    PageHeaderComponent
  ],
  templateUrl: './ai-assistant.component.html'
})
export class AiAssistantComponent implements OnInit {
  // L'onglet RAG est le premier : c'est l'outil propre à l'administration (recherche dans tous les cours)
  activeTab = 'rag';

  // RAG
  ragStatus = signal<RagStatus | null>(null);
  ragIndexing = signal(false);
  ragFullIndexing = signal(false);
  ragQuestion = '';
  ragLoading = signal(false);
  ragAnswer = signal<RagAnswer | null>(null);
  ragError = signal('');

  // Text Agent
  textInput = '';
  textMessages = signal<ChatMessage[]>([]);
  textLoading = signal(false);
  textUploadSuccess = signal('');
  textUploadError = signal('');

  // Video Agent
  videoInput = '';
  videoMessages = signal<ChatMessage[]>([]);
  videoLoading = signal(false);
  youtubeUrl = signal('');
  videoUploadSuccess = signal('');
  videoUploadError = signal('');

  // Progress Analysis
  progressCourseId: number | null = null;
  progressResult = signal<CourseProgressAnalysis | null>(null);
  progressLoading = signal(false);
  progressError = signal('');
  courses = signal<Course[]>([]);
  private courseService = inject(CourseService);

  constructor(private aiService: AiService, private authService: AuthService) {}

  ngOnInit(): void {
    this.aiService.ragStatus().subscribe({
      next: st => this.ragStatus.set(st),
      error: err => this.ragError.set(err.error?.message || 'Service IA injoignable : réessayez dans un instant.')
    });
    // Administration : tous les cours de la plateforme, pour l'analyse de progression
    this.courseService.getTeacherCourses().subscribe({
      next: list => { this.courses.set(list); this.progressCourseId = list[0]?.id ?? null; }
    });
  }

  selectTab(tab: string): void {
    this.activeTab = tab;
  }

  // --- RAG ---
  /** complet : analyser aussi les vidéos et PDF scannés jamais lus (plus long, appels Azure) */
  rebuildRagIndex(complet = false): void {
    this.ragError.set('');
    this.ragIndexing.set(true);
    this.ragFullIndexing.set(complet);
    this.aiService.ragReindex(complet).subscribe({
      next: st => { this.ragStatus.set(st); this.ragIndexing.set(false); this.ragFullIndexing.set(false); },
      error: err => {
        this.ragIndexing.set(false); this.ragFullIndexing.set(false);
        this.ragError.set(err.error?.message || "Mise à jour de l'index impossible.");
      }
    });
  }

  askRag(): void {
    const question = this.ragQuestion.trim();
    if (question.length < 3) { this.ragError.set("Posez une question d'au moins 3 caractères."); return; }
    this.ragError.set('');
    this.ragLoading.set(true);
    this.aiService.ragAsk(question).subscribe({
      next: answer => { this.ragAnswer.set(answer); this.ragLoading.set(false); },
      error: err => { this.ragLoading.set(false); this.ragError.set(err.error?.message || 'Recherche impossible.'); }
    });
  }

  // --- TEXT AGENT METHODS ---
  onTextFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (!file) return;

    this.textUploadSuccess.set('');
    this.textUploadError.set('');
    this.textLoading.set(true);

    this.aiService.uploadTextFile(file).subscribe({
      next: (res) => {
        this.textLoading.set(false);
        this.textUploadSuccess.set(`Fichier "${file.name}" téléversé et analysé par l'Agent Texte (ID: ${res.content_id || res.id || 'OK'}) !`);
      },
      error: (err) => {
        this.textLoading.set(false);
        this.textUploadError.set('Erreur lors du téléversement du document vers LMS_AI.');
      }
    });
  }

  sendTextMessage(): void {
    if (!this.textInput.trim() || this.textLoading()) return;

    const question = this.textInput.trim();
    this.textMessages.update(list => [...list, { role: 'user', content: question }]);
    this.textInput = '';
    this.textLoading.set(true);

    this.aiService.askTextAgent({ question, level: 'intermediate' }).subscribe({
      next: (res: LmsAiQuestionResponse) => {
        this.textLoading.set(false);
        this.textMessages.update(list => [...list, {
          role: 'assistant',
          content: res.answer || 'Réponse générée par l\'Agent Texte.',
          recommendation: res.recommendation,
          sources: res.sources
        }]);
      },
      error: () => {
        this.textLoading.set(false);
        this.textMessages.update(list => [...list, {
          role: 'assistant',
          content: "Désolé, le service IA Texte n'a pas pu répondre. Assurez-vous que LMS_AI est démarré."
        }]);
      }
    });
  }

  // --- VIDEO AGENT METHODS ---
  ingestYoutube(): void {
    if (!this.youtubeUrl().trim()) return;

    this.videoUploadSuccess.set('');
    this.videoUploadError.set('');
    this.videoLoading.set(true);

    this.aiService.ingestVideoUrl(this.youtubeUrl().trim(), 'Vidéo YouTube').subscribe({
      next: (res) => {
        this.videoLoading.set(false);
        this.videoUploadSuccess.set('Vidéo YouTube analysée par l\'Agent Vidéo !');
        this.youtubeUrl.set('');
      },
      error: () => {
        this.videoLoading.set(false);
        this.videoUploadError.set('Erreur lors de l\'ingestion de l\'URL YouTube.');
      }
    });
  }

  onVideoFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (!file) return;

    this.videoUploadSuccess.set('');
    this.videoUploadError.set('');
    this.videoLoading.set(true);

    this.aiService.uploadVideoFile(file).subscribe({
      next: (res) => {
        this.videoLoading.set(false);
        this.videoUploadSuccess.set(`Vidéo "${file.name}" analysée avec succès !`);
      },
      error: () => {
        this.videoLoading.set(false);
        this.videoUploadError.set('Erreur lors du téléversement de la vidéo.');
      }
    });
  }

  sendVideoMessage(): void {
    if (!this.videoInput.trim() || this.videoLoading()) return;

    const question = this.videoInput.trim();
    this.videoMessages.update(list => [...list, { role: 'user', content: question }]);
    this.videoInput = '';
    this.videoLoading.set(true);

    this.aiService.askVideoAgent({ question, level: 'intermediate' }).subscribe({
      next: (res: LmsAiQuestionResponse) => {
        this.videoLoading.set(false);
        this.videoMessages.update(list => [...list, {
          role: 'assistant',
          content: res.answer || 'Réponse de l\'Agent Vidéo.',
          recommendation: res.recommendation
        }]);
      },
      error: () => {
        this.videoLoading.set(false);
        this.videoMessages.update(list => [...list, {
          role: 'assistant',
          content: "Désolé, l'Agent Vidéo n'a pas pu répondre."
        }]);
      }
    });
  }

  // --- PROGRESS ANALYSIS ---
  /** Administration : progression de tous les étudiants du cours choisi (copies de quiz et de TP). */
  analyzeProgress(): void {
    if (!this.progressCourseId) {
      this.progressError.set('Choisissez un cours.');
      return;
    }
    this.progressLoading.set(true);
    this.progressError.set('');
    this.progressResult.set(null);
    this.aiService.analyzeCourseProgress(this.progressCourseId).subscribe({
      next: (res) => {
        this.progressLoading.set(false);
        this.progressResult.set(res);
      },
      error: (err) => {
        this.progressLoading.set(false);
        this.progressError.set(err.error?.message || 'Impossible d\'analyser la progression pour ce cours.');
      }
    });
  }
}
