package br.com.facilitasig.ref;

/** Município (código IBGE de 7 dígitos), como aparece na tabela de alíquotas do Portal NFS-e. */
public final class Municipality {

    private final int ibge;
    private final String uf;
    private final String name;

    Municipality(int ibge, String uf, String name) {
        this.ibge = ibge;
        this.uf = uf;
        this.name = name;
    }

    public int ibge() {
        return ibge;
    }

    public String ibgeCode() {
        return String.valueOf(ibge);
    }

    public String uf() {
        return uf;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name + " / " + uf + " (" + ibge + ")";
    }
}
