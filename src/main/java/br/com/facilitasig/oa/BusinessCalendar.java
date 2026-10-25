package br.com.facilitasig.oa;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dias úteis para prazos de obrigações federais: segunda a sexta, exceto feriados nacionais.
 *
 * <p>Feriados (Lei 662/1949, Lei 6.802/1980, Lei 14.759/2023): 01/01, 21/04, 01/05, 07/09, 12/10, 02/11, 15/11,
 * 20/11 (desde 2024) e 25/12; Sexta-feira Santa. Carnaval (segunda e terça) e Corpus Christi são pontos facultativos
 * federais sem expediente bancário e são tratados como não úteis [validar caso a caso]. Feriados estaduais e
 * municipais não entram: prazos estaduais e municipais calculados aqui são aproximações.
 */
public final class BusinessCalendar {

    private BusinessCalendar() {
    }

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher, calendário gregoriano). */
    public static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    /** Feriados nacionais (e pontos facultativos sem expediente bancário) do ano, com o nome. */
    public static Map<LocalDate, String> holidays(int year) {
        Map<LocalDate, String> h = new LinkedHashMap<LocalDate, String>();
        LocalDate easter = easter(year);
        h.put(LocalDate.of(year, 1, 1), "Confraternização Universal");
        h.put(easter.minusDays(48), "Carnaval (segunda-feira)");
        h.put(easter.minusDays(47), "Carnaval (terça-feira)");
        h.put(easter.minusDays(2), "Sexta-feira Santa");
        h.put(LocalDate.of(year, 4, 21), "Tiradentes");
        h.put(LocalDate.of(year, 5, 1), "Dia do Trabalho");
        h.put(easter.plusDays(60), "Corpus Christi");
        h.put(LocalDate.of(year, 9, 7), "Independência");
        h.put(LocalDate.of(year, 10, 12), "Nossa Senhora Aparecida");
        h.put(LocalDate.of(year, 11, 2), "Finados");
        h.put(LocalDate.of(year, 11, 15), "Proclamação da República");
        if (year >= 2024) {
            h.put(LocalDate.of(year, 11, 20), "Dia Nacional de Zumbi e da Consciência Negra");
        }
        h.put(LocalDate.of(year, 12, 25), "Natal");
        return h;
    }

    public static boolean isBusinessDay(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays(d.getYear()).containsKey(d);
    }

    public static LocalDate previousBusinessDay(LocalDate d) {
        LocalDate x = d;
        while (!isBusinessDay(x)) {
            x = x.minusDays(1);
        }
        return x;
    }

    public static LocalDate nextBusinessDay(LocalDate d) {
        LocalDate x = d;
        while (!isBusinessDay(x)) {
            x = x.plusDays(1);
        }
        return x;
    }

    /** n-ésimo dia útil do mês (n ≥ 1). */
    public static LocalDate nthBusinessDay(int year, int month, int n) {
        LocalDate x = LocalDate.of(year, month, 1);
        int count = 0;
        while (true) {
            if (isBusinessDay(x) && ++count == n) {
                return x;
            }
            x = x.plusDays(1);
        }
    }

    public static LocalDate lastBusinessDay(int year, int month) {
        return previousBusinessDay(LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
    }
}
