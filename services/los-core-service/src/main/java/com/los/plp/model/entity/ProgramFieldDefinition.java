package com.los.plp.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "program_field_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "field_key", nullable = false, unique = true, length = 100)
    private String fieldKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "input_type", nullable = false, length = 20)
    private String inputType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> optionsJson;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_types", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> productTypes = new ArrayList<>();

    @Column(name = "system_managed", nullable = false)
    @Builder.Default
    private boolean systemManaged = false;

    @Column(name = "help_text", length = 500)
    private String helpText;

    @Column(name = "storage_target", nullable = false, length = 50)
    @Builder.Default
    private String storageTarget = "CUSTOM";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
