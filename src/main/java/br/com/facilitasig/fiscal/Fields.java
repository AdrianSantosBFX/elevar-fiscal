package br.com.facilitasig.fiscal;

/** Chaves dos campos de documento e de item. Campo ausente no XML = "NÃO POSSUI". */
public final class Fields {

    public static final String NA = "NÃO POSSUI";

    public static final String[] DOC = {
            "number", "series", "model", "environment", "issued_at", "entry_exit_date", "competence",
            "nat_op", "nature_code", "tp_nf", "id_dest",
            "issuer_doc", "issuer_name", "issuer_uf", "issuer_ibge", "issuer_ie",
            "recipient_doc", "recipient_name", "recipient_uf", "recipient_ibge", "recipient_ie",
            "total_value", "products_value", "discount_value", "freight_value", "insurance_value", "other_value",
            "deson_value", "st_value", "fcpst_value", "ii_value", "ipi_value", "ipi_devol_value",
            "icms_value", "pis_value", "cofins_value", "iss_value",
            "ret_irrf", "ret_csll", "ret_pis", "ret_cofins", "ret_inss", "iss_retained",
            "service_code", "service_desc", "protocol_status", "protocol_number"};

    public static final String[] ITEM = {
            "code", "description", "ncm", "cfop", "cst", "unit", "qty", "unit_value", "value",
            "icms_value", "ipi_value", "pis_value", "cofins_value"};

    private Fields() {
    }
}
