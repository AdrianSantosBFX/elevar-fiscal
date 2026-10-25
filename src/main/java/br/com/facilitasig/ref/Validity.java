package br.com.facilitasig.ref;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Período de vigência [início, fim], ambos inclusivos. Início nulo = não registrado; fim nulo = sem fim. */
public final class Validity {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LocalDate start;
    private final LocalDate end;

    public Validity(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    static Validity parse(String start, String end) {
        return new Validity(date(start), date(end));
    }

    static LocalDate date(String iso) {
        return iso == null || iso.trim().isEmpty() ? null : LocalDate.parse(iso.trim());
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return end;
    }

    public boolean contains(LocalDate day) {
        return (start == null || !day.isBefore(start)) && (end == null || !day.isAfter(end));
    }

    public static String br(LocalDate day) {
        return day == null ? "" : BR.format(day);
    }

    public String startText() {
        return start == null ? "não registrado" : br(start);
    }

    public String endText() {
        return end == null ? "vigente" : br(end);
    }

    @Override
    public String toString() {
        if (start == null) {
            return end == null ? "sem data de início registrada, sem fim" : "até " + br(end);
        }
        return end == null ? "desde " + br(start) + ", sem fim" : "de " + br(start) + " a " + br(end);
    }
}
