package be.gilmotech.nestspend.domain.entity;

import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import be.gilmotech.nestspend.domain.enums.RuleSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_rules", indexes = {
        @Index(name = "idx_rule_household", columnList = "household_id"),
        @Index(name = "idx_rule_enabled", columnList = "household_id, enabled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_rule_household"))
    private Household household;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 20)
    private RuleField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType;

    @Column(name = "pattern", nullable = false, length = 255)
    private String pattern;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_rule_category"))
    private Category category;

    @Builder.Default
    @Column(name = "confidence", nullable = false)
    private Integer confidence = 80;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private RuleSource source = RuleSource.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
