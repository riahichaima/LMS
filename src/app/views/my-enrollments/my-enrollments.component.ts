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
  ProgressBarComponent,
  ProgressComponent
} from '@coreui/angular';
import { CourseService, Enrollment } from '../../services/course.service';

@Component({
  selector: 'app-my-enrollments',
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
    ProgressBarComponent,
    ProgressComponent
  ],
  templateUrl: './my-enrollments.component.html'
})
export class MyEnrollmentsComponent implements OnInit {
  enrollments = signal<Enrollment[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  constructor(private courseService: CourseService) {}

  ngOnInit(): void {
    this.loadEnrollments();
  }

  loadEnrollments(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.courseService.getMyEnrollments().subscribe({
      next: (data) => {
        this.enrollments.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger vos inscriptions. Vérifiez que le backend est démarré.');
      }
    });
  }
}
