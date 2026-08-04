package com.los.plp.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProgramFieldDefinitionResponse {
    private UUID id;
    private String fieldKey;
    private String label;
    private String inputType;
    private List<Map<String, Object>> options;
    private boolean required;
    private boolean active;
    private int sortOrder;
    private List<String> productTypes;
    private boolean systemManaged;
    private String helpText;
    private String storageTarget;
    private Instant createdAt;
    private Instant updatedAt;
}
