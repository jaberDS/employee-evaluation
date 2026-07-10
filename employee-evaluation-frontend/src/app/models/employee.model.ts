export interface Employee {
  id?: number;
  matricule: string;
  nom: string;
  prenom: string;
  email: string;
  motDePasse?: string;
  role: string;
  n1Id?: number | null;
  n2Id?: number | null;
  actif?: boolean;
}
