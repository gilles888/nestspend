package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.FutureEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Composant utilitaire partagé pour les calculs de projection.
 * Centralise la logique de comptage des occurrences d'un événement récurrent
 * sur un mois donné, évitant la duplication entre FutureEventService et ProjectionService.
 */
@Component
public class ProjectionHelper {

    /**
     * Compte le nombre d'occurrences d'un événement futur dans un mois donné,
     * en tenant compte de la périodicité et des dates de début/fin de l'événement.
     *
     * @param event l'événement récurrent
     * @param month le mois à analyser
     * @return le nombre d'occurrences (0 si l'événement n'est pas actif ce mois)
     */
    public long countOccurrencesInMonth(FutureEvent event, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        // L'événement n'a pas encore commencé ce mois-ci
        if (event.getStartDate().isAfter(monthEnd)) {
            return 0;
        }
        // L'événement est déjà terminé avant ce mois-ci
        if (event.getEndDate() != null && event.getEndDate().isBefore(monthStart)) {
            return 0;
        }

        return switch (event.getPeriodicity()) {
            case WEEKLY -> {
                // Calcul précis du nombre d'occurrences hebdomadaires dans la fenêtre effective
                LocalDate effectiveStart = event.getStartDate().isBefore(monthStart)
                        ? monthStart
                        : event.getStartDate();
                LocalDate effectiveEnd = event.getEndDate() != null && event.getEndDate().isBefore(monthEnd)
                        ? event.getEndDate()
                        : monthEnd;

                // Nombre de jours dans l'intervalle, divisé par 7 (arrondi supérieur)
                long days = effectiveStart.until(effectiveEnd, ChronoUnit.DAYS) + 1;
                yield (days + 6) / 7; // équivalent mathématique de Math.ceil(days / 7.0)
            }
            case MONTHLY -> 1;
            case QUARTERLY -> {
                // L'événement trimestriel se produit si le nombre de mois depuis la date de début
                // est un multiple de 3. On calcule la différence en mois absolus (tenant compte des années)
                // pour éviter les erreurs de modulo sur les années croisées.
                YearMonth startYearMonth = YearMonth.from(event.getStartDate());
                long totalMonths = startYearMonth.until(month, java.time.temporal.ChronoUnit.MONTHS);
                yield (totalMonths >= 0 && totalMonths % 3 == 0) ? 1 : 0;
            }
            case YEARLY ->
                // L'événement annuel se produit uniquement dans le même mois que la date de début
                (event.getStartDate().getMonthValue() == month.getMonthValue()) ? 1 : 0;
        };
    }
}
