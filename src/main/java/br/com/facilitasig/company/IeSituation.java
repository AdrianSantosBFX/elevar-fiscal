package br.com.facilitasig.company;

/**
 * Situação da inscrição estadual. Corresponde ao indicador de IE do destinatário
 * da NF-e: 1 contribuinte, 2 isento, 9 não contribuinte.
 */
public enum IeSituation {
    INSCRITA("INSCRITA (CONTRIBUINTE)", 1), ISENTA("ISENTA", 2), NAO_CONTRIBUINTE("NÃO CONTRIBUINTE", 9);

    private final String label;
    private final int nfeIndicator;

    IeSituation(String label, int nfeIndicator) {
        this.label = label;
        this.nfeIndicator = nfeIndicator;
    }

    public String label() {
        return label;
    }

    public int nfeIndicator() {
        return nfeIndicator;
    }

    @Override
    public String toString() {
        return label;
    }
}
