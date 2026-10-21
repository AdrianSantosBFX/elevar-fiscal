package br.com.facilitasig.company;

import java.time.LocalDate;

/**
 * Período de vigência de um regime tributário. Sem data final ({@code end} = NÃO POSSUI)
 * significa regime vigente.
 */
public final class RegimePeriod {

    private final TaxRegime regime;
    private final LocalDate start;
    private final Declared<LocalDate> end;
    private final Declared<String> note;

    public RegimePeriod(TaxRegime regime, LocalDate start, Declared<LocalDate> end, Declared<String> note) {
        this.regime = regime;
        this.start = start;
        this.end = end == null ? Declared.<LocalDate>none() : end;
        this.note = note == null ? Declared.<String>none() : note;
    }

    public TaxRegime regime() {
        return regime;
    }

    public LocalDate start() {
        return start;
    }

    public Declared<LocalDate> end() {
        return end;
    }

    public Declared<String> note() {
        return note;
    }

    public boolean isOpen() {
        return !end.isPresent();
    }

    /** O regime vigora na data informada? */
    public boolean covers(LocalDate date) {
        return !date.isBefore(start) && (!end.isPresent() || !date.isAfter(end.get()));
    }

    @Override
    public String toString() {
        return regime + " desde " + start + (end.isPresent() ? " até " + end.get() : " (vigente)");
    }
}
