import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  labelKey: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslateModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class SidebarComponent {
  @Output() navItemClicked = new EventEmitter<void>();

  navItems: NavItem[] = [
    { labelKey: 'nav.dashboard', icon: 'pi pi-home', route: '/dashboard' },
    { labelKey: 'nav.transactions', icon: 'pi pi-list', route: '/transactions' },
    { labelKey: 'nav.categories', icon: 'pi pi-tags', route: '/categories' },
    { labelKey: 'nav.classificationRules', icon: 'pi pi-cog', route: '/classification-rules' },
    { labelKey: 'nav.accounts', icon: 'pi pi-wallet', route: '/accounts' },
  ];

  constructor(private authService: AuthService) {}

  get currentUser() {
    return this.authService.getUser();
  }

  onNavItemClick(): void {
    this.navItemClicked.emit();
  }

  onLogout(): void {
    this.authService.logout();
  }
}
