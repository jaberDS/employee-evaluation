import { Component, OnInit, OnDestroy } from '@angular/core';
import { of, Subscription } from 'rxjs';
import { catchError, distinctUntilChanged, filter } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';

interface NoteBucket {
  label: string;
  min: number;
  max: number;
  count: number;
  color: string;
}

@Component({
  selector: 'app-n2-dashboard',
  templateUrl: './n2-dashboard.component.html',
  styleUrls: ['./n2-dashboard.component.css']
})
export class N2DashboardComponent implements OnInit, OnDestroy {

  currentUser: any = null;
  loading = true;
  private sub = new Subscription();
  private autoRefreshTimer: any;

  // All fiches that carry a note N+1 (across every status)
  ratedFiches: FicheEvaluation[] = [];
  pendingCount = 0;   // EN_ATTENTE_N2 (for the hero line)

  // Animated display counters
  display = { avgNote: 0, evaluated: 0, best: 0, distributionReady: false };

  buckets: NoteBucket[] = [];

  today = new Date();

  constructor(
    private authService: AuthService,
    private ficheService: FicheService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      this.authService.currentUser$.pipe(
        filter(user => !!user?.id && user.id !== 0),
        distinctUntilChanged((a, b) => a!.id === b!.id)
      ).subscribe(user => {
        this.currentUser = user;
        this.loadDashboard();
      })
    );
    this.autoRefreshTimer = setInterval(() => this.loadDashboard(true), 60_000);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    if (this.autoRefreshTimer) clearInterval(this.autoRefreshTimer);
  }

  // ─── Data ────────────────────────────────────────────────────────────────

  loadDashboard(silent = false): void {
    if (!this.currentUser?.id) return;
    if (!silent) this.loading = true;

    // Only the fiches of employees whose N+2 is the current user
    this.ficheService.getByN2(this.currentUser.id).pipe(
      catchError(() => of([] as FicheEvaluation[]))
    ).subscribe({
      next: (fiches) => {
        this.pendingCount = fiches.filter(f => f.statut === 'EN_ATTENTE_N2').length;
        // Every fiche that has a note N+1 counts toward the employee-note statistics
        this.ratedFiches = fiches.filter(f => f.noteN1 !== null);
        this.computeBuckets();
        this.loading = false;
        setTimeout(() => this.animateCounters(), 100);
      },
      error: () => {
        this.toastr.error('Erreur de chargement du tableau de bord', 'Erreur');
        this.loading = false;
      }
    });
  }

  private computeBuckets(): void {
    const defs: Omit<NoteBucket, 'count'>[] = [
      { label: 'Insuffisant', min: 0,  max: 4.99,  color: '#EF4444' },
      { label: 'Moyen',       min: 5,  max: 6.99,  color: '#F59E0B' },
      { label: 'Bien',        min: 7,  max: 8.49,  color: '#0284C7' },
      { label: 'Excellent',   min: 8.5, max: 10,   color: '#16A34A' }
    ];
    this.buckets = defs.map(d => ({
      ...d,
      count: this.notes.filter(n => n >= d.min && n <= d.max).length
    }));
  }

  // ─── Computed ────────────────────────────────────────────────────────────

  get greeting(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Bonjour' : h < 18 ? 'Bon après-midi' : 'Bonsoir';
  }

  get notes(): number[] {
    return this.ratedFiches.map(f => f.noteN1 as number);
  }

  get evaluatedCount(): number { return this.ratedFiches.length; }

  get avgNote(): number {
    if (!this.notes.length) return 0;
    const avg = this.notes.reduce((s, n) => s + n, 0) / this.notes.length;
    return Math.round(avg * 10) / 10;
  }

  get bestNote(): number {
    return this.notes.length ? Math.max(...this.notes) : 0;
  }

  get worstNote(): number {
    return this.notes.length ? Math.min(...this.notes) : 0;
  }

  get maxBucketCount(): number {
    return this.buckets.reduce((m, b) => Math.max(m, b.count), 0);
  }

  get avgProgressPercent(): number {
    return Math.round((this.avgNote / 10) * 100);
  }

  bucketPercent(b: NoteBucket): number {
    if (!this.evaluatedCount) return 0;
    return Math.round((b.count / this.evaluatedCount) * 100);
  }

  bucketBarHeight(b: NoteBucket): number {
    const max = this.maxBucketCount;
    if (!max) return 0;
    return Math.round((b.count / max) * 100);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  noteClass(note: number | null): string {
    if (note === null) return 'note-neutral';
    if (note >= 7) return 'note-good';
    if (note >= 5) return 'note-mid';
    return 'note-low';
  }

  /** Top-rated employees (highest notes first). */
  get topRated(): FicheEvaluation[] {
    return [...this.ratedFiches]
      .sort((a, b) => (b.noteN1 as number) - (a.noteN1 as number))
      .slice(0, 5);
  }

  trackById(_: number, item: any): number { return item.id; }

  // ─── Animation ───────────────────────────────────────────────────────────

  private animateCounters(): void {
    const targets = { avgNote: this.avgNote, evaluated: this.evaluatedCount, best: this.bestNote };
    const duration = 1200;
    const start = performance.now();
    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      this.display.avgNote   = Math.round(targets.avgNote * eased * 10) / 10;
      this.display.evaluated = Math.round(targets.evaluated * eased);
      this.display.best      = Math.round(targets.best * eased * 10) / 10;
      if (p < 1) requestAnimationFrame(step);
      else this.display.distributionReady = true;
    };
    requestAnimationFrame(step);
  }
}
