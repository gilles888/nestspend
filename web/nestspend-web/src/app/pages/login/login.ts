import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';

import { AuthenticationService } from '../../core/api/services/authentication.service';
import { AuthService, isHttpError } from '../../core/auth/auth.service';
import { LoginRequest } from '../../core/api/models/login-request';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    InputTextModule,
    PasswordModule,
    ButtonModule,
    MessageModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  email = '';
  password = '';
  loading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(
    private authenticationService: AuthenticationService,
    private authService: AuthService,
    private router: Router
  ) {}

  async onSubmit(): Promise<void> {
    if (!this.email || !this.password) {
      this.errorMessage.set('Please fill in all fields.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const loginRequest: LoginRequest = {
        email: this.email,
        password: this.password,
      };

      const strictResponse = await this.authenticationService.login$Response({ body: loginRequest });
      // The response body might be a Blob due to OpenAPI spec using */* content type
      let response = strictResponse.body;
      if (response instanceof Blob) {
        const text = await response.text();
        response = JSON.parse(text);
      }
      if (!this.authService.handleAuthResponse(response)) {
        this.errorMessage.set('Authentication failed. Please try again.');
      }
    } catch (error: unknown) {
      if (isHttpError(error) && error.status === 401) {
        this.errorMessage.set('Invalid email or password.');
      } else {
        this.errorMessage.set('An error occurred. Please try again.');
      }
    } finally {
      this.loading.set(false);
    }
  }
}
