package br.com.facilitasig.company;

/** Regime tributário. */
public enum TaxRegime {
    MEI("MEI"), SIMPLES_NACIONAL("SIMPLES NACIONAL"), LUCRO_PRESUMIDO("LUCRO PRESUMIDO"),
    LUCRO_REAL("LUCRO REAL"), LUCRO_ARBITRADO("LUCRO ARBITRADO"), IMUNE("IMUNE"), ISENTA("ISENTA");

    private final String label;

    TaxRegime(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
