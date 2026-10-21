package br.com.facilitasig.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Esquema do banco, em migrações sequenciais (índice + 1 = valor de user_version).
 *
 * <p>Regra "NÃO POSSUI": nenhum campo opcional é NULL nem vazio. Cada dado opcional tem
 * uma coluna de valor e uma coluna {@code _status} (INFORMADO ou NAO_POSSUI); quando o
 * status é NAO_POSSUI o valor gravado é exatamente "NÃO POSSUI". Um CHECK garante isso.
 */
final class Schema {

    private static final String NONE = "NÃO POSSUI";

    private Schema() {
    }

    /** Colunas valor + status para um dado que pode não existir. */
    private static String declared(String name) {
        return name + " TEXT NOT NULL, " + name + "_status TEXT NOT NULL "
                + "CHECK ((" + name + "_status = 'INFORMADO' AND " + name + " <> '' AND " + name + " <> '" + NONE + "') "
                + "OR (" + name + "_status = 'NAO_POSSUI' AND " + name + " = '" + NONE + "'))";
    }

    private static String required(String name) {
        return name + " TEXT NOT NULL CHECK (" + name + " <> '')";
    }

    static String[][] migrations() {
        List<String[]> all = new ArrayList<String[]>();
        all.add(v1());
        all.add(v2());
        return all.toArray(new String[0][]);
    }

    private static String textCols(String[] keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k).append(" TEXT NOT NULL CHECK (").append(k).append(" <> ''), ");
        }
        return sb.toString();
    }

    /** Notas fiscais importadas (NF-e / NFS-e), itens, correções do usuário e achados. */
    private static String[] v2() {
        return new String[]{
                "CREATE TABLE fiscal_doc ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE, "
                        + "direction TEXT NOT NULL CHECK (direction IN ('ENTRADA','SAIDA')), "
                        + "kind TEXT NOT NULL CHECK (kind IN ('NFE','NFSE','DESCONHECIDO')), "
                        + required("access_key") + ", "
                        + "status TEXT NOT NULL CHECK (status IN ('CONFORME','ALERTA','ERRO','DUPLICADA')), "
                        + "duplicate_of INTEGER NOT NULL DEFAULT 0, "
                        + "xml_hash TEXT NOT NULL, source_path TEXT NOT NULL, imported_at TEXT NOT NULL, "
                        + textCols(br.com.facilitasig.fiscal.Fields.DOC)
                        + "xml TEXT NOT NULL)",
                "CREATE UNIQUE INDEX ux_fiscal_doc_key ON fiscal_doc(company_id, direction, access_key) WHERE status <> 'DUPLICADA'",
                "CREATE INDEX ix_fiscal_doc_list ON fiscal_doc(company_id, direction, status)",
                "CREATE TABLE fiscal_doc_item ("
                        + "doc_id INTEGER NOT NULL REFERENCES fiscal_doc(id) ON DELETE CASCADE, seq INTEGER NOT NULL, "
                        + textCols(br.com.facilitasig.fiscal.Fields.ITEM)
                        + "PRIMARY KEY (doc_id, seq))",
                "CREATE TABLE fiscal_doc_override ("
                        + "doc_id INTEGER NOT NULL REFERENCES fiscal_doc(id) ON DELETE CASCADE, "
                        + required("field") + ", " + required("original") + ", " + required("value") + ", "
                        + required("edited_at") + ", PRIMARY KEY (doc_id, field))",
                "CREATE TABLE fiscal_doc_finding ("
                        + "doc_id INTEGER NOT NULL REFERENCES fiscal_doc(id) ON DELETE CASCADE, seq INTEGER NOT NULL, "
                        + required("rule_id") + ", severity TEXT NOT NULL CHECK (severity IN ('ERRO','ALERTA')), "
                        + required("field") + ", " + required("message") + ", " + required("suggestion") + ", "
                        + "PRIMARY KEY (doc_id, seq))"
        };
    }

    private static String[] v1() {
        return new String[]{
                "CREATE TABLE app_setting (key TEXT PRIMARY KEY, value TEXT NOT NULL)",

                "CREATE TABLE company ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "id_type TEXT NOT NULL CHECK (id_type IN ('CNPJ','CPF','CAEPF')), "
                        + required("document") + ", "
                        + "establishment TEXT NOT NULL CHECK (establishment IN ('MATRIZ','FILIAL','NAO_APLICAVEL')), "
                        + required("cnpj_root") + ", "
                        + required("legal_name") + ", "
                        + declared("trade_name") + ", "
                        + required("opening_date") + ", "
                        + required("size") + ", "
                        + required("legal_nature_code") + ", "
                        + required("legal_nature_description") + ", "
                        + required("cad_situation") + ", "
                        + required("cad_situation_date") + ", "
                        + declared("cad_reason") + ", "
                        + declared("special_situation") + ", "
                        + declared("special_situation_date") + ", "
                        + required("street") + ", "
                        + required("number") + ", "
                        + declared("complement") + ", "
                        + required("district") + ", "
                        + required("zip") + ", "
                        + required("city") + ", "
                        + required("ibge_code") + ", "
                        + required("uf") + ", "
                        + declared("email") + ", "
                        + declared("phone") + ", "
                        + declared("efr") + ", "
                        + declared("website") + ", "
                        + declared("company_group") + ", "
                        + declared("nire") + ", "
                        + declared("other_identifiers") + ", "
                        + required("main_cnae_code") + ", "
                        + required("main_cnae_description") + ", "
                        + "secondary_cnae_status TEXT NOT NULL CHECK (secondary_cnae_status IN ('INFORMADO','NAO_POSSUI')), "
                        + declared("municipal_reg") + ", "
                        + declared("municipal_reg_date") + ", "
                        + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, "
                        + "UNIQUE (id_type, document))",

                "CREATE TABLE company_cnae ("
                        + "company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE, "
                        + "seq INTEGER NOT NULL, " + required("code") + ", " + required("description") + ", "
                        + "PRIMARY KEY (company_id, seq))",

                "CREATE TABLE company_state_reg ("
                        + "company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE, "
                        + "uf TEXT NOT NULL, "
                        + "situation TEXT NOT NULL CHECK (situation IN ('INSCRITA','ISENTA','NAO_CONTRIBUINTE')), "
                        + required("number") + ", "
                        + declared("reg_date") + ", "
                        + "PRIMARY KEY (company_id, uf))",

                "CREATE TABLE company_regime ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE, "
                        + required("regime") + ", "
                        + required("start_date") + ", "
                        + declared("end_date") + ", "
                        + declared("note") + ")",

                // no máximo um regime em aberto (sem data final) por empresa
                "CREATE UNIQUE INDEX ux_company_regime_open ON company_regime(company_id) WHERE end_date_status = 'NAO_POSSUI'",
                "CREATE INDEX ix_company_root ON company(cnpj_root)"
        };
    }
}
