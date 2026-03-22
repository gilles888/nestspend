package be.gilmotech.nestspend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité représentant un budget mensuel par catégorie.
 * Un budget définit un plafond de dépenses autorisées pour une catégorie donnée
 * sur un mois donné (format YYYY-MM).
 */
@Entity
@Table(name = "budgets", indexes = {
        @Index(name = "idx_budget_household_month", columnList = "household_id, month"),
        @Index(name = "idx_budget_category", columnList = "category_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_budget_household_category_month",
                columnNames = {"household_id", "category_id", "month"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Foyer auquel ce budget appartient */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_budget_household"))
    private Household household;

    /** Catégorie concernée par ce budget */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_budget_category"))
    private Category category;

    /**
     * Mois du budget au format YYYY-MM (ex : "2025-03").
     * Ce format permet un tri lexicographique correct et évite la complexité
     * d'un type date avec jour toujours fixé au 1er.
     */
    @Column(name = "month", nullable = false, length = 7)
    private String month;

    /** Montant du budget en centimes (toujours positif) */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    /** Date de création de l'enregistrement */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Date de dernière modification */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
