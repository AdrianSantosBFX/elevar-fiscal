package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.company.TaxRegime;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Verificações da NFS-e no padrão nacional (NFSe/infNFSe/DPS/infDPS) que dependem apenas do XML.
 * Os nomes de campos seguem o leiaute nacional v1.00 e ainda precisam ser conferidos com XMLs
 * reais; por isso todas as verificações daqui geram ALERTA, nunca ERRO.
 */
final class NfseXmlChecks {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    private NfseXmlChecks() {
    }

    static void run(FiscalDoc d, Element root, Company company, List<Finding> out) {
        Element inf = X.child(root, "infNFSe");
        Element dps = X.path(inf, "DPS", "infDPS");
        if (inf == null || dps == null) {
            return;
        }
        Element val = X.child(dps, "valores");
        Element tribMun = X.path(val, "trib", "tribMun");
        Element tribFed = X.path(val, "trib", "tribFed");
        Element pc = X.child(tribFed, "piscofins");
        Element nval = X.child(inf, "valores");
        Element toma = X.child(dps, "toma");

        // ERR-3.4 — competência × emissão
        LocalDate em = DocValidator.date(X.text(dps, "dhEmi"));
        LocalDate comp = DocValidator.date(X.text(dps, "dCompet"));
        if (em != null && comp != null) {
            if (comp.isAfter(em)) {
                out.add(new Finding("ERR-3.4", Severity.ALERTA, "competence", "Competência (" + comp + ") posterior à emissão (" + em + ").",
                        "Conferir a data de competência da prestação."));
            } else if (comp.getYear() != em.getYear() || comp.getMonthValue() != em.getMonthValue()) {
                out.add(new Finding("ERR-3.4", Severity.ALERTA, "competence", "Competência " + month(comp) + " diferente do mês de emissão "
                        + month(em) + ": ISS, receita e retenções seguem a competência.", "Apurar a nota na competência " + month(comp) + "."));
            }
        }

        BigDecimal vServ = Num.get(val, "vServPrest", "vServ");
        BigDecimal vDescIncond = Num.z(val, "vDescCondIncond", "vDescIncond");
        BigDecimal vDescCond = Num.z(val, "vDescCondIncond", "vDescCond");
        BigDecimal vBC = Num.get(nval, "vBC");
        BigDecimal pAliq = Num.get(nval, "pAliqAplic");
        if (pAliq == null) {
            pAliq = Num.get(tribMun, "pAliq");
        }
        BigDecimal vISS = Num.get(nval, "vISSQN");

        // ERR-12.1 — ISS = base × alíquota
        if (vBC != null && pAliq != null && vISS != null && Num.differ(Num.pct(vBC, pAliq), vISS, Num.CENT)) {
            out.add(new Finding("ERR-12.1", Severity.ALERTA, "iss_value", "ISS " + Num.fmt(vISS) + " ≠ base " + Num.fmt(vBC) + " × "
                    + Num.fmt(pAliq) + "% = " + Num.fmt(Num.pct(vBC, pAliq)) + ".", "Conferir base, alíquota e valor do ISS."));
        }

        // ERR-12.2 — base = serviço − desconto incondicional − deduções − benefício
        if (vBC != null && vServ != null) {
            BigDecimal expected = vServ.subtract(vDescIncond).subtract(Num.z(nval, "vCalcDR")).subtract(Num.z(nval, "vCalcBM"));
            if (Num.differ(expected, vBC, Num.TWO_CENTS)) {
                boolean condReduced = vDescCond.signum() > 0 && !Num.differ(expected.subtract(vDescCond), vBC, Num.TWO_CENTS);
                out.add(new Finding("ERR-12.2", Severity.ALERTA, "iss_value", condReduced
                        ? "Desconto condicional (" + Num.fmt(vDescCond) + ") reduziu a base do ISS (base " + Num.fmt(vBC) + "; esperada " + Num.fmt(expected) + ")."
                        : "Base do ISS " + Num.fmt(vBC) + " ≠ serviço − desconto incondicional − deduções = " + Num.fmt(expected) + ".",
                        "Desconto condicional não reduz a base do ISS; conferir deduções e benefícios [validar lei municipal]."));
            }
        }

        // ERR-12.5 — tributação do ISSQN
        String trib = X.text(tribMun, "tribISSQN");
        boolean foreignTaker = toma != null && (X.child(toma, "NIF") != null || X.child(toma, "cNaoNIF") != null
                || X.path(toma, "end", "endExt") != null);
        if ("2".equals(trib) && Fields.NA.equals(X.text(tribMun, "tpImunidade"))) {
            out.add(new Finding("ERR-12.5", Severity.ALERTA, "service_code", "ISS marcado como imune (tribISSQN = 2) sem o tipo de imunidade.",
                    "Informar a imunidade (tpImunidade) e guardar a comprovação."));
        }
        if ("3".equals(trib) && !foreignTaker) {
            out.add(new Finding("ERR-12.5", Severity.ALERTA, "service_code", "Exportação de serviço (tribISSQN = 3) com tomador no Brasil.",
                    "Exportação exige tomador no exterior e resultado lá verificado (LC 116/2003, art. 2º)."));
        }
        if (("2".equals(trib) || "3".equals(trib) || "4".equals(trib)) && Num.positive(vISS)) {
            out.add(new Finding("ERR-12.5", Severity.ALERTA, "iss_value", "tribISSQN = " + trib + " (sem ISS), mas há ISS de " + Num.fmt(vISS) + ".",
                    "Conferir a tributação do serviço."));
        }

        // Retenções federais
        boolean pcRetained = "1".equals(X.text(pc, "tpRetPisCofins"));
        BigDecimal rPis = pcRetained ? Num.z(pc, "vPis") : BigDecimal.ZERO;
        BigDecimal rCof = pcRetained ? Num.z(pc, "vCofins") : BigDecimal.ZERO;
        BigDecimal rCsll = Num.z(tribFed, "vRetCSLL");
        BigDecimal rIrrf = Num.z(tribFed, "vRetIRRF");
        BigDecimal rCp = Num.z(tribFed, "vRetCP");
        BigDecimal csrf = rPis.add(rCof).add(rCsll);

        // ERR-13.2 — CSRF parcial ou fora de 4,65%
        boolean pcPos = rPis.signum() > 0 || rCof.signum() > 0;
        if (pcPos != (rCsll.signum() > 0)) {
            out.add(new Finding("ERR-13.2", Severity.ALERTA, "ret_csll", "Retenção de " + (pcPos ? "PIS/Cofins sem CSLL" : "CSLL sem PIS/Cofins")
                    + ": a CSRF é retida em conjunto (4,65%).", "Conferir se há dispensa para uma das contribuições."));
        }
        if (vServ != null && csrf.signum() > 0) {
            List<String> rates = new ArrayList<String>();
            BigDecimal basePc = Num.get(pc, "vBCPisCofins") != null ? Num.get(pc, "vBCPisCofins") : vServ;
            rateCheck(rates, "PIS", rPis, basePc, "0.65");
            rateCheck(rates, "Cofins", rCof, basePc, "3");
            rateCheck(rates, "CSLL", rCsll, vServ, "1");
            if (!rates.isEmpty()) {
                out.add(new Finding("ERR-13.2", Severity.ALERTA, "ret_csll", "CSRF fora das alíquotas: " + NfeXmlChecks.join(rates) + ".",
                        "PIS 0,65%, Cofins 3% e CSLL 1% sobre o valor do serviço."));
            }
        }

        // ERR-13.5 — retenção até R$ 10,00
        if (rIrrf.signum() > 0 && rIrrf.compareTo(TEN) <= 0) {
            out.add(new Finding("ERR-13.5", Severity.ALERTA, "ret_irrf", "IRRF retido de " + Num.fmt(rIrrf) + " (até R$ 10,00 é dispensado).",
                    "Conferir: a retenção de IR até R$ 10,00 é dispensada (Lei 9.430/1996, art. 67)."));
        }
        if (csrf.signum() > 0 && csrf.compareTo(TEN) <= 0) {
            out.add(new Finding("ERR-13.5", Severity.ALERTA, "ret_csll", "CSRF retida de " + Num.fmt(csrf) + " (até R$ 10,00 é dispensada).",
                    "Conferir: a retenção de CSRF até R$ 10,00 é dispensada (Lei 10.833/2003, art. 31, §3º)."));
        }

        // ERR-13.6 — Simples Nacional com retenção federal
        String opSimp = X.text(dps, "prest", "regTrib", "opSimpNac");
        if (("2".equals(opSimp) || "3".equals(opSimp)) && (rIrrf.signum() > 0 || csrf.signum() > 0)) {
            out.add(new Finding("ERR-13.6", Severity.ALERTA, "ret_irrf", "Prestador do Simples Nacional (opSimpNac = " + opSimp
                    + ") com IRRF/CSRF retidos.", "Optantes do Simples não sofrem retenção de IRRF e CSRF [validar exceções]."));
        }

        // ERR-13.7 — valor líquido
        BigDecimal vLiq = Num.get(nval, "vLiq");
        if (vServ != null && vLiq != null) {
            String tpRet = X.text(tribMun, "tpRetISSQN");
            BigDecimal issRet = ("2".equals(tpRet) || "3".equals(tpRet)) && vISS != null ? vISS : BigDecimal.ZERO;
            BigDecimal totRet = csrf.add(rIrrf).add(rCp).add(issRet);
            BigDecimal vTotRet = Num.get(nval, "vTotalRet");
            if (vTotRet != null && Num.differ(vTotRet, totRet, Num.TWO_CENTS)) {
                out.add(new Finding("ERR-13.7", Severity.ALERTA, "total_value", "Total de retenções " + Num.fmt(vTotRet)
                        + " ≠ soma das retenções informadas (" + Num.fmt(totRet) + ").", "Conferir as retenções da nota."));
            }
            BigDecimal ret = vTotRet != null ? vTotRet : totRet;
            BigDecimal expected = vServ.subtract(vDescIncond).subtract(vDescCond).subtract(ret);
            if (Num.differ(expected, vLiq, Num.TWO_CENTS)) {
                out.add(new Finding("ERR-13.7", Severity.ALERTA, "total_value", "Valor líquido " + Num.fmt(vLiq) + " ≠ serviço − descontos − retenções = "
                        + Num.fmt(expected) + ".", "Conferir descontos e retenções; o valor líquido é o que o tomador paga."));
            }
        }

        // ERR-11.1 — NFS-e própria de empresa do Lucro Presumido com alíquotas não cumulativas
        if (d.direction() == Direction.SAIDA && em != null && company != null) {
            RegimePeriod rp = company.regimeOn(em);
            if (rp != null && rp.regime() == TaxRegime.LUCRO_PRESUMIDO
                    && (Num.eq(Num.get(pc, "pAliqPis"), "1.65") || Num.eq(Num.get(pc, "pAliqCofins"), "7.6"))) {
                out.add(new Finding("ERR-11.1", Severity.ALERTA, "pis_value", "NFS-e da empresa (Lucro Presumido) com PIS 1,65% ou Cofins 7,6%.",
                        "No Lucro Presumido o regime é cumulativo: PIS 0,65% e Cofins 3%."));
            }
        }
    }

    private static void rateCheck(List<String> out, String name, BigDecimal value, BigDecimal base, String rate) {
        if (value.signum() > 0 && base != null && Num.differ(Num.pct(base, new BigDecimal(rate)), value, Num.TWO_CENTS)) {
            out.add(name + " " + Num.fmt(value) + " (esperado " + rate.replace('.', ',') + "% = " + Num.fmt(Num.pct(base, new BigDecimal(rate))) + ")");
        }
    }

    private static String month(LocalDate dt) {
        return String.format("%02d/%d", dt.getMonthValue(), dt.getYear());
    }
}
