import { INavData } from '@coreui/angular';

export const adminNavItems: INavData[] = [
  {
    name: 'Tableau de bord',
    url: '/dashboard',
    iconComponent: { name: 'cil-speedometer' }
  },
  {
    name: 'Administration Users',
    url: '/admin/users',
    iconComponent: { name: 'cil-people' }
  },
  {
    name: 'Administration Cours',
    url: '/admin/courses',
    iconComponent: { name: 'cil-library-add' }
  },
  {
    name: 'Gestion financière',
    url: '/admin/finance',
    iconComponent: { name: 'cil-dollar' }
  },
  {
    name: 'Supervision',
    url: '/supervision',
    iconComponent: { name: 'cil-chart-line' }
  },
  {
    name: 'Assistant IA Multi-Agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  }
];

// Super administrateur : son espace (missions admin + demandes de compte admin) puis le menu administrateur
export const superAdminNavItems: INavData[] = [
  {
    name: 'Espace super admin',
    url: '/super-admin',
    iconComponent: { name: 'cil-speedometer' }
  },
  {
    name: 'Demandes compte admin',
    url: '/super-admin/demandes',
    iconComponent: { name: 'cil-user-follow' }
  },
  {
    name: 'Contrôle financier',
    url: '/super-admin/finance',
    iconComponent: { name: 'cil-chart-pie' }
  },
  ...adminNavItems
];

export const teacherNavItems: INavData[] = [
  {
    name: 'Tableau de bord',
    url: '/dashboard',
    iconComponent: { name: 'cil-speedometer' }
  },
  {
    name: 'Mes Cours & Studio',
    url: '/teacher/courses',
    iconComponent: { name: 'cil-book' }
  },
  {
    name: 'Suivi pédagogique',
    url: '/teacher/tracking',
    iconComponent: { name: 'cil-chart-line' }
  },
  {
    name: 'Générateur Quiz/TP IA',
    url: '/teacher/assessments',
    iconComponent: { name: 'cil-task' }
  },
  {
    name: 'Vos TP et Quiz avec IA',
    url: '/teacher/ai-content',
    iconComponent: { name: 'cil-notes' }
  },
  {
    name: 'Mes revenus',
    url: '/teacher/earnings',
    iconComponent: { name: 'cil-dollar' }
  },
  {
    name: 'Messagerie',
    url: '/messages',
    iconComponent: { name: 'cil-chat-bubble' }
  },
  {
    name: 'Réunions',
    url: '/meetings',
    iconComponent: { name: 'cil-video' }
  },
  {
    name: 'Assistant IA Multi-Agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  }
];

export const studentNavItems: INavData[] = [
  {
    name: 'Tableau de bord',
    url: '/dashboard',
    iconComponent: { name: 'cil-speedometer' }
  },
  {
    name: 'Catalogue Formations',
    url: '/courses',
    iconComponent: { name: 'cil-book' }
  },
  {
    name: 'Mes Inscriptions',
    url: '/my-enrollments',
    iconComponent: { name: 'cil-library' }
  },
  {
    name: 'Messagerie',
    url: '/messages',
    iconComponent: { name: 'cil-chat-bubble' }
  },
  {
    name: 'Réunions',
    url: '/meetings',
    iconComponent: { name: 'cil-video' }
  },
  {
    name: 'Assistant IA Multi-Agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  }
];

export const navItems: INavData[] = studentNavItems;
