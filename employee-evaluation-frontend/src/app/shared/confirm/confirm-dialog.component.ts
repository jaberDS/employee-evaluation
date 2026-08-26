import { Component, HostListener } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { ToastrService } from 'ngx-toastr';
import { Observable } from 'rxjs';
import { ConfirmService, ConfirmState } from './confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.component.html',
  styleUrls: ['./confirm-dialog.component.css'],
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
        style({ opacity: 0, transform: 'translateY(18px) scale(0.94)' }),
        animate('340ms cubic-bezier(0.34, 1.56, 0.64, 1)',
          style({ opacity: 1, transform: 'translateY(0) scale(1)' }))
      ]),
      transition(':leave', [
        style({ opacity: 1, transform: 'translateY(0) scale(1)' }),
        animate('180ms ease-in', style({ opacity: 0, transform: 'translateY(10px) scale(0.97)' }))
      ])
    ])
  ]
})
export class ConfirmDialogComponent {
  loading = false;

  constructor(
    public confirmService: ConfirmService,
    private toastr: ToastrService
  ) {}

  get state(): ConfirmState | null {
    return this.confirmService.getCurrent();
  }

  onConfirm(): void {
    const s = this.state;
    if (!s || this.loading) return;

    if (s.onConfirm) {
      this.loading = true;
      const result = s.onConfirm();
      if (result && typeof (result as any).subscribe === 'function') {
        (result as Observable<unknown>).subscribe({
          next: () => this.handleSuccess(s),
          error: () => this.handleError(s)
        });
      } else if (result && typeof (result as any).then === 'function') {
        (result as Promise<unknown>).then(() => this.handleSuccess(s)).catch(() => this.handleError(s));
      } else {
        this.handleSuccess(s);
      }
    } else {
      this.confirmService.complete(true);
    }
  }

  onCancel(): void {
    if (this.loading) return;
    this.confirmService.complete(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.state && !this.loading) {
      this.confirmService.complete(false);
    }
  }

  private handleSuccess(s: ConfirmState): void {
    this.loading = false;
    if (s.successMessage) {
      this.toastr.success(s.successMessage, s.successTitle || 'Succès');
    }
    this.confirmService.complete(true);
  }

  private handleError(s: ConfirmState): void {
    this.loading = false;
    if (s.errorMessage) {
      this.toastr.error(s.errorMessage, 'Erreur');
    }
  }
}
