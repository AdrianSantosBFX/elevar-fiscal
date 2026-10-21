package br.com.facilitasig.company;

/** Tipo de identificador do contribuinte. */
public enum IdType {
    CNPJ("CNPJ"), CPF("CPF"), CAEPF("CAEPF");

    private final String label;

    IdType(String label) {
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
