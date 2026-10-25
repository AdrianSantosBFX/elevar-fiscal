package br.com.facilitasig.ref;

/** Tabelas de códigos de situação tributária e onde cada uma aparece na NF-e. */
public enum CstTable {
    ORIGEM("Origem da mercadoria", "ICMS: orig"),
    CST_ICMS("CST do ICMS (regime normal)", "ICMS: CST"),
    CSOSN("CSOSN (Simples Nacional)", "ICMS: CSOSN"),
    CST_IPI("CST do IPI", "IPI: CST"),
    CST_PIS_COFINS("CST do PIS/Pasep e da Cofins", "PIS e COFINS: CST");

    private final String label;
    private final String xmlField;

    CstTable(String label, String xmlField) {
        this.label = label;
        this.xmlField = xmlField;
    }

    public String label() {
        return label;
    }

    /** Campo da NF-e que usa a tabela. */
    public String xmlField() {
        return xmlField;
    }

    /** Códigos numéricos de largura fixa: Origem 1 dígito, CSOSN 3, demais 2. */
    public String normalize(String code) {
        String digits = code == null ? "" : code.trim();
        int width = this == ORIGEM ? 1 : this == CSOSN ? 3 : 2;
        if (!digits.matches("\\d+") || digits.length() > width) {
            return digits;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = digits.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(digits).toString();
    }

    @Override
    public String toString() {
        return label;
    }
}
