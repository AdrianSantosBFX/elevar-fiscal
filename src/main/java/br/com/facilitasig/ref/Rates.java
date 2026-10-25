package br.com.facilitasig.ref;

import java.math.BigDecimal;

/** Formatação de percentuais guardados em centésimos (250 = 2,5%). */
final class Rates {

    private Rates() {
    }

    static BigDecimal fromX100(int x100) {
        return BigDecimal.valueOf(x100, 2).stripTrailingZeros();
    }

    static BigDecimal fromX100(Integer x100) {
        return x100 == null ? null : fromX100(x100.intValue());
    }

    /** 20.5 → "20,5"; 18 → "18". */
    static String pct(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
