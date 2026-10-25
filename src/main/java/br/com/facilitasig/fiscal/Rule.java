package br.com.facilitasig.fiscal;

/**
 * Metadados de uma verificação: o que confere, o que afeta (guias e obrigações) e a base legal.
 * O achado ({@link Finding}) guarda só o id da regra; título, impacto e base vêm daqui, para que
 * a explicação acompanhe a versão do sistema.
 */
public final class Rule {

    private final String id;
    private final String title;
    private final String impacts;
    private final String basis;
    private final String catalogRef;

    public Rule(String id, String title, String impacts, String basis, String catalogRef) {
        this.id = id;
        this.title = title;
        this.impacts = impacts;
        this.basis = basis;
        this.catalogRef = catalogRef;
    }

    public String id() { return id; }
    public String title() { return title; }
    /** Tributos e obrigações afetados (ex.: "ICMS; EFD ICMS/IPI"). */
    public String impacts() { return impacts; }
    /** Base legal ou normativa; trechos marcados [validar] ainda dependem de conferência. */
    public String basis() { return basis; }
    /** Item do catálogo de erros (erros-nfe-nfse-v0.0.2) ou regra anterior equivalente. */
    public String catalogRef() { return catalogRef; }
}
