import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css'],
  animations: [
    trigger('backdropFade', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('220ms ease-out', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0 }))
      ])
    ]),
    trigger('modalPop', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(16px) scale(0.94)' }),
        animate('320ms cubic-bezier(0.34, 1.56, 0.64, 1)',
          style({ opacity: 1, transform: 'translateY(0) scale(1)' }))
      ]),
      transition(':leave', [
        style({ opacity: 1, transform: 'translateY(0) scale(1)' }),
        animate('180ms ease-in', style({ opacity: 0, transform: 'translateY(8px) scale(0.97)' }))
      ])
    ])
  ]
})
export class HeaderComponent implements OnInit, OnDestroy {
  currentTime = '';
  currentDate = '';
  isDarkMode = false;
  notificationCount = 3;
  searchQuery = '';
  showLogoutConfirm = false;
  private clockInterval?: ReturnType<typeof setInterval>;
  private themeSub?: Subscription;

  constructor(
    public authService: AuthService,
    private themeService: ThemeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.themeSub = this.themeService.isDarkMode$.subscribe(dark => {
      this.isDarkMode = dark;
    });
    this.updateClock();
    this.clockInterval = setInterval(() => this.updateClock(), 1000);
  }

  ngOnDestroy(): void {
    if (this.clockInterval) clearInterval(this.clockInterval);
    this.themeSub?.unsubscribe();
  }

  get initials(): string {
    const user = this.authService.getUser();
    const prenom = user?.prenom?.charAt(0) || '';
    const nom = user?.nom?.charAt(0) || '';
    return (prenom + nom).toUpperCase() || 'AD';
  }

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Bonjour';
    if (hour < 18) return 'Bon après-midi';
    return 'Bonsoir';
  }

  updateClock(): void {
    const now = new Date();
    this.currentTime = now.toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
    this.currentDate = now.toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  toggleDarkMode(): void {
    this.themeService.toggle();
  }

  logout(): void {
    this.showLogoutConfirm = true;
  }

  confirmLogout(): void {
    this.showLogoutConfirm = false;
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  cancelLogout(): void {
    this.showLogoutConfirm = false;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.showLogoutConfirm) {
      this.showLogoutConfirm = false;
    }
  }
}
