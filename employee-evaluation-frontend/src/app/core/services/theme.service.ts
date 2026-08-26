import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'atb-theme';
  private darkSubject = new BehaviorSubject<boolean>(
    typeof localStorage !== 'undefined' && localStorage.getItem(this.storageKey) === 'dark'
  );

  readonly isDark$ = this.darkSubject.asObservable();
  readonly isDarkMode$ = this.isDark$;

  get isDark(): boolean {
    return this.darkSubject.value;
  }

  init(): void {
    this.apply(this.isDark);
  }

  toggle(): void {
    this.setDark(!this.isDark);
  }

  setDark(dark: boolean): void {
    localStorage.setItem(this.storageKey, dark ? 'dark' : 'light');
    this.darkSubject.next(dark);
    this.apply(dark);
  }

  private apply(dark: boolean): void {
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  }
}
