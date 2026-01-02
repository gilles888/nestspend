import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthResponse } from '../api/models/auth-response';

const TOKEN_KEY = 'nestspend_token';
const USER_KEY = 'nestspend_user';

export function isHttpError(error: unknown): error is { status: number } {
  return typeof error === 'object' && error !== null && 'status' in error;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private isAuthenticatedSignal = signal(this.hasStoredToken());

  isAuthenticated = this.isAuthenticatedSignal.asReadonly();

  constructor(private router: Router) {}

  private hasStoredToken(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getUser(): AuthResponse | null {
    const user = localStorage.getItem(USER_KEY);
    return user ? JSON.parse(user) : null;
  }

  handleAuthResponse(response: AuthResponse): boolean {
    if (!response.token) {
      return false;
    }
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response));
    this.isAuthenticatedSignal.set(true);
    this.router.navigate(['/dashboard']);
    return true;
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.isAuthenticatedSignal.set(false);
    this.router.navigate(['/login']);
  }
}
