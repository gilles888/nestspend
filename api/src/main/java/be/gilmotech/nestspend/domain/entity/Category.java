package be.gilmotech.nestspend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_category_household_name", columnNames = {"household_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_household"))
    private Household household;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "icon", length = 40)
    private String icon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
