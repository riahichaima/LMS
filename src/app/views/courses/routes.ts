import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./courses.component').then(m => m.CoursesComponent),
    data: { title: 'Cours' }
  }
];
