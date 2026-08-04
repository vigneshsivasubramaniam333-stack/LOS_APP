package com.los.plp.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProgramFieldDefinitionRequest {

    /** Required on create; ignored for system-managed key change on update. */
    private String fieldKey;

    @NotBlank
    private String label;

    @NotBlank
    private String inputType;

    private List<Map<String, Object>> options;

    private Boolean required;

    private Boolean active;

    private Integer sortOrder;

    private List<String> productTypes;

    private String helpText;
}
