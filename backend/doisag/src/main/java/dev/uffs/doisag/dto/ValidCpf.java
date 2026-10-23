package dev.uffs.doisag.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// cpf conferido pela nossa conta dos digitos (CpfRules), no lugar do @CPF da biblioteca
// o vazio fica com o @NotBlank
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCpfValidator.class)
public @interface ValidCpf {

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
