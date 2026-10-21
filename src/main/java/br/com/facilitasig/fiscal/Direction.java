package br.com.facilitasig.fiscal;

/** Sentido da nota para a empresa ativa. */
public enum Direction {
    ENTRADA("Entrada"), SAIDA("Saída");

    private final String label;

    Direction(String label) {
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
