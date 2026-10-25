package br.com.facilitasig.oa;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Regra de prazo de uma obrigação, na notação do catálogo (tools/ref/obrigacoes.py). O mês-base é o mês da
 * competência (mensais) ou dezembro do ano-calendário (anuais).
 * <ul>
 *   <li>{@code DU:m:n} — n-ésimo dia útil do mês-base + m;</li>
 *   <li>{@code DIA:m:d:A|P|F} — dia d do mês-base + m; se não for útil: A antecipa, P prorroga, F mantém;</li>
 *   <li>{@code UDU:m} — último dia útil do mês-base + m;</li>
 *   <li>{@code UF}, {@code MUN} — prazo da UF ou do município, não calculado.</li>
 * </ul>
 */
public final class DeadlineRule {

    private final String kind;
    private final int months;
    private final int number;
    private final char adjust;

    private DeadlineRule(String kind, int months, int number, char adjust) {
        this.kind = kind;
        this.months = months;
        this.number = number;
        this.adjust = adjust;
    }

    public static DeadlineRule parse(String text) {
        String t = text == null ? "" : text.trim();
        String[] p = t.split(":");
        try {
            switch (p[0]) {
                case "DU":
                    check(p.length == 3, t);
                    return new DeadlineRule("DU", Integer.parseInt(p[1]), positive(p[2], t), 'F');
                case "DIA":
                    check(p.length == 4 && p[3].length() == 1 && "APF".contains(p[3]), t);
                    int day = positive(p[2], t);
                    check(day <= 31, t);
                    return new DeadlineRule("DIA", Integer.parseInt(p[1]), day, p[3].charAt(0));
                case "UDU":
                    check(p.length == 2, t);
                    return new DeadlineRule("UDU", Integer.parseInt(p[1]), 0, 'F');
                case "UF":
                case "MUN":
                    check(p.length == 1, t);
                    return new DeadlineRule(p[0], 0, 0, 'F');
                default:
                    throw new IllegalArgumentException("Regra de prazo desconhecida: " + t);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Regra de prazo inválida: " + t, e);
        }
    }

    private static void check(boolean ok, String t) {
        if (!ok) {
            throw new IllegalArgumentException("Regra de prazo inválida: " + t);
        }
    }

    private static int positive(String s, String t) {
        int v = Integer.parseInt(s);
        check(v > 0, t);
        return v;
    }

    /** O sistema calcula a data? (UF e MUN não.) */
    public boolean computable() {
        return !"UF".equals(kind) && !"MUN".equals(kind);
    }

    /**
     * Vencimento para o mês-base (competência das mensais; dezembro do ano das anuais). Nulo quando a regra é
     * da UF ou do município.
     */
    public LocalDate dueDate(YearMonth base) {
        if (!computable()) {
            return null;
        }
        YearMonth m = base.plusMonths(months);
        switch (kind) {
            case "DU":
                return BusinessCalendar.nthBusinessDay(m.getYear(), m.getMonthValue(), number);
            case "UDU":
                return BusinessCalendar.lastBusinessDay(m.getYear(), m.getMonthValue());
            default:
                LocalDate d = m.atDay(Math.min(number, m.lengthOfMonth()));
                if (adjust == 'A') {
                    return BusinessCalendar.previousBusinessDay(d);
                }
                return adjust == 'P' ? BusinessCalendar.nextBusinessDay(d) : d;
        }
    }
}
