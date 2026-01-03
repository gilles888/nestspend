import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Mock } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

interface MockAuthService {
  isAuthenticated: Mock<() => boolean>;
}

interface MockRouter {
  navigate: Mock<(commands: unknown[]) => Promise<boolean>>;
}

describe('authGuard', () => {
  let authService: MockAuthService;
  let router: MockRouter;

  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = { url: '/dashboard' } as RouterStateSnapshot;

  beforeEach(() => {
    const authServiceMock = {
      isAuthenticated: vi.fn(),
    };

    const routerMock = {
      navigate: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });

    authService = TestBed.inject(AuthService) as unknown as MockAuthService;
    router = TestBed.inject(Router) as unknown as MockRouter;
  });

  it('should allow navigation when user is authenticated', () => {
    authService.isAuthenticated.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should redirect to login when user is not authenticated', () => {
    authService.isAuthenticated.mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should protect private routes', () => {
    authService.isAuthenticated.mockReturnValue(false);

    const privateRoutes = [
      { url: '/dashboard' },
      { url: '/transactions' },
      { url: '/categories' },
      { url: '/accounts' },
    ];

    privateRoutes.forEach((state) => {
      const result = TestBed.runInInjectionContext(() =>
        authGuard(mockRoute, state as RouterStateSnapshot)
      );
      expect(result).toBe(false);
    });
  });
});
