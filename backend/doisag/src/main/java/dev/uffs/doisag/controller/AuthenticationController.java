package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ApiResponseDTO; // import do novo dto
import dev.uffs.doisag.dto.LoginDTO;
import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.dto.TokenDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.PatientService;
import dev.uffs.doisag.security.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/auth") // mudei a rota base para /auth para agrupar login e registro
public class AuthenticationController {

    private final AuthenticationManager manager;
    private final TokenService tokenService;
    private final PatientService patientService; // injete o patientService

    // injeção de dependências via construtor
    public AuthenticationController(AuthenticationManager manager, TokenService tokenService, PatientService patientService) {
        this.manager = manager;
        this.tokenService = tokenService;
        this.patientService = patientService;
    }

    // endpoint que o frontend vai chamar para fazer login
    @PostMapping("/login")
    public ResponseEntity<TokenDTO> efetuarLogin(@RequestBody LoginDTO dados) {
        // o spring usa esse objeto para juntar o email e a senha que vieram do DTO
        var authenticationToken = new UsernamePasswordAuthenticationToken(dados.email(), dados.senha());
        // o manager chama nosso AuthorizationService para validar o login e a senha
        var authentication = manager.authenticate(authenticationToken);
        // se o login deu certo, a gente pega o usuário logado e gera o token jwt
        var tokenJWT = tokenService.generateToken((Users) authentication.getPrincipal());
        // devolve o token para o frontend dentro do DTO correto
        return ResponseEntity.ok(new TokenDTO(tokenJWT));
    }

    // endpoint para registrar pacientes em /auth/register
    @PostMapping("/register")
    // o método retorna nosso DTO de resposta
    public ResponseEntity<ApiResponseDTO> register(@RequestBody @Valid RegisterDTO dados) { // adicionei o @Valid aqui para checar nosso registerDTO
        patientService.registerPatient(dados);

        // retorna 201 created com um corpo de mensagem
        var response = new ApiResponseDTO("Cadastro realizado com sucesso, seja bem-vindo(a)!");
        return ResponseEntity.status(201).body(response);
    }
}