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

    @Override
    public String toString() {
        return "[" + severity + "] " + ruleId + " — " + message
                + (Fields.NA.equals(suggestion) ? "" : "  → " + suggestion);
    }
}
