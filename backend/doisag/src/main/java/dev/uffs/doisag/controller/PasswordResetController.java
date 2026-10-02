package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.dto.PasswordResetRequestDTO;
import dev.uffs.doisag.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// recuperacao de senha pela tela de login (RF35)
@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // responde igual exista ou n uma conta com esse e-mail
    @PostMapping("/request")
    public ResponseEntity<Void> requestReset(@RequestBody @Valid PasswordResetRequestDTO requestData,
                                             HttpServletRequest request) {
        // campo escondido q so robo preenche: recebe a mesma resposta e nada acontece
        if (requestData.site() == null || requestData.site().isBlank()) {
            passwordResetService.requestReset(requestData.email(), request.getRemoteAddr());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid PasswordResetDTO resetData) {
        passwordResetService.resetPassword(resetData);
        return ResponseEntity.noContent().build();
    }
}
