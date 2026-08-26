import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-not-found',
  templateUrl: './not-found.component.html',
  styleUrls: ['./not-found.component.css']
})
export class NotFoundComponent {
  constructor(private authService: AuthService) {}

  /** Il n'existe pas de route `/dashboard` : chaque rôle a la sienne. */
  get dashboardRoute(): string {
    const role = this.authService.getRole();
    return role ? `/dashboard/${role.toLowerCase()}` : '/login';
  }
}
