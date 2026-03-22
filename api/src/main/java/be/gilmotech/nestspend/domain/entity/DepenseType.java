package be.gilmotech.nestspend.domain.entity;

import be.gilmotech.nestspend.domain.enums.CategorieDepense;
import be.gilmotech.nestspend.domain.enums.FrequenceDepense;
import be.gilmotech.nestspend.domain.enums.TypeDepense;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité représentant une dépense type rattachée à un mois type.
 * Une dépense type décrit une charge récurrente du foyer avec sa fréquence
 * et sa catégorie, utilisée pour les calculs de projection budgétaire.
 *
 * <p>Le montant mensuel équivalent est calculé selon la fréquence :
 * <ul>
 *   <li>MENSUELLE : montant intact</li>
 *   <li>TRIMESTRIELLE : montant / 3</li>
 *   <li>ANNUELLE : montant / 12</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "depenses_type", indexes = {
        @Index(name = "idx_depense_type_mois_type", columnList = "mois_type_id"),
        @Index(name = "idx_depense_type_household", columnList = "household_id"),
        @Index(name = "idx_depense_type_actif", columnList = "mois_type_id, actif")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepenseType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Foyer auquel cette dépense type appartient (dénormalisé pour les requêtes de sécurité) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_depense_type_household"))
    private Household household;

    /** Mois type auquel cette dépense est rattachée */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mois_type_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_depense_type_mois_type"))
    private MoisType moisType;

    /** Libellé de la dépense (ex : "Loyer", "Abonnement Netflix") */
    @Column(name = "nom", nullable = false)
    private String nom;

    /** Montant de la dépense en centimes pour la période de fréquence */
    @Column(name = "montant", nullable = false)
    private Long montant;

    /** Catégorie budgétaire de la dépense */
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie", nullable = false, length = 50)
    private CategorieDepense categorie;

    /** Nature de la dépense : fixe ou variable */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_depense", nullable = false, length = 20)
    private TypeDepense typeDepense;

    /** Fréquence de récurrence de la dépense */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequence", nullable = false, length = 20)
    private FrequenceDepense frequence;

    /**
     * Indique si cette dépense est active.
     * Une dépense inactive est exclue des calculs de projection.
     */
    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

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
