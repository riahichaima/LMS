import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpEventType } from '@angular/common/http';
import { AlertComponent, SpinnerComponent } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { Chapter, Course, CourseService } from '../../../services/course.service';
import {
  ACCEPTED_FILES, AssessmentFile, AssessmentFileService, FileSubmission, fileSize
} from '../../../services/assessment-file.service';
import { LEVEL_COLORS, LEVEL_LABELS } from '../exercise-utils';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { ScheduleEditorComponent } from '../../../shared/schedule-editor.component';
import { ScheduleRules, fromLocalInput, toLocalInput } from '../../../services/assessment-schedule.service';

/**
 * Onglet « Importer un fichier » : l'enseignant dépose un quiz ou un TP préparé sur son ordinateur (PDF, Word…),
 * voit les copies rendues par ses étudiants, les télécharge et les note sur 20.
 */
@Component({
  selector: 'app-teacher-files',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertComponent, SpinnerComponent, IconDirective, EmptyStateComponent, ScheduleEditorComponent],
  templateUrl: './teacher-files.component.html',
  styleUrl: './teacher-files.component.scss'
})
export class TeacherFilesComponent implements OnInit {
  readonly accepted = ACCEPTED_FILES;
  readonly levels = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
  readonly levelLabels = LEVEL_LABELS;
  readonly levelColors = LEVEL_COLORS;
  readonly fileSize = fileSize;

  // Formulaire d'import
  courses = signal<Course[]>([]);
  chapters = signal<Chapter[]>([]);
  courseId: number | null = null;
  chapterId: number | null = null;
  type: 'QUIZ' | 'TP' = 'QUIZ';
  title = '';
  instructions = '';
  level = 'BEGINNER';
  // Rendu : date limite (calendrier) et temps de travail en minutes (vides = sans limite)
  dueAtInput = '';
  durationMinutes: number | null = null;
  file = signal<File | null>(null);
  dragOver = signal(false);
  uploadProgress = signal<number | null>(null);

  // Sujets importés et copies
  files = signal<AssessmentFile[]>([]);
  loading = signal(true);
  openId = signal<number | null>(null);
  submissions = signal<FileSubmission[]>([]);
  submissionsLoading = signal(false);
  grades: Record<number, { grade: number | null; feedback: string }> = {};
  savingGrade = signal<number | null>(null);

  error = signal('');
  success = signal('');

  readonly totals = computed(() => {
    const f = this.files();
    return {
      quiz: f.filter(x => x.type === 'QUIZ').length,
      tp: f.filter(x => x.type === 'TP').length,
      copies: f.reduce((n, x) => n + x.submissionCount, 0),
      toGrade: f.reduce((n, x) => n + (x.submissionCount - x.gradedCount), 0)
    };
  });

  constructor(private courseService: CourseService, private service: AssessmentFileService) {}

  ngOnInit(): void {
    this.courseService.getTeacherCourses().subscribe({
      next: courses => {
        this.courses.set(courses);
        if (courses.length) { this.courseId = courses[0].id ?? null; this.loadChapters(); }
      }
    });
    this.load();
  }

  load(): void {
    this.service.teacherFiles().subscribe({
      next: list => { this.files.set(list); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('Impossible de charger vos fichiers.'); }
    });
  }

  loadChapters(): void {
    this.chapters.set([]);
    this.chapterId = null;
    if (!this.courseId) return;
    this.courseService.getCourseChapters(this.courseId).subscribe({
      next: chapters => { this.chapters.set(chapters); this.chapterId = chapters[0]?.id ?? null; }
    });
  }

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.setFile(input.files?.[0] ?? null);
    input.value = '';
  }

  drop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.setFile(event.dataTransfer?.files?.[0] ?? null);
  }

  private setFile(file: File | null): void {
    if (file && file.size > 25 * 1024 * 1024) { this.error.set('Fichier trop volumineux (25 Mo maximum).'); return; }
    this.file.set(file);
    if (file && !this.title.trim()) this.title = file.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').slice(0, 150);
  }

  upload(): void {
    this.error.set('');
    this.success.set('');
    const file = this.file();
    if (!this.chapterId) { this.error.set('Choisissez le cours et le chapitre.'); return; }
    if (!file) { this.error.set('Choisissez le fichier du sujet.'); return; }
    if (this.title.trim().length < 3) { this.error.set('Donnez un titre d\'au moins 3 caractères.'); return; }
    const dueAt = fromLocalInput(this.dueAtInput);
    if (this.dueAtInput && (!dueAt || new Date(dueAt).getTime() <= Date.now())) { this.error.set('La date limite doit être dans le futur.'); return; }
    const duration = this.durationMinutes ? Math.round(this.durationMinutes) : null;
    if (duration != null && (duration < 1 || duration > 1440)) { this.error.set('Temps de travail : entre 1 et 1440 minutes.'); return; }
    this.uploadProgress.set(0);
    this.service.upload({ chapterId: this.chapterId, type: this.type, title: this.title.trim(), instructions: this.instructions.trim(),
                          difficultyLevel: this.level, dueAt, durationMinutes: duration }, file)
      .subscribe({
        next: ev => {
          if (ev.type === HttpEventType.UploadProgress && ev.total) this.uploadProgress.set(Math.round(ev.loaded * 100 / ev.total));
          if (ev.type === HttpEventType.Response && ev.body) {
            this.uploadProgress.set(null);
            this.files.update(list => [ev.body!, ...list]);
            this.success.set(`${this.type === 'QUIZ' ? 'Quiz' : 'TP'} « ${ev.body.title} » ajouté : vos étudiants sont notifiés.`);
            this.file.set(null);
            this.title = '';
            this.instructions = '';
            this.dueAtInput = '';
            this.durationMinutes = null;
          }
        },
        error: err => { this.uploadProgress.set(null); this.error.set(err.error?.message || 'Import impossible.'); }
      });
  }

  /** Date minimale du calendrier : maintenant */
  get minDue(): string {
    return toLocalInput(new Date().toISOString());
  }

  updateRules(f: AssessmentFile, rules: ScheduleRules): void {
    this.files.update(list => list.map(x => (x.id === f.id ? { ...x, dueAt: rules.dueAt, durationMinutes: rules.durationMinutes } : x)));
  }

  toggle(f: AssessmentFile): void {
    if (this.openId() === f.id) { this.openId.set(null); return; }
    this.openId.set(f.id);
    this.submissions.set([]);
    this.submissionsLoading.set(true);
    this.service.submissions(f.id).subscribe({
      next: list => {
        this.submissions.set(list);
        this.grades = {};
        list.forEach(s => this.grades[s.id] = { grade: s.grade, feedback: s.feedback ?? '' });
        this.submissionsLoading.set(false);
      },
      error: () => { this.submissionsLoading.set(false); this.error.set('Impossible de charger les copies.'); }
    });
  }

  saveGrade(f: AssessmentFile, s: FileSubmission): void {
    const g = this.grades[s.id];
    if (g?.grade === null || g?.grade === undefined || g.grade < 0 || g.grade > 20) { this.error.set('La note doit être comprise entre 0 et 20.'); return; }
    this.savingGrade.set(s.id);
    this.service.grade(s.id, g.grade, g.feedback).subscribe({
      next: updated => {
        this.savingGrade.set(null);
        const wasGraded = s.grade !== null;
        this.submissions.update(list => list.map(x => (x.id === updated.id ? updated : x)));
        if (!wasGraded) this.files.update(list => list.map(x => (x.id === f.id ? { ...x, gradedCount: x.gradedCount + 1 } : x)));
        this.success.set(`Copie de ${updated.studentName} notée ${updated.grade}/20 : l'étudiant est notifié.`);
      },
      error: err => { this.savingGrade.set(null); this.error.set(err.error?.message || 'Note non enregistrée.'); }
    });
  }

  downloadSubject(f: AssessmentFile): void {
    this.service.downloadSubject(f.id).subscribe({
      next: blob => AssessmentFileService.save(blob, f.originalName),
      error: () => this.error.set('Téléchargement impossible.')
    });
  }

  downloadCopy(s: FileSubmission): void {
    this.service.downloadSubmission(s.id).subscribe({
      next: blob => AssessmentFileService.save(blob, s.originalName),
      error: () => this.error.set('Téléchargement impossible.')
    });
  }

  remove(f: AssessmentFile): void {
    if (!confirm(`Supprimer « ${f.title} » ?`)) return;
    this.service.delete(f.id).subscribe({
      next: () => { this.files.update(list => list.filter(x => x.id !== f.id)); this.success.set('Sujet supprimé.'); },
      error: err => this.error.set(err.error?.message || 'Suppression impossible.')
    });
  }
}
