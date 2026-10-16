package dev.uffs.doisag.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;

public class MinYearValidator implements ConstraintValidator<MinYear, Temporal> {

    private int minYear;

    @Override
    public void initialize(MinYear annotation) {
        minYear = annotation.value();
    }

    @Override
    public boolean isValid(Temporal date, ConstraintValidatorContext context) {
        return date == null || date.get(ChronoField.YEAR) >= minYear;
    }
}
