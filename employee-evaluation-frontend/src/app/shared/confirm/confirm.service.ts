import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { IconName } from '../lucide-icon/lucide-icon.component';

export interface ConfirmOptions {
  title: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  icon?: IconName;
  confirmColor?: 'danger' | 'primary';
  loadingText?: string;
  successMessage?: string;
  successTitle?: string;
  errorMessage?: string;
  onConfirm?: () => Observable<unknown> | Promise<unknown> | void;
}

export interface ConfirmState extends ConfirmOptions {
  key: number;
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private state$ = new BehaviorSubject<ConfirmState | null>(null);
  readonly request$ = this.state$.asObservable();
  private observerRef?: (result: boolean) => void;

  confirm(options: ConfirmOptions): Observable<boolean> {
    this.state$.next({ ...options, key: Date.now() + Math.random() });
    return new Observable<boolean>((observer) => {
      this.observerRef = (result: boolean) => {
        observer.next(result);
        observer.complete();
      };
    });
  }

  getCurrent(): ConfirmState | null {
    return this.state$.value;
  }

  complete(result: boolean): void {
    const observer = this.observerRef;
    this.observerRef = undefined;
    this.state$.next(null);
    observer?.(result);
  }
}
