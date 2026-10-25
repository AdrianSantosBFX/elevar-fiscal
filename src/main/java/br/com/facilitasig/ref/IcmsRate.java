package br.com.facilitasig.ref;

import java.math.BigDecimal;

/**
 * Alíquota de ICMS: interestadual (origem ≠ destino) ou interna (origem = destino, alíquota geral/modal).
 * A interna não inclui o fundo de combate à pobreza (FCP/FECP), informado à parte quando é geral.
 */
public final class IcmsRate {

    /** GERAL (Res. Senado 22/1989) ou IMPORTADO (4%, Res. Senado 13/2012); INTERNA para a alíquota interna. */
    public static final String GERAL = "GERAL";
    public static final String IMPORTADO = "IMPORTADO";
    public static final String INTERNA = "INTERNA";

    private final String ufOrigin;
    private final String ufDestination;
    private final String type;
    private final BigDecimal rate;
    private final BigDecimal fcp;
    private final Validity validity;
    private final String legalBasis;
    private final String note;
    private final String dataset;

    IcmsRate(String ufOrigin, String ufDestination, String type, BigDecimal rate, BigDecimal fcp, Validity validity,
             String legalBasis, String note, String dataset) {
        this.ufOrigin = ufOrigin;
        this.ufDestination = ufDestination;
        this.type = type;
        this.rate = rate;
        this.fcp = fcp;
        this.validity = validity;
        this.legalBasis = legalBasis;
        this.note = note == null ? "" : note;
        this.dataset = dataset;
    }

    public String ufOrigin() {
        return ufOrigin;
    }

    public String ufDestination() {
        return ufDestination;
    }

    public String type() {
        return type;
    }

    public boolean isInternal() {
        return INTERNA.equals(type);
    }

    /** Alíquota em % (ex.: 12, 7, 4, 18, 20.5). */
    public BigDecimal rate() {
        return rate;
    }

    /** Fundo de pobreza geral da UF, em %; nulo quando não há ou não está confirmado. */
    public BigDecimal fcp() {
        return fcp;
    }

    public Validity validity() {
        return validity;
    }

    public String legalBasis() {
        return legalBasis;
    }

    public String note() {
        return note;
    }

    public String dataset() {
        return dataset;
    }

    /** "18%", "20% + 2% FCP". */
    public String display() {
        String r = Rates.pct(rate) + "%";
        return fcp == null ? r : r + " + " + Rates.pct(fcp) + "% FCP";
    }
}
