package be.gilmotech.nestspend.domain.repository;

import be.gilmotech.nestspend.domain.entity.FutureEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FutureEventRepository extends JpaRepository<FutureEvent, UUID> {

    /**
     * Find a future event by ID and household ID.
     *
     * @param id          the event ID
     * @param householdId the household ID
     * @return optional future event
     */
    Optional<FutureEvent> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Find all future events for a household.
     *
     * @param householdId the household ID
     * @return list of future events ordered by start date
     */
    @Query("SELECT e FROM FutureEvent e WHERE e.household.id = :householdId ORDER BY e.startDate ASC")
    List<FutureEvent> findByHouseholdId(@Param("householdId") UUID householdId);

    /**
     * Find active future events for a household within a date range.
     * An event is active if its start date is before or on the end date
     * and its end date is null or after or on the start date.
     *
     * @param householdId the household ID
     * @param startDate   the range start date
     * @param endDate     the range end date
     * @return list of active future events
     */
    @Query("SELECT e FROM FutureEvent e WHERE e.household.id = :householdId " +
           "AND e.startDate <= :endDate " +
           "AND (e.endDate IS NULL OR e.endDate >= :startDate)")
    List<FutureEvent> findActiveEventsInRange(
            @Param("householdId") UUID householdId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
