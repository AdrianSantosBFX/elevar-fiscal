package br.com.facilitasig.fiscal;

/** Achado de validação: regra, gravidade, campo, mensagem e sugestão de correção. */
public final class Finding {

    private final String ruleId;
    private final Severity severity;
    private final String field;
    private final String message;
    private final String suggestion;

    public Finding(String ruleId, Severity severity, String field, String message, String suggestion) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.field = field == null || field.isEmpty() ? Fields.NA : field;
        this.message = message;
        this.suggestion = suggestion == null || suggestion.isEmpty() ? Fields.NA : suggestion;
    }

    public String ruleId() { return ruleId; }
    public Severity severity() { return severity; }
    public String field() { return field; }
    public String message() { return message; }
    public String suggestion() { return suggestion; }

    /** Metadados da regra (título, impacto, base legal), vindos do catálogo da versão. */
    public Rule rule() { return RuleCatalog.get(ruleId); }
    public String impacts() { return rule().impacts(); }
    public String basis() { return rule().basis(); }

    /** Texto completo para a tela: mensagem, sugestão, o que afeta e a base legal. */
    public String detail() {
        Rule r = rule();
        StringBuilder sb = new StringBuilder(toString());
        if (!Fields.NA.equals(r.impacts())) {
            sb.append("\n      Afeta: ").append(r.impacts());
        }
        if (!Fields.NA.equals(r.basis())) {
            sb.append("\n      Base: ").append(r.basis());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + ruleId + " — " + message
                + (Fields.NA.equals(suggestion) ? "" : "  → " + suggestion);
    }
}
