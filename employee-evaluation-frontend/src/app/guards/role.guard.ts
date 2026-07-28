import { Injectable } from '@angular/core';
import { CanActivate, Router, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const expectedRoles = route.data['roles'] as string[];
    const userRole = this.authService.getRole();

    if (!userRole) {
      this.router.navigate(['/login']);
      return false;
    }

    if (userRole === 'ADMIN') {
      return true;
    }

    if (expectedRoles && expectedRoles.includes(userRole)) {
      return true;
    }

    const dashboard = this.getDefaultDashboard(userRole);
    this.router.navigate([dashboard]);
    return false;
  }

  private getDefaultDashboard(role: string): string {
    const dashboards: { [key: string]: string } = {
      'ADMIN': '/dashboard/admin',
      'N1': '/dashboard/n1',
      'N2': '/dashboard/n2',
      'EMPLOYE': '/dashboard/employe'
    };
    return dashboards[role] || '/login';
  }
}
