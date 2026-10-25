package br.com.facilitasig.oa;

import br.com.facilitasig.ref.Obligation;

import java.time.LocalDate;

/** Resultado da avaliação de uma obrigação para uma empresa num período. */
public final class Applicability {

    /** Situação da obrigação para a empresa no período. */
    public enum Status {
        OBRIGATORIA("Obrigatória"),
        A_CONFIRMAR("A confirmar"),
        NAO_SE_APLICA("Não se aplica"),
        FORA_DE_VIGENCIA("Fora de vigência");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Obligation obligation;
    private final Status status;
    private final String reason;
    private final String period;
    private final LocalDate dueDate;

    Applicability(Obligation obligation, Status status, String reason, String period, LocalDate dueDate) {
        this.obligation = obligation;
        this.status = status;
        this.reason = reason;
        this.period = period;
        this.dueDate = dueDate;
    }

    public Obligation obligation() {
        return obligation;
    }

    public Status status() {
        return status;
    }

    /** Por que a obrigação está nessa situação (regime, inscrição, condição, vigência). */
    public String reason() {
        return reason;
    }

    /** "10/2026" (mensal) ou "Ano-calendário 2026" (anual). */
    public String period() {
        return period;
    }

    /** Vencimento calculado; nulo quando o prazo é da UF ou do município. */
    public LocalDate dueDate() {
        return dueDate;
    }
}
