import { TypeAffectation } from './evaluation.model';

export interface FicheEvaluation {
  id: number;
  employeId: number;
  employeNom: string;
  employePrenom: string;
  typeAffectation: TypeAffectation;
  evaluationId: number;
  evaluationNom: string;
  dateCreation: string;
  reponsesN1: { [key: number]: number };
  noteN1: number;
  commentaireN1: string;
  decisionN2: string | null;
  commentaireN2: string | null;
  decisionEmploye: string | null;
  noteFinale: number | null;
  statut: string;
}

export interface EvaluationN1Request {
  employeId: number;
  evaluationId: number;
  reponses: { [key: number]: number };
  commentaire: string;
}

export interface ValidationN2Request {
  accepte: boolean;
  commentaire: string;
}
