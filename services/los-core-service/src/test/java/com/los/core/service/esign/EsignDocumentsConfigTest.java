package com.los.core.service.esign;

import com.los.core.model.entity.WorkflowConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EsignDocumentsConfigTest {

    @Test
    void singleDefaultWhenNoEsignStep() {
        EsignDocumentsConfig.Settings settings = EsignDocumentsConfig.fromWorkflowSteps(List.of(
                Map.of("step", "PAN_VERIFY", "mandatory", true)
        ));
        assertThat(settings.defaultDocumentKey()).isEqualTo("KFS_AGREEMENT");
        assertThat(settings.additional()).isEmpty();
        assertThat(settings.systemDocuments()).isEmpty();
        assertThat(settings.expectedPageCount()).isEqualTo(2);
        assertThat(settings.hasMultiDocumentSigning()).isFalse();
    }

    @Test
    void parsesAdditionalWithCollectAtIntakeAndPageCount() {
        EsignDocumentsConfig.Settings settings = EsignDocumentsConfig.fromWorkflowSteps(List.of(
                Map.of(
                        "step", "ESIGN_AGREEMENT",
                        "esignDocuments", Map.of(
                                "defaultDocumentKey", "ANCHOR_PROGRAM_TERMS",
                                "expectedPageCount", 2,
                                "additional", List.of(
                                        Map.of(
                                                "documentType", "GST",
                                                "label", "GST certificate",
                                                "required", true,
                                                "collectAtIntake", true,
                                                "expectedPageCount", 2
                                        ),
                                        Map.of(
                                                "documentType", "BOARD_RESOLUTION",
                                                "required", false
                                        )
                                )
                        )
                )
        ));
        assertThat(settings.defaultDocumentKey()).isEqualTo("ANCHOR_PROGRAM_TERMS");
        assertThat(settings.requiredAdditionalTypes()).containsExactly("GST");
        assertThat(settings.intakeCollectAdditional()).hasSize(1);
        assertThat(settings.expectedPageCountFor("GST")).isEqualTo(2);
        assertThat(settings.labelFor("GST")).isEqualTo("GST certificate");
        assertThat(settings.labelFor("ANCHOR_PROGRAM_TERMS")).containsIgnoringCase("program");
    }

    @Test
    void prefersIntakeConfigEsignSigningDocumentsOverStep() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(Map.of(
                        "esignSigningDocuments", List.of(
                                Map.of(
                                        "documentType", "BOARD_RESOLUTION",
                                        "label", "Board resolution",
                                        "required", true,
                                        "collectAtIntake", false,
                                        "expectedPageCount", 3
                                )
                        )
                ))
                .steps(List.of(
                        Map.of(
                                "step", "ESIGN_AGREEMENT",
                                "esignDocuments", Map.of(
                                        "defaultDocumentKey", "KFS_AGREEMENT",
                                        "additional", List.of(
                                                Map.of("documentType", "GST", "required", true, "collectAtIntake", true)
                                        )
                                )
                        )
                ))
                .build();
        EsignDocumentsConfig.Settings settings = EsignDocumentsConfig.fromWorkflow(wf);
        assertThat(settings.defaultDocumentKey()).isEqualTo("KFS_AGREEMENT");
        assertThat(settings.additional()).hasSize(1);
        assertThat(settings.additional().get(0).documentType()).isEqualTo("BOARD_RESOLUTION");
        assertThat(settings.expectedPageCountFor("BOARD_RESOLUTION")).isEqualTo(3);
        assertThat(settings.additional().get(0).collectAtIntake()).isFalse();
        assertThat(settings.isAdditionalSigningType("BOARD_RESOLUTION")).isTrue();
        assertThat(settings.isAdditionalSigningType("KFS_AGREEMENT")).isFalse();
    }

    @Test
    void parsesSystemDocumentsAndOverridesDefaultKey() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(Map.of(
                        "esignSystemDocuments", List.of(
                                Map.of(
                                        "documentKey", "ANCHOR_PROGRAM_TERMS",
                                        "label", "Anchor program terms",
                                        "enabled", true,
                                        "expectedPageCount", 2,
                                        "templateFileName", "terms.docx",
                                        "templateBase64", "YQ=="
                                ),
                                Map.of(
                                        "documentKey", "SANCTION_LETTER",
                                        "label", "Sanction letter",
                                        "enabled", true,
                                        "expectedPageCount", 2
                                )
                        )
                ))
                .steps(List.of())
                .build();
        EsignDocumentsConfig.Settings settings = EsignDocumentsConfig.fromWorkflow(wf);
        assertThat(settings.defaultDocumentKey()).isEqualTo("ANCHOR_PROGRAM_TERMS");
        assertThat(settings.systemDocuments()).hasSize(2);
        assertThat(settings.enabledSystemExtras()).hasSize(1);
        assertThat(settings.enabledSystemExtras().get(0).documentKey()).isEqualTo("SANCTION_LETTER");
        assertThat(settings.hasMultiDocumentSigning()).isTrue();
        assertThat(settings.labelFor("SANCTION_LETTER")).isEqualTo("Sanction letter");
        assertThat(settings.systemDocuments().get(0).templateFileName()).isEqualTo("terms.docx");
        assertThat(settings.expectedPageCountFor("SANCTION_LETTER")).isEqualTo(2);
    }

    @Test
    void stripsSystemKeysFromUploadAdditionalList() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(Map.of(
                        "esignSigningDocuments", List.of(
                                Map.of("documentType", "SANCTION_LETTER", "required", true),
                                Map.of("documentType", "BOARD_RESOLUTION", "required", true)
                        )
                ))
                .steps(List.of())
                .build();
        EsignDocumentsConfig.Settings settings = EsignDocumentsConfig.fromWorkflow(wf);
        assertThat(settings.additional()).hasSize(1);
        assertThat(settings.additional().get(0).documentType()).isEqualTo("BOARD_RESOLUTION");
    }

    @Test
    void absentSystemDocumentsDoesNotChangeSingleDocBehaviour() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(Map.of("policy", "WORKFLOW_DRIVEN"))
                .steps(List.of())
                .build();
        EsignDocumentsConfig.Settings settings = EsignDocumentsConfig.fromWorkflow(wf);
        assertThat(settings.systemDocuments()).isEmpty();
        assertThat(settings.hasMultiDocumentSigning()).isFalse();
        assertThat(settings.defaultDocumentKey()).isEqualTo("KFS_AGREEMENT");
    }
}
