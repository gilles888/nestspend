package be.gilmotech.nestspend.security;

import be.gilmotech.nestspend.domain.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service to retrieve the current authenticated user's information from the security context.
 */
@Service
public class CurrentUserService {

    /**
     * Gets the current authenticated user's principal.
     *
     * @return the UserPrincipal of the current user
     * @throws IllegalStateException if no user is authenticated
     */
    public UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new IllegalStateException("Unexpected principal type: " + principal.getClass().getName());
        }
        return (UserPrincipal) principal;
    }

    /**
     * Gets the current authenticated user's ID.
     *
     * @return the UUID of the current user
     */
    public UUID getCurrentUserId() {
        return getCurrentUser().userId();
    }

    /**
     * Gets the current authenticated user's household ID.
     *
     * @return the UUID of the current user's household
     */
    public UUID getCurrentHouseholdId() {
        return getCurrentUser().householdId();
    }

    /**
     * Gets the current authenticated user's role.
     *
     * @return the UserRole of the current user
     */
    public UserRole getCurrentUserRole() {
        return getCurrentUser().role();
    }
}
