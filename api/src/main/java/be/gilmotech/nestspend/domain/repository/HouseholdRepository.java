package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
}
