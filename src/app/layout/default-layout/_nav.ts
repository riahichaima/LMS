import { INavData } from '@coreui/angular';

// Menus des espaces connectés, regroupés par sections (mêmes adresses et mêmes pages qu'avant)

// Pages d'administration, communes à l'administrateur et au super administrateur
const administrationItems: INavData[] = [
  { title: true, name: 'Administration' },
  {
    name: 'Utilisateurs',
    url: '/admin/users',
    iconComponent: { name: 'cil-people' }
  },
  {
    name: 'Formations',
    url: '/admin/courses',
    iconComponent: { name: 'cil-library-add' }
  },
  { title: true, name: 'Finance & supervision' },
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
  { title: true, name: 'Outils IA' },
  {
    name: 'Assistant IA multi-agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  }
];

export const adminNavItems: INavData[] = [
  {
    name: 'Tableau de bord',
    url: '/dashboard',
    iconComponent: { name: 'cil-speedometer' }
  },
  ...administrationItems
];

// Super administrateur : son espace (validation des admins, contrôle financier) puis les pages d'administration
export const superAdminNavItems: INavData[] = [
  {
    name: 'Espace super admin',
    url: '/super-admin',
    iconComponent: { name: 'cil-speedometer' }
  },
  { title: true, name: 'Super administration' },
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
  {
    name: 'Statistiques plateforme',
    url: '/dashboard',
    iconComponent: { name: 'cil-layers' }
  },
  ...administrationItems
];

export const teacherNavItems: INavData[] = [
  {
    name: 'Tableau de bord',
    url: '/dashboard',
    iconComponent: { name: 'cil-speedometer' }
  },
  { title: true, name: 'Enseignement' },
  {
    name: 'Mes cours & studio',
    url: '/teacher/courses',
    iconComponent: { name: 'cil-book' }
  },
  {
    name: 'Suivi pédagogique',
    url: '/teacher/tracking',
    iconComponent: { name: 'cil-chart-line' }
  },
  {
    name: 'Mes revenus',
    url: '/teacher/earnings',
    iconComponent: { name: 'cil-dollar' }
  },
  { title: true, name: 'Outils IA' },
  {
    name: 'Générateur quiz & TP',
    url: '/teacher/assessments',
    iconComponent: { name: 'cil-task' }
  },
  {
    name: 'Mes quiz & TP générés',
    url: '/teacher/ai-content',
    iconComponent: { name: 'cil-notes' }
  },
  {
    name: 'Assistant IA multi-agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  },
  { title: true, name: 'Communication' },
  {
    name: 'Messagerie',
    url: '/messages',
    iconComponent: { name: 'cil-chat-bubble' }
  },
  {
    name: 'Réunions',
    url: '/meetings',
    iconComponent: { name: 'cil-video' }
  }
];

export const studentNavItems: INavData[] = [
  {
    name: 'Tableau de bord',
    url: '/dashboard',
    iconComponent: { name: 'cil-speedometer' }
  },
  { title: true, name: 'Apprendre' },
  {
    name: 'Catalogue des formations',
    url: '/courses',
    iconComponent: { name: 'cil-book' }
  },
  {
    name: 'Mes inscriptions',
    url: '/my-enrollments',
    iconComponent: { name: 'cil-library' }
  },
  {
    name: 'Assistant IA multi-agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  },
  { title: true, name: 'Communication' },
  {
    name: 'Messagerie',
    url: '/messages',
    iconComponent: { name: 'cil-chat-bubble' }
  },
  {
    name: 'Réunions',
    url: '/meetings',
    iconComponent: { name: 'cil-video' }
  }
];

export const navItems: INavData[] = studentNavItems;
