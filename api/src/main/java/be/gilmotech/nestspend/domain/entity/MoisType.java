package be.gilmotech.nestspend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité représentant un "mois type" budgétaire pour un foyer.
 * Un mois type regroupe les revenus du foyer et l'ensemble des dépenses types
 * (fixes et variables) pour permettre des projections financières annuelles.
 *
 * <p>Plusieurs mois types peuvent coexister par foyer (ex : un par année),
 * permettant d'historiser et de comparer les budgets prévisionnels.</p>
 */
@Entity
@Table(name = "mois_type", indexes = {
        @Index(name = "idx_mois_type_household", columnList = "household_id"),
        @Index(name = "idx_mois_type_household_annee", columnList = "household_id, annee")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoisType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Foyer auquel ce mois type appartient */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mois_type_household"))
    private Household household;

    /** Nom descriptif du mois type (ex : "Mois standard 2026") */
    @Column(name = "nom", nullable = false)
    private String nom;

    /** Année de référence pour ce mois type (ex : 2026) */
    @Column(name = "annee", nullable = false)
    private Integer annee;

    /** Revenus principaux du foyer en centimes (salaires, etc.) */
    @Column(name = "revenus", nullable = false)
    @Builder.Default
    private Long revenus = 0L;

    /** Autres revenus du foyer en centimes (primes, revenus locatifs, etc.) */
    @Column(name = "autres_revenus", nullable = false)
    @Builder.Default
    private Long autresRevenus = 0L;

    /**
     * Liste des dépenses types rattachées à ce mois type.
     * Chargement lazy pour éviter les requêtes N+1 sur les listes.
     */
    @OneToMany(mappedBy = "moisType", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DepenseType> depenses = new ArrayList<>();

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
