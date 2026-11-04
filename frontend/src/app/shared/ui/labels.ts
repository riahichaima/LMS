import { Pipe, PipeTransform } from '@angular/core';

/** Libellés français des rôles (les codes techniques restent inchangés côté API). */
export const ROLE_LABELS: Record<string, string> = {
  STUDENT: 'Étudiant',
  TEACHER: 'Enseignant',
  ADMIN: 'Administrateur',
  SUPER_ADMIN: 'Super admin'
};

/** Classe de pastille (lms-pill--*) associée à chaque rôle. */
export const ROLE_TONES: Record<string, string> = {
  STUDENT: 'primary',
  TEACHER: 'warning',
  ADMIN: 'danger',
  SUPER_ADMIN: 'dark'
};

export function roleCode(role: string | null | undefined): string {
  return (role || '').replace('ROLE_', '').toUpperCase();
}

@Pipe({ name: 'roleLabel', standalone: true })
export class RoleLabelPipe implements PipeTransform {
  transform(role: string | null | undefined): string {
    const code = roleCode(role);
    return ROLE_LABELS[code] ?? code;
  }
}

@Pipe({ name: 'roleTone', standalone: true })
export class RoleTonePipe implements PipeTransform {
  transform(role: string | null | undefined): string {
    return ROLE_TONES[roleCode(role)] ?? 'neutral';
  }
}

/** Texte sans accents ni majuscules, pour les recherches (« Été » trouve « ete »). */
export function normalizeText(value: string | null | undefined): string {
  return (value || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
