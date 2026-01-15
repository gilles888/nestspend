package be.gilmotech.nestspend.controller;

import be.gilmotech.nestspend.dto.futureevent.*;
import be.gilmotech.nestspend.service.FutureEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/future-events")
@Tag(name = "Future Events", description = "Manage recurring future events and budget projections")
@SecurityRequirement(name = "bearer-jwt")
public class FutureEventController {

    private final FutureEventService futureEventService;

    public FutureEventController(FutureEventService futureEventService) {
        this.futureEventService = futureEventService;
    }

    @GetMapping
    @Operation(
            summary = "Get all future events",
            description = "Returns all future events for the current user's household."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved future events"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<List<FutureEventResponse>> getAllFutureEvents() {
        List<FutureEventResponse> events = futureEventService.getAllFutureEvents();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a future event",
            description = "Returns a specific future event by ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved future event"),
            @ApiResponse(responseCode = "404", description = "Future event not found",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<FutureEventResponse> getFutureEvent(
            @Parameter(description = "UUID of the future event", required = true)
            @PathVariable UUID id
    ) {
        FutureEventResponse event = futureEventService.getFutureEvent(id);
        return ResponseEntity.ok(event);
    }

    @PostMapping
    @Operation(
            summary = "Create a future event",
            description = "Creates a new recurring future event (subscription, regular payment, etc.)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Future event created successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<FutureEventResponse> createFutureEvent(
            @Valid @RequestBody FutureEventCreateRequest request
    ) {
        FutureEventResponse event = futureEventService.createFutureEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a future event",
            description = "Updates an existing future event."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Future event updated successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Future event not found",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<FutureEventResponse> updateFutureEvent(
            @Parameter(description = "UUID of the future event", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody FutureEventUpdateRequest request
    ) {
        FutureEventResponse event = futureEventService.updateFutureEvent(id, request);
        return ResponseEntity.ok(event);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a future event",
            description = "Deletes a future event."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Future event deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Future event not found",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<Void> deleteFutureEvent(
            @Parameter(description = "UUID of the future event", required = true)
            @PathVariable UUID id
    ) {
        futureEventService.deleteFutureEvent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projections")
    @Operation(
            summary = "Get budget projections",
            description = "Returns projected budget data based on future events for 1-12 months."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved projections"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid months parameter",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseEntity<ProjectionResponse> getProjections(
            @Parameter(description = "Number of months to project (1-12)", example = "6")
            @RequestParam(defaultValue = "6") int months
    ) {
        ProjectionResponse projections = futureEventService.getProjections(months);
        return ResponseEntity.ok(projections);
    }
}
