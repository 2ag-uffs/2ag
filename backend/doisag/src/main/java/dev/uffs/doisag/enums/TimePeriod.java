package dev.uffs.doisag.enums;

// enum para os períodos de tempo que o usuário pode escolher no filtro
public enum TimePeriod {
    DIAS_15(15),
    DIAS_30(30),
    DIAS_60(60),
    DIAS_90(90);

    private final int days;

    TimePeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}