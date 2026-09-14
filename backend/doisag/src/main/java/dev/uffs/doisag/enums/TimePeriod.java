package dev.uffs.doisag.enums;

// os periodos que o usuário pode escolher no filtro de evolução
public enum TimePeriod {

    DIAS_15(15),
    DIAS_30(30),
    DIAS_60(60),
    DIAS_90(90),
    // todo o tempo, q na pratica eh desde o comeco do tratamento (RF28)
    TUDO(36500);

    private final int days;

    TimePeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
