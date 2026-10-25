package br.com.facilitasig.ref;

/** Resposta de uma consulta: situação, o registro (quando existe) e a explicação em texto. */
public final class Lookup<T> {

    private final LookupStatus status;
    private final T value;
    private final String message;

    Lookup(LookupStatus status, T value, String message) {
        this.status = status;
        this.value = value;
        this.message = message;
    }

    public LookupStatus status() {
        return status;
    }

    /** O registro encontrado (também em FORA_DE_VIGENCIA, com a vigência mais próxima); senão nulo. */
    public T value() {
        return value;
    }

    public String message() {
        return message;
    }

    public boolean found() {
        return status == LookupStatus.ENCONTRADO;
    }

    @Override
    public String toString() {
        return status + ": " + message;
    }
}
