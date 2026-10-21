package br.com.facilitasig.fiscal;

/** Espécie do documento. DESCONHECIDO = arquivo que não pôde ser lido como NF-e/NFS-e. */
public enum DocKind {
    NFE("NFE"), NFSE("NFSE"), DESCONHECIDO("?");

    private final String label;

    DocKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
