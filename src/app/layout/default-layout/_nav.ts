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
    name: 'Assistant IA Multi-Agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  }
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
    name: 'Assistant IA Multi-Agents',
    url: '/ai-assistant',
    iconComponent: { name: 'cil-lightbulb' }
  }
];

export const navItems: INavData[] = studentNavItems;
