export interface Evaluation {
  id?: number;
  nomEvaluation: string;
  dateDebut: string;
  dateFin: string;
  statut?: string;
  questions?: Question[];
}

export type TypeQuestion = 'NOTE' | 'OUI_NON' | 'TEXTE' | 'COMMENTAIRE';

export interface Question {
  id?: number;
  libelle: string;
  description?: string;
  noteMax: number;
  ordre: number;
  typeQuestion: TypeQuestion;
  obligatoire: boolean;
  actif?: boolean;
  evaluationId?: number;
  createdAt?: string;
  updatedAt?: string;
}
