import { Component, signal } from '@angular/core';
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
import { AiService, LmsAiQuestionResponse } from '../../services/ai.service';
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
export class AiAssistantComponent {
  activeTab = 'text';

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
  progressCourseId?: number;
  progressResult = signal<any>(null);
  progressLoading = signal(false);
  progressError = signal('');

  constructor(private aiService: AiService, private authService: AuthService) {}

  selectTab(tab: string): void {
    this.activeTab = tab;
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
        this.textUploadSuccess.set(`Fichier "${file.name}" téléversé et indexé par l'Agent Texte (ID: ${res.content_id || res.id || 'OK'}) !`);
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
        this.videoUploadSuccess.set('Vidéo YouTube ingérée et indexée par l\'Agent Vidéo !');
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
        this.videoUploadSuccess.set(`Vidéo "${file.name}" indexée avec succès !`);
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
  analyzeProgress(): void {
    const user = this.authService.currentUserSignal();
    if (!user || !this.progressCourseId) {
      this.progressError.set('Veuillez saisir un ID de cours valide.');
      return;
    }

    this.progressLoading.set(true);
    this.progressError.set('');
    this.progressResult.set(null);

    this.aiService.analyzeStudentProgress(user.id, this.progressCourseId).subscribe({
      next: (res) => {
        this.progressLoading.set(false);
        this.progressResult.set(res);
      },
      error: () => {
        this.progressLoading.set(false);
        this.progressError.set('Impossible d\'analyser la progression pour ce cours.');
      }
    });
  }
}
