import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ToastrModule } from 'ngx-toastr';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { AuthGuard } from './guards/auth.guard';
import { RoleGuard } from './guards/role.guard';

// Importer tous les composants (assure-toi que les chemins sont corrects)
import { LoginComponent } from './pages/login/login.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { HeaderComponent } from './layout/header/header.component';
import { SidebarComponent } from './layout/sidebar/sidebar.component';
import { FooterComponent } from './layout/footer/footer.component';
import { AdminDashboardComponent } from './components/dashboard/admin-dashboard/admin-dashboard.component';
import { N1DashboardComponent } from './components/dashboard/n1-dashboard/n1-dashboard.component';
import { N2DashboardComponent } from './components/dashboard/n2-dashboard/n2-dashboard.component';
import { EmployeeDashboardComponent } from './components/dashboard/employee-dashboard/employee-dashboard.component';
import { EmployeeListComponent } from './components/employees/employee-list/employee-list.component';
import { EmployeeFormComponent } from './components/employees/employee-form/employee-form.component';
import { EmployeeDetailComponent } from './components/employees/employee-detail/employee-detail.component';
import { EvaluationListComponent } from './components/evaluations/evaluation-list/evaluation-list.component';
import { EvaluationFormComponent } from './components/evaluations/evaluation-form/evaluation-form.component';
import { EvaluationDetailComponent } from './components/evaluations/evaluation-detail/evaluation-detail.component';
import { QuestionListComponent } from './components/questions/question-list/question-list.component';
import { QuestionFormComponent } from './components/questions/question-form/question-form.component';
import { FicheListComponent } from './components/fiches/fiche-list/fiche-list.component';
import { FicheEvaluationComponent } from './components/fiches/fiche-evaluation/fiche-evaluation.component';
import { FicheDetailComponent } from './components/fiches/fiche-detail/fiche-detail.component';
import { ProfileComponent } from './components/profile/profile.component';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent, NotFoundComponent,
    HeaderComponent, SidebarComponent, FooterComponent,
    AdminDashboardComponent, N1DashboardComponent, N2DashboardComponent, EmployeeDashboardComponent,
    EmployeeListComponent, EmployeeFormComponent, EmployeeDetailComponent,
    EvaluationListComponent, EvaluationFormComponent, EvaluationDetailComponent,
    QuestionListComponent, QuestionFormComponent,
    FicheListComponent, FicheEvaluationComponent, FicheDetailComponent,
    ProfileComponent
  ],
  imports: [
    BrowserModule, BrowserAnimationsModule, CommonModule,
    AppRoutingModule, HttpClientModule, FormsModule, ReactiveFormsModule,
    ToastrModule.forRoot({ positionClass: 'toast-top-right', timeOut: 3000, progressBar: true, closeButton: true })
  ],
  providers: [AuthGuard, RoleGuard, { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }],
  bootstrap: [AppComponent]
})
export class AppModule { }
