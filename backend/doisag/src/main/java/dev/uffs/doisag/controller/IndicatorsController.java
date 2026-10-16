package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PilotIndicatorsDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.IndicatorsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// indicadores do piloto do prescritor logado (RF39)
// so numero somado, entao n abre prontuario e n entra na trilha
@RestController
@RequestMapping("/indicators")
public class IndicatorsController {

    private final IndicatorsService indicatorsService;

    public IndicatorsController(IndicatorsService indicatorsService) {
        this.indicatorsService = indicatorsService;
    }

    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public PilotIndicatorsDTO getIndicators(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return indicatorsService.getIndicators(loggedPrescriber.getId(), from, to);
    }
}
