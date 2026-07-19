import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

// Guards
import { AuthGuard } from './guards/auth.guard';
import { RoleGuard } from './guards/role.guard';

// Pages
import { LoginComponent } from './pages/login/login.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';

// Layout (non utilisé directement dans les routes, mais les composants seront chargés par AppComponent)

// Dashboards
import { AdminDashboardComponent } from './components/dashboard/admin-dashboard/admin-dashboard.component';
import { N1DashboardComponent } from './components/dashboard/n1-dashboard/n1-dashboard.component';
import { N2DashboardComponent } from './components/dashboard/n2-dashboard/n2-dashboard.component';
import { EmployeeDashboardComponent } from './components/dashboard/employee-dashboard/employee-dashboard.component';

// Employés
import { EmployeeListComponent } from './components/employees/employee-list/employee-list.component';
import { EmployeeFormComponent } from './components/employees/employee-form/employee-form.component';
import { EmployeeDetailComponent } from './components/employees/employee-detail/employee-detail.component';

// Campagnes (Evaluations)
import { EvaluationListComponent } from './components/evaluations/evaluation-list/evaluation-list.component';
import { EvaluationFormComponent } from './components/evaluations/evaluation-form/evaluation-form.component';
import { EvaluationDetailComponent } from './components/evaluations/evaluation-detail/evaluation-detail.component';

// Questions
import { QuestionListComponent } from './components/questions/question-list/question-list.component';
import { QuestionFormComponent } from './components/questions/question-form/question-form.component';

// Fiches
import { FicheListComponent } from './components/fiches/fiche-list/fiche-list.component';
import { FicheEvaluationComponent } from './components/fiches/fiche-evaluation/fiche-evaluation.component';
import { FicheDetailComponent } from './components/fiches/fiche-detail/fiche-detail.component';

// N1 feature components
import { EvaluerEmployesComponent } from './components/n1/evaluer-employes/evaluer-employes.component';
import { N1FicheEvaluationComponent } from './components/n1/fiche-evaluation/fiche-evaluation.component';
import { N1HistoriqueComponent } from './components/n1/historique/historique.component';

// Profil
import { ProfileComponent } from './components/profile/profile.component';

const routes: Routes = [
  // Page de connexion (publique)
  { path: 'login', component: LoginComponent },

  // Redirection par défaut vers login
  { path: '', redirectTo: '/login', pathMatch: 'full' },

  // ========== TABLEAUX DE BORD (protégés) ==========
  {
    path: 'dashboard/admin',
    component: AdminDashboardComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'dashboard/n1',
    component: N1DashboardComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'N1'] }
  },
  {
    path: 'dashboard/n2',
    component: N2DashboardComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'N2'] }
  },
  {
    path: 'dashboard/employee',
    component: EmployeeDashboardComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'EMPLOYE'] }
  },

  // ========== EMPLOYÉS (ADMIN seulement) ==========
  {
    path: 'employees',
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      { path: '', component: EmployeeListComponent },
      { path: 'create', component: EmployeeFormComponent },
      { path: 'edit/:id', component: EmployeeFormComponent },
      { path: ':id', component: EmployeeDetailComponent }
    ]
  },

  // ========== CAMPAGNES (ADMIN, N1, N2) ==========
  {
    path: 'evaluations',
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'N1', 'N2'] },
    children: [
      { path: '', component: EvaluationListComponent },
      { path: 'create', component: EvaluationFormComponent },
      { path: 'edit/:id', component: EvaluationFormComponent },
      { path: ':id', component: EvaluationDetailComponent },
      { path: ':id/questions', component: QuestionListComponent }
    ]
  },

  // ========== QUESTIONS (ADMIN seulement) ==========
  {
    path: 'questions',
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      { path: 'create/:evaluationId', component: QuestionFormComponent },
      { path: 'edit/:id', component: QuestionFormComponent }
    ]
  },

  // ========== FICHES (tous les utilisateurs authentifiés) ==========
  {
    path: 'fiches',
    canActivate: [AuthGuard],
    children: [
      { path: '', component: FicheListComponent },
      { path: 'evaluate', component: FicheEvaluationComponent },
      { path: ':id', component: FicheDetailComponent }
    ]
  },

  // ========== N1 — ESPACE MANAGER ==========
  {
    path: 'n1',
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['N1'] },
    children: [
      // Évaluer les employés (liste) — optionnel :campaignId pour pré-sélectionner
      { path: 'evaluer',                              component: EvaluerEmployesComponent },
      { path: 'evaluer/:campaignId',                  component: EvaluerEmployesComponent },
      // Formulaire de questionnaire pour un employé donné
      { path: 'evaluer/:campaignId/employe/:employeeId', component: N1FicheEvaluationComponent },
      // Historique des évaluations soumises
      { path: 'historique',                           component: N1HistoriqueComponent },
      { path: 'historique/:ficheId',                  component: N1HistoriqueComponent },
      // Redirection par défaut
      { path: '', redirectTo: 'evaluer', pathMatch: 'full' }
    ]
  },

  // ========== PROFIL ==========
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [AuthGuard]
  },

  // ========== 404 ==========
  { path: '**', component: NotFoundComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
