package br.com.facilitasig.fiscal;

import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Leitura de valores numéricos do XML e comparação com tolerância. Ausente = null (nunca zero). */
final class Num {

    static final BigDecimal CENT = new BigDecimal("0.01");
    static final BigDecimal TWO_CENTS = new BigDecimal("0.02");
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private Num() {
    }

    /** Valor no caminho, ou null se ausente ou inválido. */
    static BigDecimal get(Element e, String... path) {
        return DocValidator.dec(X.text(e, path));
    }

    /** Valor no caminho, ou zero se ausente. Usar só onde a ausência significa zero no leiaute. */
    static BigDecimal z(Element e, String... path) {
        BigDecimal v = get(e, path);
        return v == null ? BigDecimal.ZERO : v;
    }

    static boolean has(Element e, String... path) {
        return X.path(e, path) != null;
    }

    /** a e b diferem mais que a tolerância? (nulos nunca "diferem": não há o que comparar) */
    static boolean differ(BigDecimal a, BigDecimal b, BigDecimal tol) {
        return a != null && b != null && a.subtract(b).abs().compareTo(tol) > 0;
    }

    static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    static boolean isZero(BigDecimal v) {
        return v != null && v.signum() == 0;
    }

    /** base × alíquota percentual, com 2 casas. */
    static BigDecimal pct(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal mul2(BigDecimal a, BigDecimal b) {
        return a.multiply(b).setScale(2, RoundingMode.HALF_UP);
    }

    static boolean eq(BigDecimal v, String literal) {
        return v != null && v.compareTo(new BigDecimal(literal)) == 0;
    }

    static String fmt(BigDecimal v) {
        return v == null ? Fields.NA : v.setScale(Math.max(2, Math.min(v.scale(), 4)), RoundingMode.HALF_UP).toPlainString();
    }
}
