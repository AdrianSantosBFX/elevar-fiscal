package br.com.facilitasig.ref;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uma obrigação acessória do catálogo (Lucro Presumido e Lucro Real), como está no banco de referência.
 * A aplicação a uma empresa e o cálculo do prazo ficam em {@code br.com.facilitasig.oa}.
 */
public final class Obligation {

    /** Como a obrigação se aplica a um regime. */
    public enum Condition {
        /** Toda empresa do regime. */
        SEMPRE("Obrigatória"),
        /** Quem tem inscrição estadual ativa (na UF da linha, ou em qualquer UF na linha genérica). */
        IE("Obrigatória para contribuinte com inscrição estadual"),
        /** Quem tem inscrição municipal: depende da lei do município. */
        IM("Depende do município"),
        /** Depende de um fato que o cadastro não mostra (folha, retenções, benefícios...). */
        CONDICIONAL("Condicional");

        private final String label;

        Condition(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final String LP = "LP";
    public static final String LR = "LR";

    private final String code;
    private final String uf;
    private final String name;
    private final String sphere;
    private final Map<String, Condition> regimes;
    private final String periodicity;
    private final String deadlineRule;
    private final String deadlineText;
    private final String condition;
    private final String derivable;
    private final String fromInvoices;
    private final String legalBasis;
    private final Validity validity;
    private final String replacedBy;
    private final String note;

    Obligation(String code, String uf, String name, String sphere, String regimes, String periodicity, String deadlineRule,
               String deadlineText, String condition, String derivable, String fromInvoices, String legalBasis,
               Validity validity, String replacedBy, String note) {
        this.code = code;
        this.uf = uf;
        this.name = name;
        this.sphere = sphere;
        this.regimes = parseRegimes(regimes);
        this.periodicity = periodicity;
        this.deadlineRule = deadlineRule;
        this.deadlineText = deadlineText;
        this.condition = condition;
        this.derivable = derivable;
        this.fromInvoices = fromInvoices == null ? "" : fromInvoices;
        this.legalBasis = legalBasis;
        this.validity = validity;
        this.replacedBy = replacedBy == null ? "" : replacedBy;
        this.note = note == null ? "" : note;
    }

    /** "LP=SEMPRE;LR=IE" → {LP: SEMPRE, LR: IE}. */
    static Map<String, Condition> parseRegimes(String text) {
        Map<String, Condition> out = new LinkedHashMap<String, Condition>();
        for (String part : text.split(";")) {
            String[] kv = part.trim().split("=");
            if (kv.length != 2 || !(LP.equals(kv[0].trim()) || LR.equals(kv[0].trim()))) {
                throw new IllegalArgumentException("Regimes inválidos: " + text);
            }
            out.put(kv[0].trim(), Condition.valueOf(kv[1].trim()));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Regimes vazios");
        }
        return Collections.unmodifiableMap(out);
    }

    public String code() {
        return code;
    }

    /** "*" = vale para todas as UFs; UF específica sobrepõe a linha genérica. */
    public String uf() {
        return uf;
    }

    public boolean isGeneric() {
        return "*".equals(uf);
    }

    public String name() {
        return name;
    }

    /** FEDERAL, ESTADUAL ou MUNICIPAL. */
    public String sphere() {
        return sphere;
    }

    /** Condição no regime ("LP" ou "LR"); nula se a obrigação não é desse regime. */
    public Condition conditionFor(String regime) {
        return regimes.get(regime);
    }

    public Map<String, Condition> regimes() {
        return regimes;
    }

    /** MENSAL ou ANUAL. */
    public String periodicity() {
        return periodicity;
    }

    public boolean isAnnual() {
        return "ANUAL".equals(periodicity);
    }

    public String deadlineRule() {
        return deadlineRule;
    }

    public String deadlineText() {
        return deadlineText;
    }

    /** Quem está obrigado, em texto. */
    public String condition() {
        return condition;
    }

    /** SIM, PARCIAL ou NAO: quanto as notas fiscais sustentam a obrigação. */
    public String derivable() {
        return derivable;
    }

    public String fromInvoices() {
        return fromInvoices;
    }

    public String legalBasis() {
        return legalBasis;
    }

    /** Competências em que a obrigação existe (fim = extinta depois disso). */
    public Validity validity() {
        return validity;
    }

    public String replacedBy() {
        return replacedBy;
    }

    public String note() {
        return note;
    }

    public String regimesText() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Condition> e : regimes.entrySet()) {
            sb.append(sb.length() == 0 ? "" : " · ").append(e.getKey()).append(": ").append(e.getValue().label());
        }
        return sb.toString();
    }
}
