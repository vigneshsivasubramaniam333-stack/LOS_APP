package com.los.core.controller;

import com.los.core.exception.UnauthorizedException;
import com.los.core.model.dto.response.GeoCityResponse;
import com.los.core.model.dto.response.GeoStateResponse;
import com.los.core.service.master.GeoMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only India geo master for the PLP anchor portal (same data as {@link GeoMasterController}).
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/integrations/plp/geo", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "PLP Geo Integration", description = "Server-to-server geo master for anchor portal dropdowns")
public class PlpGeoIntegrationController {

    public static final String INTEGRATION_KEY_HEADER = PlpAnchorApplicationIntegrationController.INTEGRATION_KEY_HEADER;

    private final GeoMasterService geoMasterService;

    @Value("${los.plp.inbound-api-key:}")
    private String configuredApiKey;

    @GetMapping("/states")
    @Operation(summary = "List active India states for portal dropdowns")
    public ResponseEntity<List<GeoStateResponse>> listStates(
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        return ResponseEntity.ok(geoMasterService.listActiveStates());
    }

    @GetMapping("/states/{stateId}/cities")
    @Operation(summary = "List active cities for a state")
    public ResponseEntity<List<GeoCityResponse>> listCities(
            @PathVariable UUID stateId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        return ResponseEntity.ok(geoMasterService.listActiveCitiesForState(stateId));
    }

    private void requireIntegrationKey(String providedKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new UnauthorizedException("PLP anchor integration is not configured");
        }
        if (providedKey == null || !configuredApiKey.equals(providedKey.trim())) {
            throw new UnauthorizedException("Invalid or missing " + INTEGRATION_KEY_HEADER + " header");
        }
    }
}
