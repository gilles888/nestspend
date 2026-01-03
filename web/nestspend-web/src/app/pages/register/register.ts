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
import { RegisterRequest } from '../../core/api/models/register-request';

@Component({
  selector: 'app-register',
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
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class RegisterComponent {
  email = '';
  password = '';
  displayName = '';
  householdName = '';
  loading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(
    private authenticationService: AuthenticationService,
    private authService: AuthService,
    private router: Router
  ) {}

  async onSubmit(): Promise<void> {
    if (!this.email || !this.password || !this.displayName || !this.householdName) {
      this.errorMessage.set('Please fill in all fields.');
      return;
    }

    if (this.password.length < 8) {
      this.errorMessage.set('Password must be at least 8 characters.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const registerRequest: RegisterRequest = {
        email: this.email,
        password: this.password,
        displayName: this.displayName,
        householdName: this.householdName,
      };

      const strictResponse = await this.authenticationService.register$Response({ body: registerRequest });
      // The response body might be a Blob due to OpenAPI spec using */* content type
      let response = strictResponse.body;
      if (response instanceof Blob) {
        const text = await response.text();
        response = JSON.parse(text);
      }
      if (!this.authService.handleAuthResponse(response)) {
        this.errorMessage.set('Registration failed. Please try again.');
      }
    } catch (error: unknown) {
      if (isHttpError(error) && error.status === 409) {
        this.errorMessage.set('This email is already registered.');
      } else if (isHttpError(error) && error.status === 400) {
        this.errorMessage.set('Please check your input and try again.');
      } else {
        this.errorMessage.set('An error occurred. Please try again.');
      }
    } finally {
      this.loading.set(false);
    }
  }
}
