package be.gilmotech.nestspend.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    @GetMapping("/api/health")
    @Operation(summary = "Health check", description = "Returns the health status of the API")
    @ApiResponse(responseCode = "200", description = "API is healthy")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }
}
