package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * Leitor de NFS-e no padrão nacional (NFSe/infNFSe/DPS/infDPS). Baseado no leiaute nacional
 * versão 1.00; deve ser conferido com XMLs reais emitidos no Emissor Nacional.
 */
final class NfseNacionalParser {

    private NfseNacionalParser() {
    }

    static FiscalDoc parse(Element root) {
        Element inf = X.child(root, "infNFSe");
        if (inf == null) {
            throw new IllegalArgumentException("Elemento infNFSe não encontrado.");
        }
        Element dps = X.path(inf, "DPS", "infDPS");
        if (dps == null) {
            throw new IllegalArgumentException("DPS (infDPS) não encontrada dentro da NFS-e.");
        }
        FiscalDoc d = new FiscalDoc();
        d.setKind(DocKind.NFSE);
        String id = inf.getAttribute("Id");
        d.setAccessKey(id.startsWith("NFS") ? id.substring(3) : id);

        d.put("number", X.text(inf, "nNFSe"));
        d.put("series", X.text(dps, "serie"));
        d.put("model", "NFSE-NACIONAL");
        d.put("environment", X.text(dps, "tpAmb"));
        d.put("issued_at", X.text(dps, "dhEmi"));
        d.put("competence", X.text(dps, "dCompet"));
        d.put("protocol_status", X.text(inf, "cStat"));

        Element emit = X.child(inf, "emit");
        d.put("issuer_doc", first(X.text(emit, "CNPJ"), X.text(emit, "CPF")));
        d.put("issuer_name", X.text(emit, "xNome"));
        d.put("issuer_uf", X.text(emit, "enderNac", "UF"));
        d.put("issuer_ibge", X.text(emit, "enderNac", "cMun"));

        Element toma = X.child(dps, "toma");
        d.put("recipient_doc", first(first(X.text(toma, "CNPJ"), X.text(toma, "CPF")), X.text(toma, "NIF")));
        d.put("recipient_name", X.text(toma, "xNome"));
        String cMun = X.text(toma, "end", "endNac", "cMun");
        d.put("recipient_ibge", cMun);
        if (!Fields.NA.equals(cMun)) {
            d.put("recipient_uf", BrDocs.ufFromIbge(cMun));
        }

        d.put("service_code", X.text(dps, "serv", "cServ", "cTribNac"));
        d.put("service_desc", X.text(dps, "serv", "cServ", "xDescServ"));
        String xTrib = X.text(inf, "xTribNac");
        d.put("nat_op", Fields.NA.equals(xTrib) ? d.original("service_desc") : xTrib);

        Element val = X.child(dps, "valores");
        d.put("total_value", X.text(val, "vServPrest", "vServ"));
        d.put("iss_value", X.text(inf, "valores", "vISSQN"));
        String tpRet = X.text(val, "trib", "tribMun", "tpRetISSQN");
        if ("1".equals(tpRet)) {
            d.put("iss_retained", "NÃO");
        } else if ("2".equals(tpRet) || "3".equals(tpRet)) {
            d.put("iss_retained", "SIM");
        }
        Element fed = X.path(val, "trib", "tribFed");
        Element pc = X.child(fed, "piscofins");
        if ("1".equals(X.text(pc, "tpRetPisCofins"))) {
            d.put("ret_pis", X.text(pc, "vPis"));
            d.put("ret_cofins", X.text(pc, "vCofins"));
        } else {
            d.put("pis_value", X.text(pc, "vPis"));
            d.put("cofins_value", X.text(pc, "vCofins"));
        }
        d.put("ret_inss", X.text(fed, "vRetCP"));
        d.put("ret_irrf", X.text(fed, "vRetIRRF"));
        d.put("ret_csll", X.text(fed, "vRetCSLL"));

        Map<String, String> it = FiscalDoc.newItem();
        it.put("code", d.original("service_code"));
        it.put("description", d.original("service_desc"));
        it.put("value", d.original("total_value"));
        it.put("unit_value", d.original("total_value"));
        it.put("qty", "1");
        d.items().add(it);
        return d;
    }

    private static String first(String a, String b) {
        return Fields.NA.equals(a) ? b : a;
    }
}
