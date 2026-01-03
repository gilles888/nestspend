import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Mock } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

interface MockAuthService {
  getToken: Mock<() => string | null>;
  logout: Mock<() => void>;
}

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: MockAuthService;

  beforeEach(() => {
    localStorage.clear();

    const authServiceMock = {
      getToken: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceMock },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as unknown as MockAuthService;
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('Authorization header', () => {
    it('should add Authorization header with Bearer token for API requests', () => {
      const testToken = 'test-jwt-token';
      authService.getToken.mockReturnValue(testToken);

      httpClient.get('/api/transactions').subscribe();

      const req = httpMock.expectOne('/api/transactions');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${testToken}`);
      req.flush([]);
    });

    it('should not add Authorization header when no token exists', () => {
      authService.getToken.mockReturnValue(null);

      httpClient.get('/api/transactions').subscribe();

      const req = httpMock.expectOne('/api/transactions');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush([]);
    });

    it('should skip adding token for auth login endpoint', () => {
      authService.getToken.mockReturnValue('some-token');

      httpClient.post('/api/auth/login', {}).subscribe();

      const req = httpMock.expectOne('/api/auth/login');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should skip adding token for auth register endpoint', () => {
      authService.getToken.mockReturnValue('some-token');

      httpClient.post('/api/auth/register', {}).subscribe();

      const req = httpMock.expectOne('/api/auth/register');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('401 Unauthorized handling', () => {
    it('should call logout when receiving 401 error', () => {
      authService.getToken.mockReturnValue('expired-token');

      httpClient.get('/api/transactions').subscribe({
        error: () => {
          // Error expected
        },
      });

      const req = httpMock.expectOne('/api/transactions');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      expect(authService.logout).toHaveBeenCalled();
    });

    it('should not call logout for other error codes', () => {
      authService.getToken.mockReturnValue('valid-token');

      httpClient.get('/api/transactions').subscribe({
        error: () => {
          // Error expected
        },
      });

      const req = httpMock.expectOne('/api/transactions');
      req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });

      expect(authService.logout).not.toHaveBeenCalled();
    });

    it('should not call logout for 403 Forbidden', () => {
      authService.getToken.mockReturnValue('valid-token');

      httpClient.get('/api/admin/users').subscribe({
        error: () => {
          // Error expected
        },
      });

      const req = httpMock.expectOne('/api/admin/users');
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

      expect(authService.logout).not.toHaveBeenCalled();
    });
  });
});
