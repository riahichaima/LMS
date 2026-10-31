import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  ButtonDirective,
  BadgeComponent,
  TableDirective,
  ProgressBarComponent,
  ProgressComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService, User } from '../../services/auth.service';
import { AdminService, AdminStatistics, UserWithRoleDetails } from '../../services/admin.service';
import { CourseService, Course, Enrollment } from '../../services/course.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    ButtonDirective,
    BadgeComponent,
    TableDirective,
    ProgressBarComponent,
    ProgressComponent,
    IconDirective
  ],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  currentUser: User | null = null;
  loading = signal(false);

  // Admin Data
  adminStats = signal<AdminStatistics | null>(null);
  recentUsers = signal<UserWithRoleDetails[]>([]);

  // Teacher Data
  teacherCourses = signal<Course[]>([]);

  // Student Data
  studentEnrollments = signal<Enrollment[]>([]);

  constructor(
    public authService: AuthService,
    private adminService: AdminService,
    private courseService: CourseService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserSignal();
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading.set(true);

    if (this.authService.isAdmin()) {
      this.adminService.getStatistics().subscribe({
        next: (stats) => this.adminStats.set(stats)
      });
      this.adminService.getUsers().subscribe({
        next: (users) => {
          this.recentUsers.set(users.slice(0, 5));
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
    } else if (this.authService.isTeacher()) {
      this.courseService.getTeacherCourses().subscribe({
        next: (courses) => {
          this.teacherCourses.set(courses);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
    } else {
      this.courseService.getMyEnrollments().subscribe({
        next: (enrollments) => {
          this.studentEnrollments.set(enrollments);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
    }
  }

  get AverageStudentProgress(): number {
    if (this.studentEnrollments().length === 0) return 0;
    const sum = this.studentEnrollments().reduce((acc, curr) => acc + (curr.progressPercentage || 0), 0);
    return Math.round(sum / this.studentEnrollments().length);
  }
}
