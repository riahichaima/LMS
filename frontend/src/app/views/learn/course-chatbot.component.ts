import { Component, ElementRef, OnInit, ViewChild, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { LearningService, ChatMessage } from '../../services/learning.service';
import { CourseService } from '../../services/course.service';
import { MarkdownPipe } from '../../shared/markdown.pipe';

/** Message affiché : un résumé garde son titre pour pouvoir être téléchargé. */
interface ChatEntry extends ChatMessage {
  summaryTitle?: string;
  /** Message produit par l'interface (téléchargement) : ni envoyé à l'IA, ni téléchargeable. */
  local?: boolean;
}

const ORDINALS: Record<string, number> = {
  premier: 1, premiere: 1, '1er': 1, '1ere': 1, deuxieme: 2, second: 2, seconde: 2, '2e': 2, '2eme': 2,
  troisieme: 3, '3e': 3, '3eme': 3, quatrieme: 4, '4e': 4, '4eme': 4, cinquieme: 5, '5e': 5, '5eme': 5
};

interface ChatCourse {
  id: number;
  title: string;
}

/**
 * Chatbot IA du cours (Azure via LMS_AI) : bouton flottant, explique le cours, répond aux
 * questions et résume le cours ou la leçon ouverte. Les résumés se téléchargent (PDF ou texte).
 * Dans le lecteur de cours, le cours est fixé par [courseId] ; ailleurs (widget global),
 * l'étudiant choisit parmi ses cours inscrits.
 */
@Component({
  selector: 'app-course-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule, IconDirective, MarkdownPipe],
  templateUrl: './course-chatbot.component.html',
  styleUrl: './course-chatbot.component.scss'
})
export class CourseChatbotComponent implements OnInit {
  courseId = input<number | undefined>(undefined);
  courseTitle = input<string>('');
  lessonId = input<number | undefined>(undefined);
  lessonTitle = input<string | undefined>(undefined);

  open = signal(false);
  messages = signal<ChatEntry[]>([]);
  loading = signal(false);
  error = signal('');
  draft = '';

  // Mode global : cours inscrits de l'étudiant
  myCourses = signal<ChatCourse[]>([]);
  coursesLoading = signal(false);
  selectedCourseId = signal<number | null>(null);

  isGlobal = computed(() => this.courseId() === undefined);
  activeCourseId = computed(() => this.courseId() ?? this.selectedCourseId());
  activeCourseTitle = computed(() =>
    this.isGlobal()
      ? this.myCourses().find(c => c.id === this.selectedCourseId())?.title || ''
      : this.courseTitle()
  );

  @ViewChild('scrollArea') private scrollArea?: ElementRef<HTMLDivElement>;

  constructor(private learningService: LearningService, private courseService: CourseService) {}

  ngOnInit(): void {
    if (this.isGlobal()) this.loadMyCourses();
  }

  toggle(): void {
    this.open.update(v => !v);
    if (this.open()) {
      // Liste rafraîchie à l'ouverture (nouvelle inscription, achat...)
      if (this.isGlobal()) this.loadMyCourses();
      this.scrollToBottom();
    }
  }

  selectCourse(id: number | string | null): void {
    const value = id === null || id === '' ? null : Number(id);
    if (value === this.selectedCourseId()) return;
    this.selectedCourseId.set(value);
    this.messages.set([]);
    this.error.set('');
  }

  send(): void {
    const text = this.draft.trim();
    if (!text || this.loading() || !this.activeCourseId()) return;
    this.draft = '';
    this.messages.update(list => [...list, { role: 'user', content: text }]);
    if (this.handleDownloadRequest(text)) return;
    this.ask('chat');
  }

  /**
   * « Télécharge le 2e résumé en PDF », « je veux le résumé le plus long en pdf »... : le fichier
   * est produit directement par l'interface, sans passer par l'IA.
   */
  private handleDownloadRequest(text: string): boolean {
    const t = text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
    if (!/telecharg|download|exporte|\bpdf\b|enregistre/.test(t)) return false;

    const answers = this.messages().filter(m => m.role === 'assistant' && !m.local);
    const summaries = answers.filter(m => m.summaryTitle);
    let pool = /resum/.test(t) && summaries.length ? summaries : answers;
    const reply = (content: string) => this.messages.update(list => [...list, { role: 'assistant', content, local: true }]);

    if (pool.length === 0) {
      reply(/resum/.test(t)
        ? "Il n'y a pas encore de résumé dans cette conversation. Cliquez sur « Résumé cours » puis demandez-moi de le télécharger."
        : "Il n'y a encore aucune réponse à télécharger dans cette conversation.");
      this.scrollToBottom();
      return true;
    }

    const ordinal = Object.keys(ORDINALS).find(k => new RegExp(`(^|\\s)${k}(\\s|$)`).test(t));
    let target: ChatEntry | undefined;
    if (/plus (long|complet|detaille)/.test(t) && !ordinal) {
      target = pool.reduce((a, b) => (b.content.length > a.content.length ? b : a));
    } else if (/plus court/.test(t) && !ordinal) {
      target = pool.reduce((a, b) => (b.content.length < a.content.length ? b : a));
    } else if (ordinal) {
      target = pool[ORDINALS[ordinal] - 1];
      if (!target) {
        reply(`Il n'y a que ${pool.length} ${pool === summaries ? 'résumé(s)' : 'réponse(s)'} dans cette conversation.`);
        this.scrollToBottom();
        return true;
      }
    } else {
      target = pool[pool.length - 1]; // « télécharge-le » : le dernier
    }

    const asText = /\b(texte|txt|markdown|md)\b/.test(t) && !/\bpdf\b/.test(t);
    const name = target.summaryTitle || 'la réponse du tuteur';
    if (asText) {
      this.downloadText(target);
      reply(`Le fichier texte de « ${name} » est en cours de téléchargement.`);
    } else {
      this.downloadPdf(target);
      reply(`Voici le PDF de « ${name} » : dans la fenêtre qui s'ouvre, choisissez **Enregistrer au format PDF** comme imprimante.`);
    }
    this.scrollToBottom();
    return true;
  }

  summarize(scope: 'lesson' | 'course'): void {
    if (this.loading() || !this.activeCourseId()) return;
    const isLesson = scope === 'lesson' && !!this.lessonTitle();
    const title = isLesson ? `Résumé — ${this.lessonTitle()}` : `Résumé — ${this.activeCourseTitle()}`;
    const label = isLesson
      ? `Résume-moi la leçon « ${this.lessonTitle()} »`
      : `Résume-moi le cours « ${this.activeCourseTitle()} »`;
    this.messages.update(list => [...list, { role: 'user', content: label }]);
    // Le résumé est demandé par l'action "summary" : la consigne détaillée est ajoutée côté IA
    this.ask('summary', isLesson ? this.lessonId() : undefined, true, title);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  /** Fichier texte (Markdown) du résumé. */
  downloadText(entry: ChatEntry): void {
    const title = entry.summaryTitle || `Tuteur IA — ${this.activeCourseTitle() || 'réponse'}`;
    const blob = new Blob([`# ${title}\n\n${entry.content}\n`], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.fileName(title)}.md`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  /** PDF via l'impression du navigateur (« Enregistrer au format PDF »), sans dépendance. */
  downloadPdf(entry: ChatEntry): void {
    const title = entry.summaryTitle || `Tuteur IA — ${this.activeCourseTitle() || 'réponse'}`;
    const body = new MarkdownPipe().transform(entry.content);
    const iframe = document.createElement('iframe');
    iframe.style.cssText = 'position:fixed;width:0;height:0;border:0;right:0;bottom:0';
    document.body.appendChild(iframe);
    const doc = iframe.contentDocument!;
    doc.open();
    doc.write(`<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${this.escape(this.fileName(title))}</title>
      <style>
        body { font-family: 'Segoe UI', Arial, sans-serif; color: #1f2937; line-height: 1.55; margin: 32px 40px; }
        header { border-bottom: 2px solid #4f46e5; margin-bottom: 20px; padding-bottom: 8px; }
        header .brand { color: #4f46e5; font-weight: 700; font-size: 13px; letter-spacing: .08em; }
        h1 { font-size: 22px; margin: 4px 0 0; }
        h2, h3, h4 { margin: 18px 0 6px; }
        pre { background: #f3f4f6; padding: 10px; border-radius: 6px; white-space: pre-wrap; font-size: 12px; }
        code { font-family: Consolas, monospace; }
        footer { margin-top: 28px; font-size: 11px; color: #6b7280; }
      </style></head><body>
      <header><div class="brand">LMS · TUTEUR IA</div><h1>${this.escape(title)}</h1></header>
      ${body}
      <footer>Généré le ${new Date().toLocaleString('fr-FR')}</footer>
      </body></html>`);
    doc.close();
    setTimeout(() => {
      iframe.contentWindow?.focus();
      iframe.contentWindow?.print();
      setTimeout(() => iframe.remove(), 1000);
    }, 250);
  }

  private loadMyCourses(): void {
    this.coursesLoading.set(true);
    this.courseService.getMyEnrollments().subscribe({
      next: (enrollments) => {
        const courses = enrollments
          .map(e => ({ id: (e.courseId ?? e.course?.id) as number, title: e.courseTitle || e.course?.title || 'Cours' }))
          .filter(c => !!c.id);
        this.myCourses.set(courses);
        this.coursesLoading.set(false);
        const selected = this.selectedCourseId();
        if (selected && !courses.some(c => c.id === selected)) this.selectCourse(null);
        if (!this.selectedCourseId() && courses.length === 1) this.selectCourse(courses[0].id);
      },
      error: () => this.coursesLoading.set(false)
    });
  }

  private ask(action: 'chat' | 'summary', lessonId = this.lessonId(), dropLast = false, summaryTitle?: string): void {
    const courseId = this.activeCourseId();
    if (!courseId) return;
    this.loading.set(true);
    this.error.set('');
    // Pour un résumé, le dernier message (libellé affiché) est remplacé par la consigne de l'IA
    const visible = dropLast ? this.messages().slice(0, -1) : this.messages();
    const history: ChatMessage[] = visible.filter(m => !m.local).map(m => ({ role: m.role, content: m.content }));
    this.scrollToBottom();
    this.learningService.chat(courseId, history, action, lessonId).subscribe({
      next: (res) => {
        this.loading.set(false);
        // Réponse ignorée si l'étudiant a changé de cours entre-temps
        if (this.activeCourseId() !== courseId) return;
        this.messages.update(list => [...list, { role: 'assistant', content: res.answer, summaryTitle }]);
        this.scrollToBottom();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || "Le tuteur IA n'a pas pu répondre. Réessayez dans un instant.");
      }
    });
  }

  private fileName(title: string): string {
    return title.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^\w-]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 80) || 'resume';
  }

  private escape(s: string): string {
    return s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!));
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.scrollArea?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
