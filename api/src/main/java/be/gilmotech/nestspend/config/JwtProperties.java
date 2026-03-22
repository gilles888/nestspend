package be.gilmotech.nestspend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriétés de configuration JWT lues depuis application.yaml.
 * La clé secrète doit avoir au moins 32 caractères pour garantir la sécurité HMAC-SHA256.
 * En production, utiliser la variable d'environnement JWT_SECRET.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        /**
         * Clé secrète HMAC utilisée pour signer les tokens JWT.
         * Minimum 32 caractères requis pour HMAC-SHA256.
         * Ne jamais utiliser la valeur par défaut en production.
         */
        @NotBlank(message = "La clé secrète JWT est obligatoire (propriété jwt.secret)")
        @Size(min = 32, message = "La clé secrète JWT doit avoir au moins 32 caractères")
        String secret,

        /**
         * Durée de validité des tokens JWT en millisecondes.
         * Par défaut : 86400000 ms (24 heures).
         */
        @Min(value = 60000, message = "La durée d'expiration JWT doit être d'au moins 60 000 ms (1 minute)")
        long expirationMs
) {
}
