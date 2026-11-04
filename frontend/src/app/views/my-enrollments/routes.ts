import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./my-enrollments.component').then(m => m.MyEnrollmentsComponent),
    data: { title: 'Mes Inscriptions' }
  }
];
