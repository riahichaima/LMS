export interface TpExercise {
  number: number;
  statement: string;
  solution: string;
}

// Le backend enregistre les exercices d'un TP en un seul texte : "Exercice 1 : ...\n\nExercice 2 : ..."
function splitByExercise(text: string | null | undefined): Map<number, string> {
  const parts = new Map<number, string>();
  if (!text) return parts;
  const regex = /Exercice\s+(\d+)\s*:\s*/g;
  const matches = [...text.matchAll(regex)];
  if (matches.length === 0) {
    parts.set(1, text.trim());
    return parts;
  }
  matches.forEach((m, i) => {
    const start = m.index! + m[0].length;
    const end = i + 1 < matches.length ? matches[i + 1].index! : text.length;
    parts.set(Number(m[1]), text.slice(start, end).trim());
  });
  return parts;
}

// Associe chaque énoncé à sa solution attendue
export function splitExercises(statement: string | null | undefined, solution: string | null | undefined): TpExercise[] {
  const statements = splitByExercise(statement);
  const solutions = splitByExercise(solution);
  return [...statements.entries()].map(([number, text]) => ({
    number,
    statement: text,
    solution: solutions.get(number) || ''
  }));
}

export const LEVEL_LABELS: Record<string, string> = {
  BEGINNER: 'Débutant',
  INTERMEDIATE: 'Intermédiaire',
  ADVANCED: 'Avancé'
};

export const LEVEL_COLORS: Record<string, string> = {
  BEGINNER: 'success',
  INTERMEDIATE: 'warning',
  ADVANCED: 'danger'
};
