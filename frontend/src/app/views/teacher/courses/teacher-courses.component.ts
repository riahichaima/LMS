import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CardComponent,
  CardBodyComponent,
  ColComponent,
  RowComponent,
  TableDirective,
  ButtonDirective,
  BadgeComponent,
  SpinnerComponent,
  AlertComponent,
  ModalComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  ModalBodyComponent,
  ModalFooterComponent,
  FormControlDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { environment } from '../../../../environments/environment';
import { CourseService, Course, Chapter, Lesson, LessonResource, ResourceType, PricingType } from '../../../services/course.service';
import { AiService } from '../../../services/ai.service';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
@Component({
  selector: 'app-teacher-courses',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    CardBodyComponent,
    ColComponent,
    RowComponent,
    TableDirective,
    ButtonDirective,
    BadgeComponent,
    SpinnerComponent,
    AlertComponent,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    ModalFooterComponent,
    FormControlDirective,
    IconDirective,
    PageHeaderComponent
  ],
  templateUrl: './teacher-courses.component.html'
})
export class TeacherCoursesComponent implements OnInit {
  courses = signal<Course[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  // Course Modal Studio
  createModalVisible = signal(false);
  isEditing = false;
  currentCourseId?: number;

  courseTitle = '';
  courseDescription = '';
  courseStatus = 'PUBLISHED';
  // Tarification : gratuit ou payant (prix en USD, vérifié par le backend)
  pricingType: PricingType = 'FREE';
  price: number | null = null;
  chapters: Chapter[] = [];

  // Files selected for upload
  selectedFilesMap: Map<string, File> = new Map();

  constructor(private courseService: CourseService, private aiService: AiService) {}

  ngOnInit(): void {
    this.loadTeacherCourses();
  }

  loadTeacherCourses(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.courseService.getTeacherCourses().subscribe({
      next: (data) => {
        this.courses.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Erreur lors du chargement de vos cours.');
      }
    });
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.currentCourseId = undefined;
    this.courseTitle = '';
    this.courseDescription = '';
    this.courseStatus = 'PUBLISHED';
    this.pricingType = 'FREE';
    this.price = null;
    this.chapters = [];
    this.selectedFilesMap.clear();
    this.createModalVisible.set(true);
  }

  addChapter(): void {
    this.chapters.push({
      title: `Chapitre ${this.chapters.length + 1}`,
      description: '',
      orderIndex: this.chapters.length + 1,
      lessons: []
    });
  }

  removeChapter(index: number): void {
    this.chapters.splice(index, 1);
  }

  addLesson(chapter: Chapter): void {
    chapter.lessons.push({
      title: `Leçon ${chapter.lessons.length + 1}`,
      description: '',
      orderIndex: chapter.lessons.length + 1,
      resources: []
    });
  }

  removeLesson(chapter: Chapter, lessonIndex: number): void {
    chapter.lessons.splice(lessonIndex, 1);
  }

  // Types de ressources proposés pour une leçon (on peut en combiner plusieurs)
  readonly resourceTypes: { type: ResourceType; label: string; color: string; accept?: string; url?: boolean }[] = [
    { type: 'PDF', label: 'PDF', color: 'primary', accept: '.pdf,application/pdf' },
    { type: 'VIDEO', label: 'Vidéo', color: 'success', accept: 'video/*,.mp4,.webm,.mov,.mkv,.avi' },
    { type: 'AUDIO', label: 'Audio', color: 'info', accept: 'audio/*,.mp3,.wav,.m4a,.ogg' },
    { type: 'YOUTUBE_VIDEO', label: 'YouTube', color: 'danger', url: true },
    { type: 'WEB_PAGE', label: 'Lien web', color: 'warning', url: true },
    { type: 'TEXT', label: 'Texte', color: 'secondary' }
  ];

  resourceInfo(type: ResourceType) {
    return this.resourceTypes.find(r => r.type === type)!;
  }

  addResource(lesson: Lesson, type: ResourceType): void {
    lesson.resources.push({
      resourceType: type,
      title: this.resourceInfo(type).label,
      externalUrl: '',
      fileName: ''
    });
  }

  removeResource(lesson: Lesson, resourceIndex: number): void {
    lesson.resources.splice(resourceIndex, 1);
  }

  onFileSelected(event: any, resource: LessonResource): void {
    const file: File = event.target.files[0];
    if (file) {
      resource.fileName = file.name;
      this.selectedFilesMap.set(file.name, file);
    }
  }

  // Ouvre le studio pré-rempli avec le cours existant (chapitres, leçons, ressources)
  openEditModal(course: Course): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.courseService.getTeacherCourseById(course.id!).subscribe({
      next: (full) => {
        this.isEditing = true;
        this.currentCourseId = full.id;
        this.courseTitle = full.title;
        this.courseDescription = full.description || '';
        this.courseStatus = full.status || 'PUBLISHED';
        this.pricingType = full.pricingType || 'FREE';
        this.price = full.price ?? null;
        this.chapters = (full.chapters || []).map((ch, i) => ({
          id: ch.id,
          title: ch.title,
          description: ch.description || '',
          orderIndex: ch.orderIndex ?? i + 1,
          lessons: (ch.lessons || []).map((les, j) => ({
            id: les.id,
            title: les.title,
            description: les.description || '',
            orderIndex: les.orderIndex ?? j + 1,
            resources: (les.resources || []).map(r => ({
              resourceType: r.resourceType,
              title: r.title,
              externalUrl: r.externalUrl || '',
              filePath: r.filePath,
              mimeType: r.mimeType,
              size: r.size,
              fileName: ''
            }))
          }))
        }));
        this.selectedFilesMap.clear();
        this.createModalVisible.set(true);
      },
      error: () => this.errorMessage.set('Impossible de charger ce cours pour le modifier.')
    });
  }

  // Lien vers un fichier déjà importé (servi par le backend sous /uploads)
  fileUrl(filePath: string): string {
    return `${environment.apiUrl}/${filePath.replace(/\\/g, '/')}`;
  }

  saveCourse(): void {
    if (!this.courseTitle.trim()) {
      this.errorMessage.set('Veuillez renseigner le titre du cours.');
      return;
    }

    if (this.pricingType === 'PAID' && (this.price == null || this.price < 0.5)) {
      this.errorMessage.set('Indiquez un prix d\'au moins 0,50 $ pour un cours payant.');
      return;
    }

    const coursePayload: Course = {
      title: this.courseTitle,
      description: this.courseDescription,
      status: this.courseStatus,
      pricingType: this.pricingType,
      price: this.pricingType === 'PAID' ? this.price : null,
      chapters: this.chapters
    };

    this.loading.set(true);
    this.errorMessage.set('');
    const files = Array.from(this.selectedFilesMap.values());
    // Toujours l'endpoint complet : il enregistre aussi les chapitres, leçons et ressources sans fichier
    const request = this.isEditing && this.currentCourseId
      ? this.courseService.updateFullCourseWithFiles(this.currentCourseId, coursePayload, files)
      : this.courseService.createFullCourseWithFiles(coursePayload, files);

    request.subscribe({
      next: () => {
        this.loading.set(false);
        this.successMessage.set(this.isEditing ? 'Cours modifié avec succès !' : 'Cours enregistré avec succès !');
        this.createModalVisible.set(false);
        this.loadTeacherCourses();
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err.error?.message || 'Erreur lors de la sauvegarde du cours.');
      }
    });
  }

  deleteCourse(course: Course): void {
    if (confirm(`Voulez-vous vraiment supprimer le cours "${course.title}" ? Ses quiz et TP générés seront aussi supprimés.`)) {
      this.courseService.deleteCourse(course.id!).subscribe({
        next: () => {
          this.successMessage.set('Cours supprimé.');
          this.loadTeacherCourses();
        },
        error: (err) => this.errorMessage.set(err.error?.message || 'Erreur lors de la suppression du cours.')
      });
    }
  }
}
