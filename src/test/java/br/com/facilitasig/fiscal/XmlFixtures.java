package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NF-e e NFS-e sintéticas e coerentes, para provocar um erro de cada vez. Os totais são
 * calculados a partir dos itens; o teste altera só o que quer tornar errado.
 */
final class XmlFixtures {

    static final String L5 = "41843649000151";
    static final String OTHER = other();

    private XmlFixtures() {
    }

    private static String other() {
        String base = "112223330001";
        return base + BrDocs.cnpjCheckDigits(base);
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    static String pct(String base, String rate) {
        return bd(base).multiply(bd(rate)).divide(bd("100"), 2, RoundingMode.HALF_UP).toPlainString();
    }

    // ================================================================== NF-e

    static final class Item {
        String cfop = "5102";
        String ncm = "12345678";
        String uCom = "UN";
        String uTrib = "UN";
        String qCom = "1.0000";
        String vUnCom = "100.00";
        String qTrib = null;
        String vUnTrib = null;
        String vProd = "100.00";
        String vFrete = null;
        String vSeg = null;
        String vDesc = null;
        String vOutro = null;
        String indTot = "1";
        /** Grupo ICMS completo (ex.: icms00(...)); null = ICMS00 18% sobre vProd. */
        String icms = null;
        String ipi = null;
        String pis = "<PIS><PISNT><CST>07</CST></PISNT></PIS>";
        String cofins = "<COFINS><COFINSNT><CST>07</CST></COFINSNT></COFINS>";
        String extraImposto = "";
        // valores que entram nos totais
        String tBC;
        String tICMS;
        String tBCST = "0.00";
        String tST = "0.00";
        String tIPI = "0.00";
        String tPIS = "0.00";
        String tCOFINS = "0.00";

        Item() {
            tBC = vProd;
            tICMS = pct(vProd, "18");
        }

        Item value(String q, String unit) {
            qCom = q;
            vUnCom = unit;
            vProd = bd(q).multiply(bd(unit)).setScale(2, RoundingMode.HALF_UP).toPlainString();
            tBC = vProd;
            tICMS = pct(vProd, "18");
            return this;
        }

        String xml(int n) {
            String g = icms != null ? icms : icms00(vProd, "18.00", pct(vProd, "18"));
            return "<det nItem=\"" + n + "\"><prod><cProd>" + n + "</cProd><cEAN>SEM GTIN</cEAN><xProd>Produto " + n + "</xProd><NCM>" + ncm
                    + "</NCM><CFOP>" + cfop + "</CFOP><uCom>" + uCom + "</uCom><qCom>" + qCom + "</qCom><vUnCom>" + vUnCom
                    + "</vUnCom><vProd>" + vProd + "</vProd><cEANTrib>SEM GTIN</cEANTrib><uTrib>" + uTrib + "</uTrib><qTrib>"
                    + (qTrib != null ? qTrib : qCom) + "</qTrib><vUnTrib>" + (vUnTrib != null ? vUnTrib : vUnCom) + "</vUnTrib>"
                    + tag("vFrete", vFrete) + tag("vSeg", vSeg) + tag("vDesc", vDesc) + tag("vOutro", vOutro)
                    + "<indTot>" + indTot + "</indTot></prod><imposto><ICMS>" + g + "</ICMS>" + (ipi == null ? "" : ipi) + pis + cofins
                    + extraImposto + "</imposto></det>";
        }
    }

    static String icms00(String vBC, String p, String v) {
        return "<ICMS00><orig>0</orig><CST>00</CST><modBC>3</modBC><vBC>" + vBC + "</vBC><pICMS>" + p + "</pICMS><vICMS>" + v + "</vICMS></ICMS00>";
    }

    static String tag(String name, String v) {
        return v == null ? "" : "<" + name + ">" + v + "</" + name + ">";
    }

    static final class Nfe {
        int number = 100;
        String dhEmi = "2026-07-10T10:00:00-03:00";
        String dhSaiEnt = null;
        String tpNF = "1";
        String idDest = "1";
        String finNFe = "1";
        String indFinal = "0";
        String indPres = "9";
        String crt = "3";
        String emitDoc = L5;
        String emitUf = "MG";
        String emitMun = "3170701";
        String dest = "<CNPJ>" + OTHER + "</CNPJ>";
        String destUf = "MG";
        String destMun = "3170701";
        String destCPais = null;
        String indIEDest = "1";
        String destIE = "0012345678";
        String nfRef = "";
        String transp = "<transp><modFrete>9</modFrete></transp>";
        String pag = "";
        String cobr = "";
        String infAdic = "";
        String issqnTot = "";
        final List<Item> items = new ArrayList<Item>();
        /** Totais calculados que o teste quer sobrescrever. */
        final Map<String, String> totalOverride = new LinkedHashMap<String, String>();

        Nfe() {
            items.add(new Item());
        }

        Item item() {
            return items.get(0);
        }

        String key() {
            String k = "3126" + "07" + emitDoc + "55" + "001" + String.format("%09d", number) + "1" + "12345678";
            return k + DocValidator.keyCheckDigit(k);
        }

        String xml() {
            Map<String, BigDecimal> t = new LinkedHashMap<String, BigDecimal>();
            for (String k : new String[]{"vBC", "vICMS", "vICMSDeson", "vFCP", "vBCST", "vST", "vFCPST", "vFCPSTRet", "vProd", "vFrete",
                    "vSeg", "vDesc", "vII", "vIPI", "vIPIDevol", "vPIS", "vCOFINS", "vOutro"}) {
                t.put(k, BigDecimal.ZERO);
            }
            StringBuilder dets = new StringBuilder();
            int n = 0;
            for (Item it : items) {
                dets.append(it.xml(++n));
                t.put("vBC", t.get("vBC").add(bd(it.tBC)));
                t.put("vICMS", t.get("vICMS").add(bd(it.tICMS)));
                t.put("vBCST", t.get("vBCST").add(bd(it.tBCST)));
                t.put("vST", t.get("vST").add(bd(it.tST)));
                if ("1".equals(it.indTot)) {
                    t.put("vProd", t.get("vProd").add(bd(it.vProd)));
                }
                t.put("vFrete", t.get("vFrete").add(z(it.vFrete)));
                t.put("vSeg", t.get("vSeg").add(z(it.vSeg)));
                t.put("vDesc", t.get("vDesc").add(z(it.vDesc)));
                t.put("vOutro", t.get("vOutro").add(z(it.vOutro)));
                t.put("vIPI", t.get("vIPI").add(bd(it.tIPI)));
                t.put("vPIS", t.get("vPIS").add(bd(it.tPIS)));
                t.put("vCOFINS", t.get("vCOFINS").add(bd(it.tCOFINS)));
            }
            BigDecimal vNF = t.get("vProd").subtract(t.get("vDesc")).add(t.get("vST")).add(t.get("vFrete")).add(t.get("vSeg"))
                    .add(t.get("vOutro")).add(t.get("vIPI"));
            StringBuilder tot = new StringBuilder("<ICMSTot>");
            for (Map.Entry<String, BigDecimal> e : t.entrySet()) {
                String v = totalOverride.containsKey(e.getKey()) ? totalOverride.get(e.getKey()) : e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
                tot.append(tag(e.getKey(), v));
            }
            tot.append(tag("vNF", totalOverride.containsKey("vNF") ? totalOverride.get("vNF") : vNF.setScale(2, RoundingMode.HALF_UP).toPlainString()));
            tot.append("</ICMSTot>");
            String k = key();
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                    + "<NFe><infNFe Id=\"NFe" + k + "\" versao=\"4.00\"><ide><cUF>31</cUF><natOp>Venda</natOp><mod>55</mod><serie>1</serie>"
                    + "<nNF>" + number + "</nNF><dhEmi>" + dhEmi + "</dhEmi>" + tag("dhSaiEnt", dhSaiEnt) + "<tpNF>" + tpNF + "</tpNF><idDest>"
                    + idDest + "</idDest><tpAmb>1</tpAmb><finNFe>" + finNFe + "</finNFe><indFinal>" + indFinal + "</indFinal><indPres>"
                    + indPres + "</indPres>" + nfRef + "</ide>"
                    + "<emit><CNPJ>" + emitDoc + "</CNPJ><xNome>EMITENTE LTDA</xNome><enderEmit><cMun>" + emitMun + "</cMun><UF>" + emitUf
                    + "</UF></enderEmit><IE>0044543260066</IE><CRT>" + crt + "</CRT></emit>"
                    + "<dest>" + dest + "<xNome>DESTINATARIO</xNome><enderDest><cMun>" + destMun + "</cMun><UF>" + destUf + "</UF>"
                    + tag("cPais", destCPais) + "</enderDest><indIEDest>" + indIEDest + "</indIEDest>" + tag("IE", destIE) + "</dest>"
                    + dets + "<total>" + tot + issqnTot + "</total>" + transp + cobr + pag + infAdic + "</infNFe></NFe>"
                    + "<protNFe><infProt><chNFe>" + k + "</chNFe><cStat>100</cStat><nProt>131000000000001</nProt></infProt></protNFe></nfeProc>";
        }

        private static BigDecimal z(String v) {
            return v == null ? BigDecimal.ZERO : bd(v);
        }
    }

    // ================================================================== NFS-e nacional

    static final class Nfse {
        String provider = OTHER;
        String taker = "<CNPJ>" + L5 + "</CNPJ>";
        String takerEnd = "<end><endNac><cMun>3170701</cMun></endNac></end>";
        String number = "77";
        String dhEmi = "2026-09-03T09:00:00-03:00";
        String dCompet = "2026-09-03";
        String opSimpNac = "1";
        String vServ = "1000.00";
        String descontos = "";
        String tribISSQN = "1";
        String tpImunidade = "";
        String tpRetISSQN = "1";
        String pAliq = "2.00";
        String tribFed = "";
        // infNFSe/valores
        String vBC = "1000.00";
        String pAliqAplic = "2.00";
        String vISSQN = "20.00";
        String vTotalRet = null;
        String vLiq = "1000.00";

        String xml() {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.00\">"
                    + "<infNFSe Id=\"NFS3170701" + provider + number + "\"><xTribNac>Serviços</xTribNac><nNFSe>" + number + "</nNFSe><cStat>100</cStat>"
                    + "<emit><CNPJ>" + provider + "</CNPJ><xNome>PRESTADOR LTDA</xNome><enderNac><cMun>3550308</cMun><UF>SP</UF></enderNac></emit>"
                    + "<valores>" + tag("vBC", vBC) + tag("pAliqAplic", pAliqAplic) + tag("vISSQN", vISSQN) + tag("vTotalRet", vTotalRet)
                    + tag("vLiq", vLiq) + "</valores>"
                    + "<DPS versao=\"1.00\"><infDPS><tpAmb>1</tpAmb><dhEmi>" + dhEmi + "</dhEmi><serie>1</serie><nDPS>" + number + "</nDPS>"
                    + "<dCompet>" + dCompet + "</dCompet><prest><CNPJ>" + provider + "</CNPJ><regTrib><opSimpNac>" + opSimpNac
                    + "</opSimpNac></regTrib></prest>"
                    + "<toma>" + taker + "<xNome>TOMADOR</xNome>" + takerEnd + "</toma>"
                    + "<serv><cServ><cTribNac>171901</cTribNac><xDescServ>Serviço</xDescServ></cServ></serv>"
                    + "<valores><vServPrest><vServ>" + vServ + "</vServ></vServPrest>" + descontos
                    + "<trib><tribMun><tribISSQN>" + tribISSQN + "</tribISSQN>" + tpImunidade + tag("pAliq", pAliq)
                    + "<tpRetISSQN>" + tpRetISSQN + "</tpRetISSQN></tribMun>" + tribFed + "</trib></valores>"
                    + "</infDPS></DPS></infNFSe></NFSe>";
        }
    }
}
