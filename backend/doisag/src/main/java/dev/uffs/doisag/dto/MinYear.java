package dev.uffs.doisag.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// data com o ano digitado errado (0202 no lugar de 2026, 1800 no lugar de 1980) passava
// e ia pro historico como a mais antiga do paciente
// vale pra LocalDate e LocalDateTime, e o vazio fica com quem cuida dele
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MinYearValidator.class)
public @interface MinYear {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
