package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ConsentTermDTO;
import dev.uffs.doisag.service.ConsentTermService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// termo de consentimento do cadastro (RF36)
@RestController
@RequestMapping("/consent-term")
public class ConsentTermController {

    private final ConsentTermService consentTermService;

    public ConsentTermController(ConsentTermService consentTermService) {
        this.consentTermService = consentTermService;
    }

    // rota publica pq a pessoa le o termo antes de ter conta
    @GetMapping
    public ConsentTermDTO getCurrentTerm() {
        return consentTermService.getCurrentTerm();
    }
}
