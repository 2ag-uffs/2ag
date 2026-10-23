package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// dados do formulario de login
// o tamanho maximo barra o corpo gigante antes da contagem de senha errada, q fica em memoria
public record LoginDTO(
        @NotBlank(message = "Informe o e-mail")
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String email,

        // folgado de proposito: a senha mais longa q o bcrypt guarda tem 72 bytes
        @NotBlank(message = "Informe a senha")
        @Size(max = 255, message = "A senha pode ter até 255 caracteres")
        String password
) {
}
