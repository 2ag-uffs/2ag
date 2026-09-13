package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ApiResponseDTO;
import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.LoginDTO;
import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.dto.SessionUserDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.security.SessionCookieService;
import dev.uffs.doisag.service.AuthService;
import dev.uffs.doisag.service.PasswordService;
import dev.uffs.doisag.service.PatientService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// entrada e saida do sistema
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthService authService;
    private final SessionCookieService sessionCookieService;
    private final PatientService patientService;
    private final PasswordService passwordService;

    public AuthenticationController(AuthService authService, SessionCookieService sessionCookieService,
                                    PatientService patientService, PasswordService passwordService) {
        this.authService = authService;
        this.sessionCookieService = sessionCookieService;
        this.patientService = patientService;
        this.passwordService = passwordService;
    }

    // confere a senha e devolve o cookie da sessao junto com os dados de quem entrou
    @PostMapping("/login")
    public SessionUserDTO login(@RequestBody @Valid LoginDTO loginData, HttpServletResponse response) {
        Users user = authService.login(loginData.email(), loginData.password());
        sessionCookieService.writeSession(response, user);
        return new SessionUserDTO(user);
    }

    // apaga o cookie e a sessao acaba
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        sessionCookieService.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    // quem esta logado agora
    @GetMapping("/me")
    public SessionUserDTO getLoggedUser(@AuthenticationPrincipal Users loggedUser) {
        return new SessionUserDTO(loggedUser);
    }

    // o paciente cria a conta pelo link de convite e ja sai logado
    @PostMapping("/register")
    public ResponseEntity<SessionUserDTO> register(@RequestBody @Valid RegisterDTO registerData,
                                                   HttpServletResponse response) {
        Patient patient = patientService.registerPatient(registerData);
        sessionCookieService.writeSession(response, patient);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SessionUserDTO(patient));
    }

    // cada um so troca a propria senha entao a rota n tem id
    @PutMapping("/senha")
    public ApiResponseDTO changePassword(@RequestBody @Valid ChangePasswordDTO passwordData,
                                         @AuthenticationPrincipal Users loggedUser) {
        passwordService.trocarSenha(loggedUser, passwordData);
        return new ApiResponseDTO("Senha alterada com sucesso.");
    }
}
