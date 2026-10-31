import { Routes } from '@angular/router';
import { authGuard } from './services/auth.guard';
import { roleGuard } from './services/role.guard';
import { landingFirstGuard } from './services/landing.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./views/pages/home/home.component').then(m => m.HomeComponent),
    data: {
      title: 'Accueil'
    }
  },
  {
    path: '',
    loadComponent: () => import('./layout').then(m => m.DefaultLayoutComponent),
    canActivate: [authGuard],
    data: {
      title: 'Home'
    },
    children: [
      {
        path: 'dashboard',
        loadChildren: () => import('./views/dashboard/routes').then((m) => m.routes)
      },
      {
        path: 'courses',
        loadChildren: () => import('./views/courses/routes').then((m) => m.routes)
      },
      {
        path: 'my-enrollments',
        loadChildren: () => import('./views/my-enrollments/routes').then((m) => m.routes)
      },
      {
        path: 'ai-assistant',
        loadChildren: () => import('./views/ai-assistant/routes').then((m) => m.routes)
      },
      {
        path: 'admin/users',
        loadComponent: () => import('./views/admin/users/admin-users.component').then(m => m.AdminUsersComponent),
        canActivate: [roleGuard(['ADMIN'])],
        data: { title: 'Administration Utilisateurs' }
      },
      {
        path: 'admin/courses',
        loadComponent: () => import('./views/admin/courses/admin-courses.component').then(m => m.AdminCoursesComponent),
        canActivate: [roleGuard(['ADMIN'])],
        data: { title: 'Administration Formations' }
      },
      {
        path: 'teacher/courses',
        loadComponent: () => import('./views/teacher/courses/teacher-courses.component').then(m => m.TeacherCoursesComponent),
        canActivate: [roleGuard(['TEACHER', 'ADMIN'])],
        data: { title: 'Studio Enseignant' }
      },
      {
        path: 'teacher/assessments',
        loadComponent: () => import('./views/teacher/assessments/teacher-assessments.component').then(m => m.TeacherAssessmentsComponent),
        canActivate: [roleGuard(['TEACHER', 'ADMIN'])],
        data: { title: 'Générateur Quiz/TP IA' }
      },
      {
        path: 'teacher/ai-content',
        loadComponent: () => import('./views/teacher/ai-content/teacher-ai-content.component').then(m => m.TeacherAiContentComponent),
        canActivate: [roleGuard(['TEACHER', 'ADMIN'])],
        data: { title: 'Vos TP et Quiz avec IA' }
      },
      {
        path: 'assessments',
        loadComponent: () => import('./views/assessments/assessments.component').then(m => m.AssessmentsComponent),
        data: { title: 'Évaluations' }
      },
      {
        path: 'assessments/:id',
        loadComponent: () => import('./views/assessments/assessments.component').then(m => m.AssessmentsComponent),
        data: { title: 'Évaluation Détails' }
      }
    ]
  },
  {
    path: '404',
    loadComponent: () => import('./views/pages/page404/page404.component').then(m => m.Page404Component),
    data: {
      title: 'Page 404'
    }
  },
  {
    path: '500',
    loadComponent: () => import('./views/pages/page500/page500.component').then(m => m.Page500Component),
    data: {
      title: 'Page 500'
    }
  },
  {
    path: 'login',
    loadComponent: () => import('./views/pages/login/login.component').then(m => m.LoginComponent),
    canActivate: [landingFirstGuard],
    data: {
      title: 'Connexion'
    }
  },
  {
    path: 'register',
    loadComponent: () => import('./views/pages/register/register.component').then(m => m.RegisterComponent),
    canActivate: [landingFirstGuard],
    data: {
      title: 'Inscription'
    }
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./views/pages/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent),
    data: {
      title: 'Mot de passe oublié'
    }
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./views/pages/reset-password/reset-password.component').then(m => m.ResetPasswordComponent),
    data: {
      title: 'Réinitialiser le mot de passe'
    }
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./views/pages/verify-email/verify-email.component').then(m => m.VerifyEmailComponent),
    data: {
      title: 'Vérification email'
    }
  },
  { path: '**', redirectTo: '' }
];
