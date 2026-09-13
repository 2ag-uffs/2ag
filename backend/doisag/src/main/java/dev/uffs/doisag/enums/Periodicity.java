package dev.uffs.doisag.enums;

// de quanto em quanto tempo uma escala volta pro paciente durante o
// acompanhamento
public enum Periodicity {
    SEMANAL(7),
    QUINZENAL(14),
    MENSAL(30);

    private final int days;

    Periodicity(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
