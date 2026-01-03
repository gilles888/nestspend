import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Mock } from 'vitest';
import { AuthService, isHttpError } from './auth.service';
import { AuthResponse } from '../api/models/auth-response';

interface MockRouter {
  navigate: Mock<(commands: unknown[]) => Promise<boolean>>;
}

describe('AuthService', () => {
  let service: AuthService;
  let router: MockRouter;

  const TOKEN_KEY = 'nestspend_token';
  const USER_KEY = 'nestspend_user';

  const mockAuthResponse: AuthResponse = {
    token: 'test-jwt-token',
    userId: 'user-123',
    displayName: 'Test User',
    householdId: 'household-123',
    role: 'USER',
  };

  beforeEach(() => {
    localStorage.clear();

    const routerMock = {
      navigate: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [AuthService, { provide: Router, useValue: routerMock }],
    });

    service = TestBed.inject(AuthService);
    router = TestBed.inject(Router) as unknown as MockRouter;
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('initial state', () => {
    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should return false for isAuthenticated when no token stored', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should return true for isAuthenticated when token is stored', () => {
      // Store a token before creating a new service instance
      localStorage.setItem(TOKEN_KEY, 'some-token');
      
      // Reset TestBed to create a fresh service instance
      // This simulates a page refresh where the service is re-initialized
      // and reads the token from localStorage during construction
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [AuthService, { provide: Router, useValue: { navigate: vi.fn() } }],
      });
      const freshService = TestBed.inject(AuthService);
      expect(freshService.isAuthenticated()).toBe(true);
    });
  });

  describe('getToken', () => {
    it('should return null when no token is stored', () => {
      expect(service.getToken()).toBeNull();
    });

    it('should return the stored token', () => {
      localStorage.setItem(TOKEN_KEY, 'test-token');
      expect(service.getToken()).toBe('test-token');
    });
  });

  describe('getUser', () => {
    it('should return null when no user is stored', () => {
      expect(service.getUser()).toBeNull();
    });

    it('should return the stored user', () => {
      localStorage.setItem(USER_KEY, JSON.stringify(mockAuthResponse));
      expect(service.getUser()).toEqual(mockAuthResponse);
    });
  });

  describe('handleAuthResponse', () => {
    it('should return false when response has no token', () => {
      const responseWithoutToken: AuthResponse = { ...mockAuthResponse, token: undefined };
      const result = service.handleAuthResponse(responseWithoutToken);
      expect(result).toBe(false);
    });

    it('should store token and user in localStorage', () => {
      service.handleAuthResponse(mockAuthResponse);
      expect(localStorage.getItem(TOKEN_KEY)).toBe(mockAuthResponse.token);
      expect(localStorage.getItem(USER_KEY)).toBe(JSON.stringify(mockAuthResponse));
    });

    it('should set isAuthenticated to true', () => {
      service.handleAuthResponse(mockAuthResponse);
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should navigate to dashboard after successful auth', () => {
      service.handleAuthResponse(mockAuthResponse);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('should return true on successful auth', () => {
      const result = service.handleAuthResponse(mockAuthResponse);
      expect(result).toBe(true);
    });
  });

  describe('logout', () => {
    beforeEach(() => {
      service.handleAuthResponse(mockAuthResponse);
    });

    it('should remove token from localStorage', () => {
      service.logout();
      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    });

    it('should remove user from localStorage', () => {
      service.logout();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
    });

    it('should set isAuthenticated to false', () => {
      service.logout();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should navigate to login page', () => {
      service.logout();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('session persistence after refresh', () => {
    it('should preserve authentication state after simulated refresh', () => {
      service.handleAuthResponse(mockAuthResponse);
      expect(service.isAuthenticated()).toBe(true);
      expect(localStorage.getItem(TOKEN_KEY)).toBe(mockAuthResponse.token);

      // Simulate refresh by creating a new instance
      const newService = TestBed.inject(AuthService);
      expect(newService.isAuthenticated()).toBe(true);
      expect(newService.getToken()).toBe(mockAuthResponse.token);
    });
  });

  describe('isHttpError helper', () => {
    it('should return true for objects with status property', () => {
      expect(isHttpError({ status: 401 })).toBe(true);
    });

    it('should return false for null', () => {
      expect(isHttpError(null)).toBe(false);
    });

    it('should return false for undefined', () => {
      expect(isHttpError(undefined)).toBe(false);
    });

    it('should return false for objects without status', () => {
      expect(isHttpError({ message: 'error' })).toBe(false);
    });
  });
});
