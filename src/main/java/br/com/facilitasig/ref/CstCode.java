package br.com.facilitasig.ref;

/** Um código de situação tributária (Origem, CST do ICMS, CSOSN, CST do IPI ou do PIS/Cofins). */
public final class CstCode {

    private final CstTable table;
    private final String code;
    private final String description;
    private final Validity validity;
    private final String legalBasis;
    private final String note;
    private final String dataset;

    CstCode(CstTable table, String code, String description, Validity validity, String legalBasis, String note, String dataset) {
        this.table = table;
        this.code = code;
        this.description = description;
        this.validity = validity;
        this.legalBasis = legalBasis;
        this.note = note == null ? "" : note;
        this.dataset = dataset;
    }

    public CstTable table() {
        return table;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public Validity validity() {
        return validity;
    }

    public String legalBasis() {
        return legalBasis;
    }

    /** Observação da curadoria (ex.: pontos [validar]); vazio quando não há. */
    public String note() {
        return note;
    }

    public String dataset() {
        return dataset;
    }
}
