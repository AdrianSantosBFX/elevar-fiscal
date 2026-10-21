package br.com.facilitasig.company;

import java.text.Normalizer;
import java.util.Objects;

/**
 * Valor de um campo que pode não existir. Regra do sistema: um dado que a empresa
 * não possui NUNCA fica em branco; ele é registrado explicitamente como
 * {@link #NONE_TEXT} ("NÃO POSSUI"). Assim, "não possui" é um fato conhecido
 * (determinado) e se distingue de "ainda não informado", que o sistema não aceita.
 */
public final class Declared<T> {

    /** Texto gravado e exibido quando a empresa não possui o dado. */
    public static final String NONE_TEXT = "NÃO POSSUI";

    private final boolean present;
    private final T value;

    private Declared(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    public static <T> Declared<T> of(T value) {
        return new Declared<T>(true, Objects.requireNonNull(value, "value"));
    }

    @SuppressWarnings("unchecked")
    public static <T> Declared<T> none() {
        return (Declared<T>) NONE;
    }

    private static final Declared<Object> NONE = new Declared<Object>(false, null);

    /**
     * Texto digitado pelo usuário: vazio, ou "não possui" (com ou sem acento, em
     * qualquer caixa), vira {@link #none()}; qualquer outro vira o valor aparado.
     */
    public static Declared<String> ofText(String text) {
        if (text == null) {
            return none();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || isNoneMarker(trimmed)) {
            return none();
        }
        return of(trimmed);
    }

    public static boolean isNoneMarker(String text) {
        if (text == null) {
            return false;
        }
        String folded = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase();
        return folded.equals("NAO POSSUI");
    }

    public boolean isPresent() {
        return present;
    }

    public T get() {
        if (!present) {
            throw new IllegalStateException("O dado é NÃO POSSUI");
        }
        return value;
    }

    public T orElse(T other) {
        return present ? value : other;
    }

    /** Texto para exibição: o valor, ou "NÃO POSSUI". */
    public String display() {
        return present ? String.valueOf(value) : NONE_TEXT;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Declared)) {
            return false;
        }
        Declared<?> that = (Declared<?>) other;
        return present == that.present && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(present, value);
    }

    @Override
    public String toString() {
        return display();
    }
}
