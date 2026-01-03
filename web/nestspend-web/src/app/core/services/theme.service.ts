import { Injectable, signal, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const THEME_KEY = 'nestspend_theme';
const DARK_CLASS = 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private platformId = inject(PLATFORM_ID);
  private isDarkSignal = signal<boolean>(this.getStoredTheme());

  isDark = this.isDarkSignal.asReadonly();

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.applyTheme(this.isDarkSignal());
    }
  }

  private getStoredTheme(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }
    const stored = localStorage.getItem(THEME_KEY);
    if (stored !== null) {
      return stored === 'dark';
    }
    // Check system preference if no stored preference
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  private applyTheme(isDark: boolean): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const html = document.documentElement;
    if (isDark) {
      html.classList.add(DARK_CLASS);
    } else {
      html.classList.remove(DARK_CLASS);
    }
  }

  toggle(): void {
    const newValue = !this.isDarkSignal();
    this.isDarkSignal.set(newValue);
    localStorage.setItem(THEME_KEY, newValue ? 'dark' : 'light');
    this.applyTheme(newValue);
  }

  setDark(isDark: boolean): void {
    this.isDarkSignal.set(isDark);
    localStorage.setItem(THEME_KEY, isDark ? 'dark' : 'light');
    this.applyTheme(isDark);
  }
}
