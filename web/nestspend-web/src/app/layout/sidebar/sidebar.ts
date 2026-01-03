import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class SidebarComponent {
  @Output() navItemClicked = new EventEmitter<void>();

  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'pi pi-home', route: '/dashboard' },
    { label: 'Transactions', icon: 'pi pi-list', route: '/transactions' },
    { label: 'Categories', icon: 'pi pi-tags', route: '/categories' },
    { label: 'Accounts', icon: 'pi pi-wallet', route: '/accounts' },
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
