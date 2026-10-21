package br.com.facilitasig.company;

/** Porte da empresa, como no Cartão CNPJ. */
public enum CompanySize {
    MEI("MICROEMPREENDEDOR INDIVIDUAL"), ME("MICRO EMPRESA"), EPP("EMPRESA DE PEQUENO PORTE"), DEMAIS("DEMAIS");

    private final String label;

    CompanySize(String label) {
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
