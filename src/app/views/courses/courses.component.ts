import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  SpinnerComponent,
  AlertComponent,
  BadgeComponent,
  ButtonDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { CourseService, Course } from '../../services/course.service';

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    SpinnerComponent,
    AlertComponent,
    BadgeComponent,
    ButtonDirective,
    IconDirective
  ],
  templateUrl: './courses.component.html'
})
export class CoursesComponent implements OnInit {
  courses = signal<Course[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  constructor(private courseService: CourseService) {}

  ngOnInit(): void {
    this.loadCourses();
  }

  loadCourses(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.courseService.getCourses().subscribe({
      next: (data) => {
        this.courses.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger les cours. Vérifiez que le backend est démarré.');
      }
    });
  }

  enroll(courseId: number): void {
    this.courseService.enrollInCourse(courseId).subscribe({
      next: () => {
        this.loadCourses();
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message || "Erreur lors de l'inscription au cours.");
      }
    });
  }
}
