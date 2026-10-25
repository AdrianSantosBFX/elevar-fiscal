package br.com.facilitasig.ref;

/**
 * Esquema do banco de referência ({@code elevar-fiscal-ref.db}), em migrações sequenciais como o
 * banco principal. Guarda só tabelas oficiais ou curadas; nenhum dado de empresa.
 *
 * <p>Datas em texto ISO (aaaa-mm-dd). {@code vig_ini} nulo = início não registrado (vale desde
 * sempre para a tabela); {@code vig_fim} nulo = vigente, sem fim.
 */
final class RefSchema {

    private RefSchema() {
    }

    static String[][] migrations() {
        return new String[][]{v1(), v2(), v3()};
    }

    /**
     * v0.1.4: alíquotas de ICMS (interestaduais e internas), lista da LC 116, municípios e alíquotas de ISS
     * do Portal NFS-e. A tabela de ISS (1,9 milhão de linhas) usa chaves inteiras e WITHOUT ROWID para
     * caber em poucas dezenas de MB; datas como aaaammdd e alíquotas em centésimos de ponto percentual
     * (2,5% = 250); alíquota nula = o Portal publicou a vigência sem alíquota. {@code origin} distingue tabela embutida no sistema de arquivo importado pelo usuário.
     */
    /** v0.1.5: catálogo de obrigações acessórias (Lucro Presumido e Lucro Real). */
    static String[] v3() {
        return new String[]{
                "CREATE TABLE ref_obrigacao ("
                        + "codigo TEXT NOT NULL CHECK (codigo <> ''), "
                        + "uf TEXT NOT NULL CHECK (uf = '*' OR length(uf) = 2), "
                        + "nome TEXT NOT NULL CHECK (nome <> ''), "
                        + "esfera TEXT NOT NULL CHECK (esfera IN ('FEDERAL','ESTADUAL','MUNICIPAL')), "
                        + "regimes TEXT NOT NULL CHECK (regimes <> ''), "
                        + "periodicidade TEXT NOT NULL CHECK (periodicidade IN ('MENSAL','ANUAL')), "
                        + "prazo_regra TEXT NOT NULL CHECK (prazo_regra <> ''), "
                        + "prazo_texto TEXT NOT NULL, "
                        + "condicao TEXT NOT NULL, "
                        + "derivavel TEXT NOT NULL CHECK (derivavel IN ('SIM','PARCIAL','NAO')), "
                        + "notas_sustentam TEXT, "
                        + "base_legal TEXT NOT NULL CHECK (base_legal <> ''), "
                        + "vig_ini TEXT, vig_fim TEXT, "
                        + "substituida_por TEXT, "
                        + "observacao TEXT, "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE, "
                        + "PRIMARY KEY (codigo, uf))"
        };
    }

    static String[] v2() {
        return new String[]{
                "ALTER TABLE ref_dataset ADD COLUMN origin TEXT NOT NULL DEFAULT 'EMBUTIDO' "
                        + "CHECK (origin IN ('EMBUTIDO','IMPORTADO'))",
                "CREATE TABLE ref_icms_inter ("
                        + "id INTEGER PRIMARY KEY, "
                        + "uf_origem TEXT NOT NULL CHECK (length(uf_origem) = 2), "
                        + "uf_destino TEXT NOT NULL CHECK (length(uf_destino) = 2 AND uf_destino <> uf_origem), "
                        + "tipo TEXT NOT NULL CHECK (tipo IN ('GERAL','IMPORTADO')), "
                        + "aliq_x100 INTEGER NOT NULL CHECK (aliq_x100 > 0), "
                        + "vig_ini TEXT, vig_fim TEXT, "
                        + "legal_basis TEXT NOT NULL CHECK (legal_basis <> ''), "
                        + "note TEXT, "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE, "
                        + "UNIQUE (uf_origem, uf_destino, tipo, vig_ini))",
                "CREATE INDEX ix_ref_icms_inter_dataset ON ref_icms_inter(dataset)",
                "CREATE TABLE ref_icms_interna ("
                        + "id INTEGER PRIMARY KEY, "
                        + "uf TEXT NOT NULL CHECK (length(uf) = 2), "
                        + "aliq_x100 INTEGER NOT NULL CHECK (aliq_x100 > 0), "
                        + "fcp_x100 INTEGER, "
                        + "vig_ini TEXT, vig_fim TEXT, "
                        + "legal_basis TEXT NOT NULL CHECK (legal_basis <> ''), "
                        + "note TEXT, "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE, "
                        + "UNIQUE (uf, vig_ini))",
                "CREATE INDEX ix_ref_icms_interna_dataset ON ref_icms_interna(dataset)",
                "CREATE TABLE ref_lc116 ("
                        + "subitem TEXT PRIMARY KEY CHECK (subitem GLOB '[0-9][0-9].[0-9][0-9]'), "
                        + "description TEXT NOT NULL CHECK (description <> ''), "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE)",
                "CREATE INDEX ix_ref_lc116_dataset ON ref_lc116(dataset)",
                "CREATE TABLE ref_municipio ("
                        + "ibge INTEGER PRIMARY KEY CHECK (ibge BETWEEN 1100000 AND 5399999), "
                        + "uf TEXT NOT NULL CHECK (length(uf) = 2), "
                        + "name TEXT NOT NULL CHECK (name <> ''), "
                        + "search_text TEXT NOT NULL, "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE)",
                "CREATE INDEX ix_ref_municipio_dataset ON ref_municipio(dataset)",
                "CREATE TABLE ref_iss_aliquota ("
                        + "ibge INTEGER NOT NULL, "
                        + "servico INTEGER NOT NULL, "
                        + "vig_ini INTEGER NOT NULL, "
                        + "vig_fim INTEGER, "
                        + "aliq_x100 INTEGER, "
                        + "PRIMARY KEY (ibge, servico, vig_ini)) WITHOUT ROWID"
        };
    }

    static String[] v1() {
        return new String[]{
                "CREATE TABLE ref_dataset ("
                        + "code TEXT PRIMARY KEY CHECK (code <> ''), "
                        + "kind TEXT NOT NULL CHECK (kind <> ''), "
                        + "title TEXT NOT NULL CHECK (title <> ''), "
                        + "grade TEXT NOT NULL CHECK (grade IN ('OFICIAL','SECUNDARIA','CURADORIA')), "
                        + "source TEXT NOT NULL CHECK (source <> ''), "
                        + "source_date TEXT, "
                        + "license TEXT, "
                        + "coverage_from TEXT, "
                        + "conclusive_from TEXT, "
                        + "notes TEXT, "
                        + "file TEXT NOT NULL, "
                        + "sha256 TEXT NOT NULL CHECK (length(sha256) = 64), "
                        + "row_count INTEGER NOT NULL, "
                        + "loaded_at TEXT NOT NULL)",
                "CREATE TABLE ref_ncm ("
                        + "id INTEGER PRIMARY KEY, "
                        + "code TEXT NOT NULL CHECK (code GLOB '[0-9]*' AND length(code) BETWEEN 2 AND 8), "
                        + "formatted TEXT NOT NULL, "
                        + "description TEXT NOT NULL CHECK (description <> ''), "
                        + "search_text TEXT NOT NULL, "
                        + "vig_ini TEXT, "
                        + "vig_fim TEXT, "
                        + "legal_act TEXT, "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE, "
                        + "UNIQUE (code, vig_ini))",
                "CREATE INDEX ix_ref_ncm_code ON ref_ncm(code)",
                "CREATE INDEX ix_ref_ncm_dataset ON ref_ncm(dataset)",
                "CREATE TABLE ref_cst ("
                        + "id INTEGER PRIMARY KEY, "
                        + "tbl TEXT NOT NULL CHECK (tbl IN ('ORIGEM','CST_ICMS','CSOSN','CST_IPI','CST_PIS_COFINS')), "
                        + "code TEXT NOT NULL CHECK (code <> ''), "
                        + "description TEXT NOT NULL CHECK (description <> ''), "
                        + "vig_ini TEXT, "
                        + "vig_fim TEXT, "
                        + "legal_basis TEXT NOT NULL CHECK (legal_basis <> ''), "
                        + "note TEXT, "
                        + "dataset TEXT NOT NULL REFERENCES ref_dataset(code) ON DELETE CASCADE, "
                        + "UNIQUE (tbl, code, vig_ini))",
                "CREATE INDEX ix_ref_cst_dataset ON ref_cst(dataset)"
        };
    }
}
