package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.AssignableScaleDTO;
import dev.uffs.doisag.dto.ScaleDefinitionDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.ScaleResponseDTO;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.service.ScaleResponseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// as escalas: o formulario de cada uma e as respostas (RF08 e RF21 a RF26)
//
// a tela n conhece escala nenhuma: ela pede a definicao aqui e desenha
// os campos q vierem
@RestController
@RequestMapping("/scales")
public class ScalesController {

    private final ScaleCatalog catalog;
    private final ScaleResponseService responseService;

    public ScalesController(ScaleCatalog catalog, ScaleResponseService responseService) {
        this.catalog = catalog;
        this.responseService = responseService;
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER')")
    @GetMapping("/definitions")
    public List<ScaleDefinitionDTO> getDefinitions() {
        return catalog.all().stream()
                .map(ScaleDefinitionDTO::new)
                .toList();
    }

    // o q o prescritor pode enviar pro paciente responder (RN09)
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping("/assignable")
    public List<AssignableScaleDTO> getAssignableScales() {
        return Arrays.stream(ScaleType.values())
                .filter(ScaleType::isFilledByPatient)
                .map(AssignableScaleDTO::new)
                .toList();
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER')")
    @GetMapping("/definitions/{slug}")
    public ScaleDefinitionDTO getDefinition(@PathVariable String slug) {
        return new ScaleDefinitionDTO(catalog.definitionOf(ScaleType.fromSlug(slug)));
    }

    // o paciente responde uma escala dele
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/{slug}/responses")
    public ResponseEntity<ScaleResponseDTO> answer(@PathVariable String slug,
                                                   @RequestBody @Valid ScaleResponseCreateDTO answerData,
                                                   @AuthenticationPrincipal Patient loggedPatient) {
        ScaleResponseService.AnswerResult result =
                responseService.answer(loggedPatient.getId(), ScaleType.fromSlug(slug), answerData);
        // 201 so quando o dia ainda n tinha resposta, senao foi correcao
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    // as respostas do paciente logado numa escala, q a grade da semana usa
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/{slug}/responses")
    public List<ScaleResponseDTO> getMyResponses(@PathVariable String slug,
                                                 @AuthenticationPrincipal Patient loggedPatient) {
        return responseService.getByPatientIdAndType(loggedPatient.getId(), ScaleType.fromSlug(slug));
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @scaleAccess.canAccess(#id, authentication)")
    @GetMapping("/responses/{id}")
    public ScaleResponseDTO getResponse(@PathVariable Long id) {
        return responseService.getById(id);
    }

    // o paciente corrige enquanto o prescritor n analisou
    // o prescritor n corrige resposta nenhuma: errou anula com motivo e aplica de novo
    @PreAuthorize("hasRole('PATIENT') and @scaleAccess.canAccess(#id, authentication)")
    @PutMapping("/responses/{id}")
    public ScaleResponseDTO updateResponse(@PathVariable Long id,
                                           @RequestBody @Valid ScaleResponseCreateDTO answerData) {
        return responseService.update(id, answerData);
    }

    // o prescritor marca q ja conferiu a resposta e o paciente para de editar
    @PreAuthorize("hasRole('PRESCRIBER') and @scaleAccess.canAccess(#id, authentication)")
    @PutMapping("/responses/{id}/review")
    public ScaleResponseDTO review(@PathVariable Long id, @AuthenticationPrincipal Users loggedUser) {
        return responseService.review(id, loggedUser);
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @scaleAccess.canAccess(#id, authentication)")
    @PutMapping("/responses/{id}/annul")
    public ScaleResponseDTO annul(@PathVariable Long id,
                                  @RequestBody @Valid AnnulmentDTO annulmentData,
                                  @AuthenticationPrincipal Users loggedUser) {
        return responseService.annul(id, annulmentData, loggedUser);
    }

    // o exame ja aplicado nessa consulta, pra tela abrir no resultado em vez
    // de um formulario em branco q o servidor vai recusar no salvar
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @GetMapping("/mental-state-exam/appointments/{appointmentId}")
    public ResponseEntity<ScaleResponseDTO> getMentalStateExam(@PathVariable Long appointmentId) {
        ScaleResponseDTO exam = responseService.getMentalStateExamOf(appointmentId);
        return exam == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(exam);
    }

    // o MEEM eh aplicado pelo prescritor dentro da consulta (RF26 e RN09)
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @PostMapping("/mental-state-exam/appointments/{appointmentId}")
    public ResponseEntity<ScaleResponseDTO> applyMentalStateExam(
            @PathVariable Long appointmentId,
            @RequestBody @Valid ScaleResponseCreateDTO answerData,
            @AuthenticationPrincipal Prescriber loggedPrescriber) {
        ScaleResponseDTO response = responseService.applyMentalStateExam(appointmentId, answerData, loggedPrescriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
