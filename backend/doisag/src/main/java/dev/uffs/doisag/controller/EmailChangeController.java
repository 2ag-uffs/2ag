package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.EmailChangeConfirmDTO;
import dev.uffs.doisag.service.EmailChangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// confirmacao do e-mail novo pelo link q chegou nele (RF18, issue 130)
// sem login de proposito: o link costuma ser aberto no celular, em outro navegador
// o pedido em si fica no ProfileController, pq ali a pessoa esta logada e confere a senha
@RestController
@RequestMapping("/auth/email-change")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    public EmailChangeController(EmailChangeService emailChangeService) {
        this.emailChangeService = emailChangeService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody @Valid EmailChangeConfirmDTO confirmData) {
        emailChangeService.confirm(confirmData.token());
        return ResponseEntity.noContent().build();
    }
}
