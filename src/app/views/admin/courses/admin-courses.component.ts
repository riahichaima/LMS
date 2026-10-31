import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
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
  FormSelectDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AdminService, UserWithRoleDetails } from '../../../services/admin.service';
import { Course } from '../../../services/course.service';

@Component({
  selector: 'app-admin-courses',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
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
    FormSelectDirective,
    IconDirective
  ],
  templateUrl: './admin-courses.component.html'
})
export class AdminCoursesComponent implements OnInit {
  courses = signal<Course[]>([]);
  teachers = signal<UserWithRoleDetails[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  selectedCourse: Course | null = null;
  selectedTeacherId: number | null = null;
  assignModalVisible = signal(false);

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.adminService.getAdminCourses().subscribe({
      next: (data) => {
        this.courses.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Erreur de chargement des formations.');
      }
    });

    this.adminService.getUsers().subscribe({
      next: (users) => {
        this.teachers.set(users.filter(u => u.role.includes('TEACHER') || u.role.includes('ADMIN')));
      }
    });
  }

  openAssignModal(course: Course): void {
    this.selectedCourse = course;
    this.selectedTeacherId = course.teacherId || null;
    this.assignModalVisible.set(true);
  }

  saveAssignment(): void {
    if (!this.selectedCourse || !this.selectedTeacherId) return;

    this.adminService.assignTeacherToCourse(this.selectedCourse.id!, this.selectedTeacherId).subscribe({
      next: () => {
        this.successMessage.set(`Formateur attribué avec succès à "${this.selectedCourse?.title}".`);
        this.assignModalVisible.set(false);
        this.loadData();
      },
      error: () => this.errorMessage.set("Échec de l'attribution du formateur.")
    });
  }
}
