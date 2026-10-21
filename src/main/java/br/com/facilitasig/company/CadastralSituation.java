package br.com.facilitasig.company;

/** Situação cadastral na Receita Federal. */
public enum CadastralSituation {
    ATIVA("ATIVA"), SUSPENSA("SUSPENSA"), INAPTA("INAPTA"), BAIXADA("BAIXADA"), NULA("NULA");

    private final String label;

    CadastralSituation(String label) {
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
