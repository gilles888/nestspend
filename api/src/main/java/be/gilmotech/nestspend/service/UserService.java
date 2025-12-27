package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.repository.UserRepository;
import be.gilmotech.nestspend.dto.CurrentUserResponse;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user-related operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public UserService(UserRepository userRepository, CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Gets the current authenticated user's information.
     *
     * @return CurrentUserResponse with user details
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUserInfo() {
        User user = userRepository.findById(currentUserService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return new CurrentUserResponse(
                user.getId(),
                user.getHousehold().getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole()
        );
    }
}
