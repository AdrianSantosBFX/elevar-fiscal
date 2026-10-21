package br.com.facilitasig.company;

import java.util.Objects;

/** Atividade econômica: código de 7 dígitos (sem pontuação) e descrição. */
public final class Cnae {

    private final String code;
    private final String description;

    public Cnae(String code, String description) {
        this.code = BrDocs.digits(code);
        this.description = description == null ? "" : description.trim();
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    /** Código no formato 99.99-9-99. */
    public String formattedCode() {
        return BrDocs.formatCnae(code);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Cnae && ((Cnae) other).code.equals(code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return formattedCode() + " - " + description;
    }
}
