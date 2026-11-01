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
        path: 'super-admin',
        loadComponent: () => import('./views/super-admin/admin-approvals.component').then(m => m.AdminApprovalsComponent),
        canActivate: [roleGuard(['SUPER_ADMIN'])],
        data: { title: 'Espace super administrateur' }
      },
      {
        path: 'super-admin/demandes',
        loadComponent: () => import('./views/super-admin/admin-requests.component').then(m => m.AdminRequestsComponent),
        canActivate: [roleGuard(['SUPER_ADMIN'])],
        data: { title: 'Demandes compte admin' }
      },
      { path: 'super-admin/approvals', redirectTo: 'super-admin/demandes' },
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
        path: 'teacher/earnings',
        loadComponent: () => import('./views/teacher/earnings/teacher-earnings.component').then(m => m.TeacherEarningsComponent),
        canActivate: [roleGuard(['TEACHER'])],
        data: { title: 'My Earnings' }
      },
      {
        path: 'admin/finance',
        loadComponent: () => import('./views/admin/finance/admin-finance.component').then(m => m.AdminFinanceComponent),
        canActivate: [roleGuard(['ADMIN'])],
        data: { title: 'Financial Management' }
      },
      {
        path: 'super-admin/finance',
        loadComponent: () => import('./views/super-admin/finance/platform-finance.component').then(m => m.PlatformFinanceComponent),
        canActivate: [roleGuard(['SUPER_ADMIN'])],
        data: { title: 'Platform Financial Control' }
      },
      // Retour de Stripe Checkout (l'accès n'est accordé qu'après confirmation serveur)
      {
        path: 'payment/success',
        loadComponent: () => import('./views/payment/payment-success.component').then(m => m.PaymentSuccessComponent),
        canActivate: [roleGuard(['STUDENT'])],
        data: { title: 'Paiement' }
      },
      {
        path: 'payment/cancel',
        loadComponent: () => import('./views/payment/payment-cancel.component').then(m => m.PaymentCancelComponent),
        canActivate: [roleGuard(['STUDENT'])],
        data: { title: 'Paiement annulé' }
      },
      {
        path: 'teacher/ai-content',
        loadComponent: () => import('./views/teacher/ai-content/teacher-ai-content.component').then(m => m.TeacherAiContentComponent),
        canActivate: [roleGuard(['TEACHER', 'ADMIN'])],
        data: { title: 'Vos TP et Quiz avec IA' }
      },
      // Espace étudiant : lecture du cours, quiz, TP et statistiques
      {
        path: 'learn/quiz/:id',
        loadComponent: () => import('./views/learn/quiz-player.component').then(m => m.QuizPlayerComponent),
        canActivate: [roleGuard(['STUDENT'])],
        data: { title: 'Quiz' }
      },
      {
        path: 'learn/tp/:id',
        loadComponent: () => import('./views/learn/assignment-player.component').then(m => m.AssignmentPlayerComponent),
        canActivate: [roleGuard(['STUDENT'])],
        data: { title: 'TP' }
      },
      {
        path: 'learn/:courseId',
        loadComponent: () => import('./views/learn/course-player.component').then(m => m.CoursePlayerComponent),
        canActivate: [roleGuard(['STUDENT'])],
        data: { title: 'Cours' }
      },
      {
        path: 'teacher/tracking',
        loadComponent: () => import('./views/teacher/tracking/teacher-tracking.component').then(m => m.TeacherTrackingComponent),
        canActivate: [roleGuard(['TEACHER'])],
        data: { title: 'Suivi pédagogique' }
      },
      {
        path: 'profile',
        loadComponent: () => import('./views/profile/profile.component').then(m => m.ProfileComponent),
        data: { title: 'Mon profil' }
      },
      {
        path: 'profile/:id',
        loadComponent: () => import('./views/profile/profile.component').then(m => m.ProfileComponent),
        data: { title: 'Profil' }
      },
      // Ancienne adresse du profil formateur
      { path: 'teachers/:id', redirectTo: 'profile/:id' },
      {
        path: 'supervision',
        loadComponent: () => import('./views/supervision/supervision.component').then(m => m.SupervisionComponent),
        canActivate: [roleGuard(['ADMIN'])],
        data: { title: 'Supervision' }
      },
      {
        path: 'messages',
        loadComponent: () => import('./views/messages/messages.component').then(m => m.MessagesComponent),
        canActivate: [roleGuard(['STUDENT', 'TEACHER'])],
        data: { title: 'Messagerie' }
      },
      // Les statistiques sont fusionnées dans le tableau de bord étudiant
      { path: 'my-statistics', redirectTo: 'dashboard' },
      // Anciennes adresses de la page d'évaluations (quiz/TP fictifs) : renvoyées vers le cours
      { path: 'assessments/:id', redirectTo: 'learn/:id' },
      { path: 'assessments', redirectTo: 'my-enrollments' }
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
