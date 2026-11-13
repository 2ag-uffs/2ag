package dev.uffs.doisag.infra;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// o front acha o HH:mm nos textos dos avisos p/ por o fuso de brasilia entao o formato n muda
public class BrazilianFormat {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private BrazilianFormat() {
    }

    public static String date(LocalDate date) {
        if (date == null) {
            return null;
        }
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    public static String time(LocalDateTime dateTime) {
        return String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
    }

    public static String dateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMAT);
    }

    // tipo 72,5 e 0,3: virgula decimal e sem os zeros a toa. no brasil o ponto separa o milhar,
    // entao com 72.5 a planilha em portugues le texto e o banco devolve 10.000 q seria dez mil
    public static String decimal(Number value) {
        if (value == null) {
            return null;
        }
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(value);
    }

    // tipo 45 min e 7h05
    public static String minutes(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + "h" + String.format("%02d", minutes % 60);
    }
}
