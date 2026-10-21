package br.com.facilitasig.fiscal;

/** Situação da nota após a validação da importação. */
public enum DocStatus {
    CONFORME("Conforme"), ALERTA("Com alerta"), ERRO("Com erro"), DUPLICADA("Duplicada");

    private final String label;

    DocStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
