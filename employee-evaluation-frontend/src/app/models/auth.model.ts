export interface LoginRequest {
  matricule: string;
  motDePasse: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  matricule: string;
  nom: string;
  prenom: string;
  role: string;
  expiration: number;
}

export interface User {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
  email: string;
  role: string;
  actif: boolean;
}
