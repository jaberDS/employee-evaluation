export interface Employee {
  id?: number;
  matricule: string;
  nom: string;
  prenom: string;
  email: string;
  motDePasse?: string;
  role: string;
  n1Id?: number | null;
  n1Nom?: string | null;
  n1Prenom?: string | null;
  n2Id?: number | null;
  n2Nom?: string | null;
  n2Prenom?: string | null;
  actif?: boolean;
}
