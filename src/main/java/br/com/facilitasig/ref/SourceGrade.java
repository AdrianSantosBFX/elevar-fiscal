package br.com.facilitasig.ref;

/** Grau da fonte de uma tabela de referência. Regras futuras limitam a severidade conforme o grau. */
public enum SourceGrade {
    OFICIAL("Oficial", "Publicada pelo órgão competente"),
    SECUNDARIA("Secundária", "Publicada por terceiros; usar com cautela (achado no máximo ALERTA)"),
    CURADORIA("Curadoria", "Transcrita da norma pela equipe; conferir itens marcados [validar]");

    private final String label;
    private final String meaning;

    SourceGrade(String label, String meaning) {
        this.label = label;
        this.meaning = meaning;
    }

    public String label() {
        return label;
    }

    public String meaning() {
        return meaning;
    }
}
