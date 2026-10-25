package br.com.facilitasig.oa;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.IeSituation;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.company.StateRegistration;
import br.com.facilitasig.company.TaxRegime;
import br.com.facilitasig.ref.Obligation;
import br.com.facilitasig.ref.Validity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aplica o catálogo de obrigações acessórias a uma empresa: regime vigente na competência, inscrições estaduais e
 * municipal e vigência de cada obrigação. Determinístico e explicado: cada resultado diz o motivo. O que depende de
 * fato que o cadastro não mostra (folha, retenções, benefícios, recebimento em espécie...) fica "A confirmar",
 * nunca "Não se aplica".
 */
public final class ObligationService {

    private final List<Obligation> catalog;

    public ObligationService(List<Obligation> catalog) {
        this.catalog = catalog;
    }

    public List<Obligation> catalog() {
        return catalog;
    }

    /** "LP", "LR" ou nulo (regime fora do catálogo). */
    public static String regimeCode(TaxRegime r) {
        if (r == TaxRegime.LUCRO_PRESUMIDO) {
            return Obligation.LP;
        }
        return r == TaxRegime.LUCRO_REAL ? Obligation.LR : null;
    }

    /**
     * Obrigações da empresa: as mensais da competência e as anuais do ano-calendário da competência.
     * Uma linha por obrigação (a linha da UF da empresa substitui a genérica; obrigação estadual genérica aparece
     * para cada UF em que a empresa tem inscrição).
     */
    public List<Applicability> evaluate(Company company, YearMonth competence) {
        List<Applicability> out = new ArrayList<Applicability>();
        for (Obligation o : catalog) {
            if (o.isAnnual()) {
                out.addAll(evaluateOne(company, o, YearMonth.of(competence.getYear(), 12)));
            } else {
                out.addAll(evaluateOne(company, o, competence));
            }
        }
        return out;
    }

    /** Obrigações da empresa com vencimento no mês informado (procura competências de até 12 meses antes). */
    public List<Applicability> dueIn(Company company, YearMonth month) {
        List<Applicability> out = new ArrayList<Applicability>();
        for (Obligation o : catalog) {
            if (o.isAnnual()) {
                for (int y = month.getYear() - 1; y <= month.getYear(); y++) {
                    addIfDue(out, evaluateOne(company, o, YearMonth.of(y, 12)), month);
                }
            } else {
                for (int back = 0; back <= 12; back++) {
                    addIfDue(out, evaluateOne(company, o, month.minusMonths(back)), month);
                }
            }
        }
        out.sort(Comparator.comparing(Applicability::dueDate));
        return out;
    }

    private static void addIfDue(List<Applicability> out, List<Applicability> candidates, YearMonth month) {
        for (Applicability a : candidates) {
            if (a.dueDate() != null && YearMonth.from(a.dueDate()).equals(month)
                    && (a.status() == Applicability.Status.OBRIGATORIA || a.status() == Applicability.Status.A_CONFIRMAR)) {
                out.add(a);
            }
        }
    }

    /** Uma linha do calendário genérico (sem empresa): obrigação, competência e vencimento. */
    public static final class Due {
        private final Obligation obligation;
        private final String period;
        private final LocalDate date;
        private final boolean mandatory;

        Due(Obligation obligation, String period, LocalDate date, boolean mandatory) {
            this.obligation = obligation;
            this.period = period;
            this.date = date;
            this.mandatory = mandatory;
        }

        public Obligation obligation() {
            return obligation;
        }

        public String period() {
            return period;
        }

        public LocalDate date() {
            return date;
        }

        /** Obrigatória para todo o regime (ou para todo contribuinte com IE); senão, condicional. */
        public boolean mandatory() {
            return mandatory;
        }
    }

    /**
     * Calendário genérico do regime ("LP" ou "LR") na UF: vencimentos que caem no mês, de obrigações vigentes na
     * competência. Prazos da UF ou do município não calculados ficam de fora.
     */
    public List<Due> calendar(YearMonth month, String regime, String uf) {
        List<Due> out = new ArrayList<Due>();
        Set<String> withUfRow = new HashSet<String>();
        for (Obligation o : catalog) {
            if (!o.isGeneric() && o.uf().equals(uf)) {
                withUfRow.add(o.code());
            }
        }
        for (Obligation o : catalog) {
            Obligation.Condition cond = o.conditionFor(regime);
            if (cond == null || (!o.isGeneric() && !o.uf().equals(uf)) || (o.isGeneric() && withUfRow.contains(o.code()))) {
                continue;
            }
            DeadlineRule rule = DeadlineRule.parse(o.deadlineRule());
            if (!rule.computable()) {
                continue;
            }
            List<YearMonth> bases = new ArrayList<YearMonth>();
            if (o.isAnnual()) {
                bases.add(YearMonth.of(month.getYear() - 1, 12));
                bases.add(YearMonth.of(month.getYear(), 12));
            } else {
                for (int back = 0; back <= 12; back++) {
                    bases.add(month.minusMonths(back));
                }
            }
            for (YearMonth base : bases) {
                LocalDate first = o.isAnnual() ? LocalDate.of(base.getYear(), 1, 1) : base.atDay(1);
                Validity v = o.validity();
                if ((v.end() != null && first.isAfter(v.end())) || (v.start() != null && base.atEndOfMonth().isBefore(v.start()))) {
                    continue;
                }
                LocalDate due = rule.dueDate(base);
                if (YearMonth.from(due).equals(month)) {
                    String period = o.isAnnual() ? "Ano-calendário " + base.getYear()
                            : String.format("%02d/%d", base.getMonthValue(), base.getYear());
                    out.add(new Due(o, period, due, cond == Obligation.Condition.SEMPRE || cond == Obligation.Condition.IE));
                }
            }
        }
        out.sort(Comparator.comparing(Due::date));
        return out;
    }

    /** Avaliação de uma obrigação no mês-base (competência, ou dezembro para as anuais). */
    List<Applicability> evaluateOne(Company company, Obligation o, YearMonth base) {
        List<Applicability> out = new ArrayList<Applicability>();
        String period = o.isAnnual() ? "Ano-calendário " + base.getYear()
                : String.format("%02d/%d", base.getMonthValue(), base.getYear());
        LocalDate due = DeadlineRule.parse(o.deadlineRule()).dueDate(base);
        LocalDate first = o.isAnnual() ? LocalDate.of(base.getYear(), 1, 1) : base.atDay(1);
        LocalDate last = base.atEndOfMonth();

        // linhas por UF: a genérica só vale onde não há linha específica
        // linha de UF específica: vale onde a empresa tem IE; sem IE nenhuma, só na UF do endereço
        if (!o.isGeneric() && !hasRegistrationIn(company, o.uf())
                && !(o.uf().equals(company.uf()) && activeIeUfs(company).isEmpty())) {
            return out;
        }
        List<String> genericUfs = activeIeUfs(company);
        genericUfs.removeAll(specificUfs(o.code()));
        Set<String> companyUfs = new HashSet<String>(activeIeUfs(company));
        companyUfs.add(company.uf());
        companyUfs.retainAll(specificUfs(o.code()));
        if (o.isGeneric() && "ESTADUAL".equals(o.sphere()) && genericUfs.isEmpty() && !companyUfs.isEmpty()) {
            return out; // as UFs da empresa têm linha própria
        }

        RegimePeriod rp = company.regimeOn(last);
        String regime = rp == null ? null : regimeCode(rp.regime());
        if (rp == null) {
            out.add(new Applicability(o, Applicability.Status.NAO_SE_APLICA, "Empresa sem regime tributário vigente em "
                    + Validity.br(last) + " (cadastro).", period, null));
            return out;
        }
        if (regime == null) {
            out.add(new Applicability(o, Applicability.Status.NAO_SE_APLICA, "Regime " + rp.regime().label()
                    + ": o catálogo cobre só Lucro Presumido e Lucro Real.", period, null));
            return out;
        }
        Obligation.Condition cond = o.conditionFor(regime);
        String regimeName = rp.regime().label();
        if (cond == null) {
            out.add(new Applicability(o, Applicability.Status.NAO_SE_APLICA, "Não é obrigação do " + regimeName + ".", period, null));
            return out;
        }
        Validity v = o.validity();
        if ((v.end() != null && first.isAfter(v.end())) || (v.start() != null && last.isBefore(v.start()))) {
            String why = v.end() != null && first.isAfter(v.end())
                    ? "Extinta a partir de " + Validity.br(v.end().plusDays(1))
                    + (o.replacedBy().isEmpty() ? "" : "; substituída por " + o.replacedBy()) + "."
                    : "Ainda não existia nesta competência (início " + Validity.br(v.start()) + ").";
            out.add(new Applicability(o, Applicability.Status.FORA_DE_VIGENCIA, why, period, null));
            return out;
        }
        switch (cond) {
            case SEMPRE:
                out.add(new Applicability(o, Applicability.Status.OBRIGATORIA, regimeName + ": obrigatória. "
                        + o.condition(), period, due));
                break;
            case IE: {
                List<String> ufs = o.isGeneric() ? genericUfs : activeIeUfs(company);
                boolean applies = o.isGeneric() ? !ufs.isEmpty() : ufs.contains(o.uf());
                if (applies) {
                    String where = o.isGeneric() ? "inscrição estadual em " + String.join(", ", ufs)
                            : "inscrição estadual em " + o.uf();
                    out.add(new Applicability(o, Applicability.Status.OBRIGATORIA, regimeName + " com " + where + ". "
                            + o.condition(), period, due));
                } else {
                    out.add(new Applicability(o, Applicability.Status.NAO_SE_APLICA, "Sem inscrição estadual ativa"
                            + (o.isGeneric() ? "" : " em " + o.uf()) + " no cadastro.", period, null));
                }
                break;
            }
            case IM:
                if (company.municipalRegistration().isPresent()) {
                    out.add(new Applicability(o, Applicability.Status.A_CONFIRMAR, "Inscrição municipal "
                            + company.municipalRegistration().get() + " (" + company.city() + "/" + company.uf()
                            + "): depende da lei do município. " + o.condition(), period, due));
                } else {
                    out.add(new Applicability(o, Applicability.Status.NAO_SE_APLICA, "Sem inscrição municipal no cadastro.",
                            period, null));
                }
                break;
            default:
                out.add(new Applicability(o, Applicability.Status.A_CONFIRMAR, "Depende de fato que o cadastro não mostra: "
                        + o.condition() + ".", period, due));
        }
        return out;
    }

    /** UFs que têm linha própria para a obrigação. */
    private Set<String> specificUfs(String code) {
        Set<String> ufs = new HashSet<String>();
        for (Obligation x : catalog) {
            if (x.code().equals(code) && !x.isGeneric()) {
                ufs.add(x.uf());
            }
        }
        return ufs;
    }

    private static boolean hasRegistrationIn(Company company, String uf) {
        return activeIeUfs(company).contains(uf);
    }

    static List<String> activeIeUfs(Company company) {
        List<String> out = new ArrayList<String>();
        for (StateRegistration s : company.stateRegistrations()) {
            if (s.situation() == IeSituation.INSCRITA && !out.contains(s.uf())) {
                out.add(s.uf());
            }
        }
        return out;
    }
}
