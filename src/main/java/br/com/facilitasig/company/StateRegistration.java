package br.com.facilitasig.company;

import java.time.LocalDate;

/**
 * Inscrição estadual de uma UF. Se a empresa é isenta ou não contribuinte, o número
 * é registrado como "ISENTO" ou "NÃO POSSUI" (nunca em branco).
 */
public final class StateRegistration {

    public static final String EXEMPT_TEXT = "ISENTO";

    private final String uf;
    private final IeSituation situation;
    private final String number;
    private final Declared<LocalDate> registrationDate;

    public StateRegistration(String uf, IeSituation situation, String number, Declared<LocalDate> registrationDate) {
        this.uf = uf == null ? "" : uf.trim().toUpperCase();
        this.situation = situation;
        this.number = normalizeNumber(situation, number);
        this.registrationDate = registrationDate == null ? Declared.<LocalDate>none() : registrationDate;
    }

    private static String normalizeNumber(IeSituation situation, String number) {
        if (situation == IeSituation.ISENTA) {
            return EXEMPT_TEXT;
        }
        if (situation == IeSituation.NAO_CONTRIBUINTE) {
            return Declared.NONE_TEXT;
        }
        return number == null ? "" : BrDocs.alphanumeric(number);
    }

    public String uf() {
        return uf;
    }

    public IeSituation situation() {
        return situation;
    }

    /** Número da IE, ou "ISENTO", ou "NÃO POSSUI". */
    public String number() {
        return number;
    }

    public Declared<LocalDate> registrationDate() {
        return registrationDate;
    }

    @Override
    public String toString() {
        return uf + " - " + number;
    }
}
