package br.com.facilitasig.company;

/** Matriz ou filial (só se aplica a CNPJ). */
public enum EstablishmentType {
    MATRIZ("MATRIZ"), FILIAL("FILIAL"), NAO_APLICAVEL("NÃO SE APLICA");

    private final String label;

    EstablishmentType(String label) {
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
