import { Injectable, signal, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';

const LANG_KEY = 'nestspend_lang';
const DEFAULT_LANG = 'fr';
const SUPPORTED_LANGS = ['fr', 'en'];

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private platformId = inject(PLATFORM_ID);
  private currentLangSignal = signal<string>(this.getStoredLang());

  currentLang = this.currentLangSignal.asReadonly();

  constructor(private translate: TranslateService) {
    this.initializeLanguage();
  }

  private initializeLanguage(): void {
    this.translate.setDefaultLang(DEFAULT_LANG);
    const lang = this.getStoredLang();
    this.translate.use(lang);
    this.currentLangSignal.set(lang);
  }

  private getStoredLang(): string {
    if (!isPlatformBrowser(this.platformId)) {
      return DEFAULT_LANG;
    }
    const stored = localStorage.getItem(LANG_KEY);
    if (stored && SUPPORTED_LANGS.includes(stored)) {
      return stored;
    }
    return DEFAULT_LANG;
  }

  getCurrentLang(): string {
    return this.currentLangSignal();
  }

  setLang(lang: string): void {
    if (!SUPPORTED_LANGS.includes(lang)) {
      return;
    }
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(LANG_KEY, lang);
    }
    this.translate.use(lang);
    this.currentLangSignal.set(lang);
  }

  getSupportedLanguages(): string[] {
    return [...SUPPORTED_LANGS];
  }
}
