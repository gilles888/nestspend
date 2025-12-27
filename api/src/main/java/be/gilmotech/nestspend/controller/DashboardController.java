package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.dashboard.DashboardResponse;
import be.gilmotech.nestspend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Dashboard endpoints for monthly aggregations and statistics")
@SecurityRequirement(name = "bearer-jwt")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(
            summary = "Get monthly dashboard",
            description = "Returns monthly aggregations including total income, total expenses, " +
                    "net balance, and expense breakdown by category for the current user's household."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved monthly dashboard"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid month format (expected YYYY-MM)",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<DashboardResponse> getMonthlyDashboard(
            @Parameter(description = "Month in YYYY-MM format", example = "2025-12", required = true)
            @RequestParam String month
    ) {
        DashboardResponse response = dashboardService.getMonthlyDashboard(month);
        return ResponseEntity.ok(response);
    }
}
