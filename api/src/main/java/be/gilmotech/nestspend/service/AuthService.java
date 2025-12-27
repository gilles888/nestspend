package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.UserRole;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.UserRepository;
import be.gilmotech.nestspend.dto.auth.AuthResponse;
import be.gilmotech.nestspend.dto.auth.LoginRequest;
import be.gilmotech.nestspend.dto.auth.RegisterRequest;
import be.gilmotech.nestspend.exception.AuthenticationException;
import be.gilmotech.nestspend.exception.ResourceConflictException;
import be.gilmotech.nestspend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, HouseholdRepository householdRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.householdRepository = householdRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ResourceConflictException("Email already registered");
        }

        Household household = Household.builder()
                .name(request.householdName())
                .build();
        household = householdRepository.save(household);

        User user = User.builder()
                .household(household)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(UserRole.ADMIN)
                .build();
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId());

        return new AuthResponse(
                token,
                user.getId(),
                household.getId(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId());

        return new AuthResponse(
                token,
                user.getId(),
                user.getHousehold().getId(),
                user.getDisplayName(),
                user.getRole()
        );
    }
}
