package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o prontuario tem guarda minima de 20 anos pela lei 13.787 de 2018
// entao nenhuma rota da api apaga dado clinico de vez
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClinicalDataRetentionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TreatmentProtocolRepository treatmentProtocolRepository;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;
    private String prescriberToken;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora da guarda");
        prescriber.setEmail("guarda-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da guarda");
        patient.setEmail("guarda-paciente@email.com");
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        prescriberToken = "Bearer " + tokenService.generateToken(prescriber);
    }

    @Test
    void onlyNotificationsHaveADeleteRoute() {
        List<String> deleteRoutes = new ArrayList<>();
        for (RequestMappingInfo mapping : handlerMapping.getHandlerMethods().keySet()) {
            if (mapping.getMethodsCondition().getMethods().contains(RequestMethod.DELETE)) {
                deleteRoutes.addAll(mapping.getPatternValues());
            }
        }

        // o aviso eh do proprio usuario e n faz parte do prontuario
        assertThat(deleteRoutes).containsExactly("/notifications/{id}");
    }

    @Test
    void deletingAPatientOrAPrescriptionIsNotAllowed() throws Exception {
        mockMvc.perform(delete("/paciente/" + patient.getId()).header("Authorization", prescriberToken))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/prescricao/1").header("Authorization", prescriberToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void endingTheFollowUpKeepsTheProtocolSaved() throws Exception {
        ProtocolItemDTO weeklyHamilton = new ProtocolItemDTO(
                ScaleType.ESCALA_HAMILTON, ScaleType.ESCALA_HAMILTON.getDisplayName(), Periodicity.SEMANAL);
        treatmentProtocolService.create(patient.getId(),
                new TreatmentProtocolCreateDTO(LocalDate.now(), 90, null, null, List.of(weeklyHamilton)), prescriber);

        mockMvc.perform(put("/pacientes/" + patient.getId() + "/acompanhamento/encerrar")
                        .header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        long savedProtocols = treatmentProtocolRepository.findAll().stream()
                .filter(protocol -> protocol.getPatient().getId().equals(patient.getId()))
                .count();
        assertThat(savedProtocols).isEqualTo(1);
    }
}
