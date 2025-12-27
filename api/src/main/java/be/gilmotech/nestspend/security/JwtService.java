package be.gilmotech.nestspend.security;

import be.gilmotech.nestspend.config.JwtProperties;
import be.gilmotech.nestspend.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_HOUSEHOLD_ID = "householdId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = jwtProperties.expirationMs();
    }

    public String generateToken(UUID userId, UUID householdId, UserRole role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_HOUSEHOLD_ID, householdId.toString())
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = getClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractHouseholdId(String token) {
        Claims claims = getClaims(token);
        return UUID.fromString(claims.get(CLAIM_HOUSEHOLD_ID, String.class));
    }

    public UserRole extractRole(String token) {
        Claims claims = getClaims(token);
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public UserPrincipal extractUserPrincipal(String token) {
        Claims claims = getClaims(token);
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_HOUSEHOLD_ID, String.class)),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))
        );
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
