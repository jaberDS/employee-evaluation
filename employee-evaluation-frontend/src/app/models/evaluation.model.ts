export interface Evaluation {
  id?: number;
  nomEvaluation: string;
  dateDebut: string;
  dateFin: string;
  statut?: string;
  questions?: Question[];
}

export interface Question {
  id?: number;
  libelle: string;
  noteMax: number;
  ordre: number;
  evaluationId?: number;
}
