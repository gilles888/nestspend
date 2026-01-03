import { Component, EventEmitter, Output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../../core/services/language.service';
import { ThemeService } from '../../core/services/theme.service';
import { AuthService } from '../../core/auth/auth.service';
import { AuthResponse } from '../../core/api/models/auth-response';

interface LanguageOption {
  code: string;
  label: string;
}

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './topbar.html',
  styleUrl: './topbar.scss',
})
export class TopbarComponent {
  @Output() menuToggle = new EventEmitter<void>();

  languages: LanguageOption[] = [
    { code: 'fr', label: 'FR' },
    { code: 'en', label: 'EN' }
  ];

  isUserMenuOpen = signal(false);
  private cachedUser = signal<AuthResponse | null>(null);

  currentUser = computed(() => this.cachedUser());

  constructor(
    public languageService: LanguageService,
    public themeService: ThemeService,
    private authService: AuthService
  ) {
    this.cachedUser.set(this.authService.getUser());
  }

  onMenuToggle(): void {
    this.menuToggle.emit();
  }

  switchLanguage(lang: string): void {
    this.languageService.setLang(lang);
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  isCurrentLang(lang: string): boolean {
    return this.languageService.currentLang() === lang;
  }

  toggleUserMenu(): void {
    this.isUserMenuOpen.update(value => !value);
  }

  closeUserMenu(): void {
    this.isUserMenuOpen.set(false);
  }

  onLogout(): void {
    this.closeUserMenu();
    this.authService.logout();
  }
}
