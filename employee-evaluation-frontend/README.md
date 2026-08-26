# 🏦 ATB - Gestion des Évaluations RH

## **Application de gestion des évaluations des employés pour la banque ATB**

---

## 📋 PRÉSENTATION DU PROJET

L'application **ATB - Gestion des Évaluations RH** est une plateforme web complète développée pour la **Arab Tunisian Bank (ATB)**. Elle permet de gérer l'intégralité du cycle de vie des évaluations des employés, depuis la création des campagnes jusqu'à la validation finale par les différents acteurs hiérarchiques.

Ce système remplace les processus manuels et papiers par une solution numérique sécurisée, transparente et efficace, respectant les règles métier spécifiques à la banque.

---

## 🎯 OBJECTIFS DU PROJET

| Objectif | Description |
|----------|-------------|
| **Digitalisation** | Remplacer les évaluations papier par un processus numérique |
| **Traçabilité** | Assurer un suivi complet de chaque évaluation |
| **Hiérarchie** | Respecter la chaîne de validation (N+1 → N+2 → Employé) |
| **Sécurité** | Authentification JWT et gestion des rôles |
| **Simplicité** | Interface intuitive pour tous les utilisateurs |

---

## 🛠️ TECHNOLOGIES UTILISÉES

### Backend (Spring Boot)
| Technologie | Version | Utilisation |
|-------------|---------|-------------|
| **Spring Boot** | 3.3.2 | Framework principal |
| **Spring Security** | 6.x | Authentification et autorisations |
| **Spring Data JPA** | - | ORM et accès aux données |
| **JWT** | 0.11.5 | Tokens d'authentification |
| **MySQL** | 8.x | Base de données relationnelle |
| **BCrypt** | - | Hachage des mots de passe |
| **Lombok** | - | Réduction du code boilerplate |
| **Maven** | - | Gestion des dépendances |

### Frontend (Angular)
| Technologie | Version | Utilisation |
|-------------|---------|-------------|
| **Angular** | 17 | Framework frontend |
| **TypeScript** | 5.4 | Langage principal |
| **Bootstrap** | 5.3.2 | Framework CSS |
| **Font Awesome** | 6.0 | Icônes |
| **ngx-toastr** | 18.0.0 | Notifications |
| **RxJS** | 7.8 | Programmation réactive |

---

## 👥 ACTEURS ET RÔLES

| Rôle | Description | Responsabilités |
|------|-------------|-----------------|
| **ADMIN** | Administrateur | Gestion des employés, campagnes, questions, et consultation de toutes les évaluations |
| **N+1** | Manager direct | Évaluation des collaborateurs (notes + commentaires) |
| **N+2** | Supérieur du N+1 | Validation ou refus des évaluations faites par le N+1 |
| **EMPLOYE** | Collaborateur | Consultation de ses évaluations et acceptation/refus final |

---

## 📊 WORKFLOW COMPLET

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            WORKFLOW D'ÉVALUATION                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐                                                          │
│  │ Étape 1      │                                                          │
│  │ ADMIN        │  Crée les employés et définit la hiérarchie              │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Étape 2      │                                                          │
│  │ ADMIN        │  Crée une campagne d'évaluation (BROUILLON)              │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Étape 3      │                                                          │
│  │ ADMIN        │  Ajoute les questions (BROUILLON)                        │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Étape 4      │                                                          │
│  │ ADMIN        │  Ouvre la campagne (OUVERTE) - Questions figées          │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Étape 5      │                                                          │
│  │ N+1          │  Évalue le collaborateur → EN_ATTENTE_N2                 │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Étape 6      │                                                          │
│  │ N+2          │  Accepte ou refuse → EN_ATTENTE_EMPLOYE ou A_REVISER     │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Étape 7      │                                                          │
│  │ EMPLOYÉ      │  Accepte ou refuse → CLOTUREE ou A_REVISER               │
│  └──────────────┘                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 ARCHITECTURE DU PROJET

```
employee-evaluation/
├── src/                                    # Backend Spring Boot
│   ├── main/
│   │   ├── java/com/atb/employeeevaluation/
│   │   │   ├── config/                     # Configuration (Security, CORS)
│   │   │   ├── controller/                 # REST Controllers
│   │   │   ├── dto/                        # Data Transfer Objects
│   │   │   ├── entity/                     # JPA Entities
│   │   │   ├── enums/                      # Énumérations
│   │   │   ├── exception/                  # Gestion des exceptions
│   │   │   ├── mapper/                     # MapStruct mappers
│   │   │   ├── repository/                 # JPA Repositories
│   │   │   ├── security/                   # JWT, UserDetailsService
│   │   │   └── service/                    # Logique métier
│   │   └── resources/
│   │       ├── application.properties      # Configuration
│   │       └── static/                     # Frontend compilé
│   └── test/                               # Tests unitaires
│
├── employee-evaluation-frontend/           # Frontend Angular
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/                 # Composants Angular
│   │   │   │   ├── dashboard/              # Dashboards par rôle
│   │   │   │   ├── employees/              # CRUD employés
│   │   │   │   ├── evaluations/            # CRUD campagnes
│   │   │   │   ├── questions/              # Gestion des questions
│   │   │   │   ├── fiches/                 # Gestion des évaluations
│   │   │   │   └── profile/                # Profil utilisateur
│   │   │   ├── layout/                     # Header, Sidebar, Footer
│   │   │   ├── pages/                      # Login, 404
│   │   │   ├── services/                   # Services API
│   │   │   ├── models/                     # Modèles TypeScript
│   │   │   ├── guards/                     # AuthGuard, RoleGuard
│   │   │   ├── interceptors/               # Intercepteur JWT
│   │   │   └── shared/                     # Utilitaires partagés
│   │   ├── assets/                         # Images, logos
│   │   ├── environments/                   # Environnements
│   │   └── styles.css                      # Styles globaux ATB
│   ├── angular.json
│   ├── package.json
│   └── tsconfig.json
│
├── pom.xml                                 # Maven (Backend)
├── .gitignore
└── README.md
```

---

## 🔌 ENDPOINTS PRINCIPAUX

### Authentification
| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/auth/login` | Connexion |
| POST | `/api/auth/refresh` | Rafraîchir token |
| POST | `/api/auth/logout` | Déconnexion |
| GET | `/api/auth/me` | Utilisateur courant |

### Employés (ADMIN)
| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/employes` | Liste des employés |
| POST | `/api/employes` | Créer un employé |
| GET | `/api/employes/{id}` | Détails employé |
| PUT | `/api/employes/{id}` | Modifier employé |
| DELETE | `/api/employes/{id}` | Supprimer employé |
| PATCH | `/api/employes/{id}/hierarchie` | Assigner N+1/N+2 |

### Campagnes (ADMIN, N1, N2)
| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/evaluations` | Liste des campagnes |
| POST | `/api/evaluations` | Créer campagne |
| PUT | `/api/evaluations/{id}` | Modifier campagne |
| DELETE | `/api/evaluations/{id}` | Supprimer campagne |
| PATCH | `/api/evaluations/{id}/ouvrir` | Ouvrir campagne |
| PATCH | `/api/evaluations/{id}/fermer` | Fermer campagne |
| PATCH | `/api/evaluations/{id}/cloturer` | Clôturer campagne |

### Questions (ADMIN)
| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/evaluations/{id}/questions` | Ajouter question |
| PUT | `/api/evaluations/questions/{id}` | Modifier question |
| DELETE | `/api/evaluations/questions/{id}` | Supprimer question |
| GET | `/api/evaluations/{id}/questions` | Liste des questions |

### Évaluations (N1, N2, EMPLOYEE)
| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/fiches/evaluer` | N+1 évalue (N1) |
| PATCH | `/api/fiches/{id}/n2` | N+2 valide/refuse (N2) |
| PATCH | `/api/fiches/{id}/employe` | Employé accepte/refuse |
| GET | `/api/fiches/{id}` | Détails fiche |
| GET | `/api/fiches/employe/{id}` | Fiches d'un employé |
| GET | `/api/fiches/evaluation/{id}` | Fiches d'une campagne |

---

## 🗄️ MODÈLE DE DONNÉES

### Tables principales

**`employe`**
```sql
id, matricule, nom, prenom, email, mot_de_passe, role, n1_id, n2_id, actif
```

**`evaluation`**
```sql
id, nom_evaluation, date_debut, date_fin, statut (BROUILLON|OUVERTE|FERMEE|CLOTUREE)
```

**`question`**
```sql
id, libelle, note_max, ordre, evaluation_id
```

**`fiche_evaluation`**
```sql
id, employe_id, evaluation_id, date_creation, reponses_n1 (JSON), note_n1, commentaire_n1,
decision_n2 (ACCEPTEE|REFUSEE), commentaire_n2, decision_employe (ACCEPTEE|REFUSEE), 
note_finale, statut (EN_ATTENTE|EN_COURS_N1|EN_ATTENTE_N2|EN_ATTENTE_EMPLOYE|CLOTUREE|A_REVISER)
```

---

## 🎨 DESIGN UI (ATB)

### Charte graphique
| Élément | Valeur |
|---------|--------|
| **Couleur principale** | `#8b0000` (Rouge bordeaux) |
| **Couleur secondaire** | `#6b0000` |
| **Fond** | `#f5f7fa` (Gris clair) |
| **Blanc** | `#ffffff` |
| **Texte** | `#1e1e2f` |
| **Ombres** | Douces, modernes |
| **Typographie** | Segoe UI, Roboto |

### Pages principales
- **Login** : Page avec logo ATB, panneau gauche marque, formulaire droit
- **Dashboard Admin** : Cartes statistiques, actions rapides, résumé
- **Employés** : Tableau avec recherche, filtrage, icônes CRUD
- **Campagnes** : Gestion complète avec statuts et actions
- **Profil** : Carte utilisateur + informations personnelles

---

## 🔐 SÉCURITÉ

### Authentification JWT
- Connexion avec **matricule** et **mot de passe**
- Token JWT stocké en **localStorage**
- Intercepteur Angular ajoute automatiquement le token aux requêtes
- **Refresh token** pour renouveler la session

### Gestion des rôles
- Protection des endpoints avec `@PreAuthorize`
- Guards Angular (`AuthGuard`, `RoleGuard`)
- Sidebar adaptative selon le rôle

---

## 🧪 IDENTIFIANTS DE TEST

| Rôle | Matricule | Mot de passe | Dashboard |
|------|-----------|--------------|-----------|
| **ADMIN** | `ADMIN001` | `admin123` | `/dashboard/admin` |
| **N+1** | `N1001` | `password123` | `/dashboard/n1` |
| **N+2** | `N2001` | `password123` | `/dashboard/n2` |
| **Employé** | `EMP001` | `password123` | `/dashboard/employee` |

---

## 🚀 INSTALLATION ET LANCEMENT

### Prérequis
- Java 17+
- Node.js 18+
- MySQL 8+
- Maven 3.8+
- Angular CLI 17+

### 1. Cloner le projet
```bash
git clone https://github.com/jaberDS/employee-evaluation.git
cd employee-evaluation
```

### 2. Configurer la base de données
```sql
CREATE DATABASE employee_db;
```

### 3. Lancer le backend
```bash
mvn clean install
mvn spring-boot:run
```

### 4. Lancer le frontend
```bash
cd employee-evaluation-frontend
npm install
ng serve
```

### 5. Accéder à l'application
- Frontend : `http://localhost:4200`
- Backend : `http://localhost:8080`

---

## 📊 DIAGRAMME DE CLASSE

```
┌─────────────────┐          ┌─────────────────┐
│     Employe     │          │   Evaluation    │
├─────────────────┤          ├─────────────────┤
│ - id            │1        *│ - id            │
│ - matricule     │─────────>│ - nomEvaluation │
│ - nom           │          │ - dateDebut     │
│ - prenom        │          │ - dateFin       │
│ - email         │          │ - statut        │
│ - motDePasse    │          └────────┬────────┘
│ - role          │                    │1
│ - n1 (self)     │                    │
│ - n2 (self)     │                    │*
│ - actif         │           ┌────────▼────────┐
└────────┬────────┘           │    Question     │
         │1                   ├─────────────────┤
         │                    │ - id            │
         │*                   │ - libelle       │
         │           ┌────────▼────────┐        │ - noteMax     │
         │           │ FicheEvaluation │        │ - ordre       │
         │           ├─────────────────┤        └─────────────────┘
         └──────────>│ - id            │
                     │ - dateCreation  │
                     │ - reponsesN1    │
                     │ - noteN1        │
                     │ - commentaireN1 │
                     │ - decisionN2    │
                     │ - decisionEmploye│
                     │ - noteFinale    │
                     │ - statut        │
                     └─────────────────┘
```

---

## ✨ AMÉLIORATIONS FUTURES

| Fonctionnalité | Description |
|----------------|-------------|
| **Notifications** | Envoi d'emails/SMS lors des changements de statut |
| **Historique** | Suivi complet des modifications des fiches |
| **Export PDF** | Génération de rapports d'évaluation |
| **Analytics** | Dashboard avec graphiques et statistiques avancées |
| **Multi-langues** | Support de l'arabe et du français |
| **Mobile** | Application mobile responsive |

---

## 👨‍💻 AUTEUR

**Jaber DS**  
Développeur Full Stack  
GitHub : [https://github.com/jaberDS](https://github.com/jaberDS)

---

## 📄 LICENCE

Ce projet est la propriété de **Arab Tunisian Bank (ATB)** et est utilisé exclusivement dans le cadre interne de la banque. Toute reproduction ou distribution est interdite sans autorisation préalable.

---

**© 2026 Arab Tunisian Bank - Tous droits réservés**
