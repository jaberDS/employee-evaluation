import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { animate, style, transition, trigger, query, stagger } from '@angular/animations';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ActiviteService } from '../../services/activite.service';
import { ActivityView, toActivityView } from '../../shared/activity-view';

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
    ]),
    trigger('dropdownPop', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-10px) scale(0.96)' }),
        animate('260ms cubic-bezier(0.34, 1.56, 0.64, 1)',
          style({ opacity: 1, transform: 'translateY(0) scale(1)' }))
      ]),
      transition(':leave', [
        animate('160ms ease-in', style({ opacity: 0, transform: 'translateY(-6px) scale(0.98)' }))
      ])
    ]),
    trigger('listStagger', [
      transition(':enter', [
        query('.notif-item', [
          style({ opacity: 0, transform: 'translateX(16px)' }),
          stagger(60, [
            animate('340ms cubic-bezier(0.23, 1, 0.32, 1)',
              style({ opacity: 1, transform: 'translateX(0)' }))
          ])
        ], { optional: true })
      ])
    ])
  ]
})
export class HeaderComponent implements OnInit, OnDestroy {
  currentTime = '';
  currentDate = '';
  isDarkMode = false;
  notificationCount = 0;
  notifications: ActivityView[] = [];
  showNotifications = false;
  loadingNotifications = false;
  clearingNotifications = false;
  searchQuery = '';
  showLogoutConfirm = false;
  private lastActivityIds: number[] = [];
  private readonly SEEN_KEY = 'atb_last_seen_activity_id';
  private clockInterval?: ReturnType<typeof setInterval>;
  private pollInterval?: ReturnType<typeof setInterval>;
  private themeSub?: Subscription;
  private activitiesSub?: Subscription;

  constructor(
    public authService: AuthService,
    private themeService: ThemeService,
    private router: Router,
    private activiteService: ActiviteService
  ) {}

  ngOnInit(): void {
    this.themeSub = this.themeService.isDarkMode$.subscribe(dark => {
      this.isDarkMode = dark;
    });
    this.updateClock();
    this.clockInterval = setInterval(() => this.updateClock(), 1000);
    // Reste synchronisé avec le flux partagé (dashboard, effacement, etc.)
    this.activitiesSub = this.activiteService.activities$.subscribe(activities => {
      this.lastActivityIds = activities.map(a => a.id);
      this.notifications = activities.map(toActivityView);
      this.recomputeUnread();
    });
    this.loadNotifications();
    // Rafraîchit le compteur toutes les 30s pour détecter les nouvelles activités
    this.pollInterval = setInterval(() => this.loadNotifications(), 30000);
  }

  ngOnDestroy(): void {
    if (this.clockInterval) clearInterval(this.clockInterval);
    if (this.pollInterval) clearInterval(this.pollInterval);
    this.themeSub?.unsubscribe();
    this.activitiesSub?.unsubscribe();
  }

  get isAdmin(): boolean {
    return this.authService.getRole() === 'ADMIN';
  }

  private get lastSeenId(): number {
    return Number(localStorage.getItem(this.SEEN_KEY) || 0);
  }

  private loadNotifications(): void {
    this.loadingNotifications = true;
    this.activiteService.refresh(10).subscribe({
      next: () => {
        this.loadingNotifications = false;
      },
      error: () => {
        this.loadingNotifications = false;
      }
    });
  }

  private recomputeUnread(): void {
    const seen = this.lastSeenId;
    this.notificationCount = this.lastActivityIds.filter(id => id > seen).length;
  }

  private markAllSeen(): void {
    if (this.lastActivityIds.length > 0) {
      const newest = Math.max(...this.lastActivityIds);
      localStorage.setItem(this.SEEN_KEY, String(newest));
    }
    this.notificationCount = 0;
  }

  toggleNotifications(event: Event): void {
    event.stopPropagation();
    this.showNotifications = !this.showNotifications;
    if (this.showNotifications) {
      this.loadNotifications();
      this.markAllSeen();
    }
  }

  closeNotifications(): void {
    this.showNotifications = false;
  }

  clearAllNotifications(event: Event): void {
    event.stopPropagation();
    this.clearingNotifications = true;
    this.activiteService.deleteAll().subscribe({
      next: () => {
        // Le flux partagé se vide déjà (activities$), ce qui met à jour la liste et le dashboard.
        this.notificationCount = 0;
        this.clearingNotifications = false;
      },
      error: () => {
        this.clearingNotifications = false;
      }
    });
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
    if (this.showNotifications) {
      this.showNotifications = false;
    }
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.showNotifications) {
      this.showNotifications = false;
    }
  }
}
