import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./ai-assistant.component').then(m => m.AiAssistantComponent),
    data: { title: 'Assistant IA' }
  }
];
