package br.com.facilitasig.ref;

/**
 * Resultado de uma consulta a tabela de referência. Só {@link #ENCONTRADO} confirma o código;
 * {@link #SEM_COBERTURA} significa que a tabela não permite afirmar nada (vira INDETERMINADO
 * nas regras): falta de dado nunca vira "conforme".
 */
public enum LookupStatus {
    ENCONTRADO("Encontrado e vigente na data"),
    FORA_DE_VIGENCIA("Código existe, mas não estava vigente na data"),
    NAO_ENCONTRADO("Código inexistente na tabela"),
    SEM_COBERTURA("A tabela não cobre esta consulta (indeterminado)");

    private final String label;

    LookupStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
