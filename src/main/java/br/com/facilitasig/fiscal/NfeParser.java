package br.com.facilitasig.fiscal;

import org.w3c.dom.Element;

import java.util.Map;

/** Leitor de NF-e (modelo 55) autorizada (nfeProc) ou apenas assinada (NFe). */
final class NfeParser {

    private NfeParser() {
    }

    static FiscalDoc parse(Element root) {
        Element nfe = "NFe".equals(X.name(root)) ? root : X.child(root, "NFe");
        Element inf = X.child(nfe, "infNFe");
        if (inf == null) {
            throw new IllegalArgumentException("Elemento infNFe não encontrado.");
        }
        FiscalDoc d = new FiscalDoc();
        d.setKind(DocKind.NFE);
        String id = inf.getAttribute("Id");
        d.setAccessKey(id.startsWith("NFe") ? id.substring(3) : id);

        Element ide = X.child(inf, "ide");
        d.put("number", X.text(ide, "nNF"));
        d.put("series", X.text(ide, "serie"));
        d.put("model", X.text(ide, "mod"));
        d.put("environment", X.text(ide, "tpAmb"));
        String emi = X.text(ide, "dhEmi");
        d.put("issued_at", Fields.NA.equals(emi) ? X.text(ide, "dEmi") : emi);
        String sai = X.text(ide, "dhSaiEnt");
        d.put("entry_exit_date", Fields.NA.equals(sai) ? X.text(ide, "dSaiEnt") : sai);
        d.put("nat_op", X.text(ide, "natOp"));
        d.put("tp_nf", X.text(ide, "tpNF"));
        d.put("id_dest", X.text(ide, "idDest"));

        Element emit = X.child(inf, "emit");
        d.put("issuer_doc", first(X.text(emit, "CNPJ"), X.text(emit, "CPF")));
        d.put("issuer_name", X.text(emit, "xNome"));
        d.put("issuer_uf", X.text(emit, "enderEmit", "UF"));
        d.put("issuer_ibge", X.text(emit, "enderEmit", "cMun"));
        d.put("issuer_ie", X.text(emit, "IE"));

        Element dest = X.child(inf, "dest");
        d.put("recipient_doc", first(first(X.text(dest, "CNPJ"), X.text(dest, "CPF")), X.text(dest, "idEstrangeiro")));
        d.put("recipient_name", X.text(dest, "xNome"));
        d.put("recipient_uf", X.text(dest, "enderDest", "UF"));
        d.put("recipient_ibge", X.text(dest, "enderDest", "cMun"));
        d.put("recipient_ie", X.text(dest, "IE"));

        Element tot = X.path(inf, "total", "ICMSTot");
        d.put("total_value", X.text(tot, "vNF"));
        d.put("products_value", X.text(tot, "vProd"));
        d.put("discount_value", X.text(tot, "vDesc"));
        d.put("freight_value", X.text(tot, "vFrete"));
        d.put("insurance_value", X.text(tot, "vSeg"));
        d.put("other_value", X.text(tot, "vOutro"));
        d.put("deson_value", X.text(tot, "vICMSDeson"));
        d.put("st_value", X.text(tot, "vST"));
        d.put("fcpst_value", X.text(tot, "vFCPST"));
        d.put("ii_value", X.text(tot, "vII"));
        d.put("ipi_value", X.text(tot, "vIPI"));
        d.put("ipi_devol_value", X.text(tot, "vIPIDevol"));
        d.put("icms_value", X.text(tot, "vICMS"));
        d.put("pis_value", X.text(tot, "vPIS"));
        d.put("cofins_value", X.text(tot, "vCOFINS"));
        Element ret = X.path(inf, "total", "retTrib");
        d.put("ret_pis", X.text(ret, "vRetPIS"));
        d.put("ret_cofins", X.text(ret, "vRetCOFINS"));
        d.put("ret_csll", X.text(ret, "vRetCSLL"));
        d.put("ret_irrf", X.text(ret, "vIRRF"));
        d.put("ret_inss", X.text(ret, "vRetPrev"));

        Element prot = X.path(root, "protNFe", "infProt");
        d.put("protocol_status", X.text(prot, "cStat"));
        d.put("protocol_number", X.text(prot, "nProt"));

        for (Element det : X.children(inf, "det")) {
            Map<String, String> it = FiscalDoc.newItem();
            Element prod = X.child(det, "prod");
            put(it, "code", X.text(prod, "cProd"));
            put(it, "description", X.text(prod, "xProd"));
            put(it, "ncm", X.text(prod, "NCM"));
            put(it, "cfop", X.text(prod, "CFOP"));
            put(it, "unit", X.text(prod, "uCom"));
            put(it, "qty", X.text(prod, "qCom"));
            put(it, "unit_value", X.text(prod, "vUnCom"));
            put(it, "value", X.text(prod, "vProd"));
            Element imp = X.child(det, "imposto");
            Element icms = X.first(X.child(imp, "ICMS"));
            String cst = X.text(icms, "CST");
            put(it, "cst", Fields.NA.equals(cst) ? X.text(icms, "CSOSN") : cst);
            put(it, "icms_value", X.text(icms, "vICMS"));
            put(it, "ipi_value", X.text(imp, "IPI", "IPITrib", "vIPI"));
            put(it, "pis_value", X.text(X.first(X.child(imp, "PIS")), "vPIS"));
            put(it, "cofins_value", X.text(X.first(X.child(imp, "COFINS")), "vCOFINS"));
            d.items().add(it);
        }
        return d;
    }

    private static void put(Map<String, String> m, String k, String v) {
        m.put(k, v == null || v.isEmpty() ? Fields.NA : v);
    }

    private static String first(String a, String b) {
        return Fields.NA.equals(a) ? b : a;
    }
}
