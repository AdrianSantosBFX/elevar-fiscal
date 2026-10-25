package br.com.facilitasig.ref;

/** Um código da NCM (capítulo, posição, subposição ou item de 8 dígitos). */
public final class Ncm {

    private final String code;
    private final String formatted;
    private final String description;
    private final Validity validity;
    private final String legalAct;
    private final String dataset;

    Ncm(String code, String formatted, String description, Validity validity, String legalAct, String dataset) {
        this.code = code;
        this.formatted = formatted;
        this.description = description;
        this.validity = validity;
        this.legalAct = legalAct;
        this.dataset = dataset;
    }

    /** Só dígitos (ex.: 01012100). */
    public String code() {
        return code;
    }

    /** Como na tabela oficial (ex.: 0101.21.00). */
    public String formatted() {
        return formatted;
    }

    public String description() {
        return description;
    }

    public Validity validity() {
        return validity;
    }

    public String legalAct() {
        return legalAct;
    }

    public String dataset() {
        return dataset;
    }

    /** Só o código de 8 dígitos pode ser informado no item da NF-e. */
    public boolean isItemCode() {
        return code.length() == 8;
    }

    public String levelName() {
        switch (code.length()) {
            case 2:
                return "Capítulo";
            case 4:
                return "Posição";
            case 5:
            case 6:
                return "Subposição";
            case 7:
                return "Item";
            default:
                return "Subitem (código da nota)";
        }
    }
}
