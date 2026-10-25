package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.company.TaxRegime;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verificações da NF-e (modelo 55) que dependem apenas do XML da própria nota (catálogo
 * erros-nfe-nfse-v0.0.2, classe SO_XML). Usam o valor efetivo de CFOP e CST (XML ou correção do
 * usuário); os valores monetários vêm sempre do XML original.
 *
 * <p>Princípio: só acusa quando os dados necessários estão presentes. Campo ausente não é
 * tratado como zero, salvo onde o leiaute define a ausência como zero.
 */
final class NfeXmlChecks {

    /** Obrigatoriedade do grupo IBS/CBS (NT 2025.002-RTC) [validar datas]. */
    static final LocalDate IBSCBS_CRT3 = LocalDate.of(2026, 8, 3);
    static final LocalDate IBSCBS_CRT124 = LocalDate.of(2027, 1, 4);

    private static final Set<String> SOUTH_SOUTHEAST = set("SP", "RJ", "MG", "PR", "SC", "RS");
    private static final Set<String> IMPORTED_ORIGINS = set("1", "2", "3", "8");
    private static final Set<String> ST_SUBSTITUTE_CSTS = set("10", "30", "70", "90", "201", "202", "203", "900");
    private static final Map<String, Set<String>> GROUP_CSTS = new HashMap<String, Set<String>>();

    static {
        GROUP_CSTS.put("ICMS00", set("00"));
        GROUP_CSTS.put("ICMS02", set("02"));
        GROUP_CSTS.put("ICMS10", set("10"));
        GROUP_CSTS.put("ICMS15", set("15"));
        GROUP_CSTS.put("ICMS20", set("20"));
        GROUP_CSTS.put("ICMS30", set("30"));
        GROUP_CSTS.put("ICMS40", set("40", "41", "50"));
        GROUP_CSTS.put("ICMS51", set("51"));
        GROUP_CSTS.put("ICMS53", set("53"));
        GROUP_CSTS.put("ICMS60", set("60"));
        GROUP_CSTS.put("ICMS61", set("61"));
        GROUP_CSTS.put("ICMS70", set("70"));
        GROUP_CSTS.put("ICMS90", set("90"));
        GROUP_CSTS.put("ICMSPart", set("10", "90"));
        GROUP_CSTS.put("ICMSST", set("41", "60"));
        GROUP_CSTS.put("ICMSSN101", set("101"));
        GROUP_CSTS.put("ICMSSN102", set("102", "103", "300", "400"));
        GROUP_CSTS.put("ICMSSN201", set("201"));
        GROUP_CSTS.put("ICMSSN202", set("202", "203"));
        GROUP_CSTS.put("ICMSSN500", set("500"));
        GROUP_CSTS.put("ICMSSN900", set("900"));
    }

    private final FiscalDoc d;
    private final Company company;
    private final List<Finding> out;
    private final Element inf;
    private final Element ide;
    private final Element emit;
    private final Element dest;
    private final Element tot;
    private final List<Element> dets;
    private final String tpNF;
    private final String finNFe;
    private final String idDest;
    private final String indFinal;
    private final String indPres;
    private final String crt;
    private final String emitUf;
    private final String destUf;
    private final boolean foreign;
    private final LocalDate issued;

    private NfeXmlChecks(FiscalDoc d, Element root, Company company, List<Finding> out) {
        this.d = d;
        this.company = company;
        this.out = out;
        Element nfe = "NFe".equals(X.name(root)) ? root : X.child(root, "NFe");
        this.inf = X.child(nfe, "infNFe");
        this.ide = X.child(inf, "ide");
        this.emit = X.child(inf, "emit");
        this.dest = X.child(inf, "dest");
        this.tot = X.path(inf, "total", "ICMSTot");
        this.dets = X.children(inf, "det");
        this.tpNF = X.text(ide, "tpNF");
        this.finNFe = X.text(ide, "finNFe");
        this.idDest = X.text(ide, "idDest");
        this.indFinal = X.text(ide, "indFinal");
        this.indPres = X.text(ide, "indPres");
        this.crt = X.text(emit, "CRT");
        this.emitUf = X.text(emit, "enderEmit", "UF");
        this.destUf = X.text(dest, "enderDest", "UF");
        String cPais = X.text(dest, "enderDest", "cPais");
        this.foreign = dest != null && (X.child(dest, "idEstrangeiro") != null || "EX".equalsIgnoreCase(destUf)
                || (!Fields.NA.equals(cPais) && !"1058".equals(cPais)));
        this.issued = DocValidator.date(X.text(ide, "dhEmi"));
    }

    static void run(FiscalDoc d, Element root, Company company, List<Finding> out) {
        NfeXmlChecks c = new NfeXmlChecks(d, root, company, out);
        if (c.inf == null) {
            return;
        }
        c.operation();
        c.period();
        c.itemValues();
        c.totals();
        c.freightAndPayment();
        c.icms();
        c.difal();
        c.ipi();
        c.pisCofins();
        c.misc();
        c.reform();
    }

    // ================================================================== natureza da operação

    private void operation() {
        // ERR-2.6 — exterior
        if (foreign && !"3".equals(idDest)) {
            add("ERR-2.6", Severity.ALERTA, "id_dest", "Destinatário no exterior, mas idDest = " + idDest + " (esperado 3).",
                    "Conferir o destino da operação e o CFOP (7.xxx na saída, 3.xxx na entrada).");
        } else if ("3".equals(idDest) && !foreign) {
            add("ERR-2.6", Severity.ALERTA, "id_dest", "idDest = 3 (exterior), mas o destinatário é nacional.",
                    "Conferir o destinatário ou o indicador de destino.");
        }
        if (foreign) {
            List<String> wrong = new ArrayList<String>();
            String expected = "0".equals(tpNF) ? "3" : "7";
            for (int i = 0; i < dets.size(); i++) {
                String cfop = cfop(i);
                if (cfop.length() == 4 && !cfop.startsWith(expected)) {
                    wrong.add((i + 1) + " (" + cfop + ")");
                }
            }
            if (!wrong.isEmpty()) {
                add("ERR-2.6", Severity.ALERTA, "items.cfop", "Operação com exterior com CFOP diferente de " + expected
                        + ".xxx nos itens " + join(wrong) + ".", "Conferir o CFOP dos itens.");
            }
        }

        // ERR-4.2 — idDest × UFs
        if (!foreign && BrDocs.isValidUf(emitUf) && BrDocs.isValidUf(destUf)) {
            boolean same = emitUf.equalsIgnoreCase(destUf);
            boolean presentialFinal = "1".equals(indPres) && "1".equals(indFinal);
            if ("1".equals(idDest) && !same && !presentialFinal) {
                add("ERR-4.2", Severity.ALERTA, "id_dest", "idDest = 1 (interna), mas as UFs diferem (" + emitUf + " → " + destUf + ").",
                        "Conferir o indicador de destino; operação interestadual usa idDest = 2.");
            } else if ("2".equals(idDest) && same) {
                add("ERR-4.2", Severity.ALERTA, "id_dest", "idDest = 2 (interestadual), mas as UFs são iguais (" + emitUf + ").",
                        "Conferir o indicador de destino e a UF do destinatário.");
            }
        }

        // ERR-4.5 — devolução
        boolean hasRef = X.child(ide, "NFref") != null;
        List<String> devolItems = new ArrayList<String>();
        for (int i = 0; i < dets.size(); i++) {
            if (isDevolution(cfop(i))) {
                devolItems.add(String.valueOf(i + 1));
            }
        }
        if ("4".equals(finNFe)) {
            if (!hasRef) {
                add("ERR-4.5", Severity.ALERTA, "nfref", "Nota de devolução (finNFe = 4) sem referência à nota original (NFref).",
                        "Obter a nota com a referência; sem ela, a devolução não abate a receita com segurança.");
            }
            if (devolItems.isEmpty()) {
                add("ERR-4.5", Severity.ALERTA, "items.cfop", "Nota de devolução (finNFe = 4) sem nenhum CFOP de devolução.",
                        "Conferir o CFOP: devolução usa x.201–x.212, x.410–x.413, x.553, x.556, x.918, x.919 etc.");
            }
        } else if (!devolItems.isEmpty()) {
            add("ERR-4.5", Severity.ALERTA, "fin_nfe", "CFOP de devolução nos itens " + join(devolItems)
                    + ", mas a finalidade da nota é " + finNFe + " (devolução = 4).", "Conferir a finalidade e a referência da nota.");
        }

        // ERR-4.7 — complementar sem referência
        if ("2".equals(finNFe) && !hasRef) {
            add("ERR-4.7", Severity.ALERTA, "nfref", "Nota complementar (finNFe = 2) sem referência à nota original (NFref).",
                    "Obter a nota com a referência; o complemento precisa apontar a nota complementada.");
        }

        // ERR-4.12 — serviço (ISSQN) dentro da NF-e
        for (int i = 0; i < dets.size(); i++) {
            boolean iss = X.path(dets.get(i), "imposto", "ISSQN") != null;
            String cfop = cfop(i);
            boolean cfop933 = cfop.length() == 4 && cfop.endsWith("933");
            if (iss && !cfop933) {
                add("ERR-4.12", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"), "Item " + (i + 1) + ": serviço com ISSQN, mas CFOP "
                        + cfop + " (serviço tributado pelo ISS usa x.933).", "Conferir se o item é serviço ou mercadoria.");
            } else if (!iss && cfop933) {
                add("ERR-4.12", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"), "Item " + (i + 1) + ": CFOP " + cfop
                        + " (serviço sujeito ao ISS) sem o grupo ISSQN.", "Conferir a tributação do item.");
            }
        }
    }

    // ================================================================== período

    private void period() {
        // ERR-3.3 — saída da empresa com emissão e saída em meses diferentes
        if (d.direction() != Direction.SAIDA || !"1".equals(tpNF)) {
            return;
        }
        LocalDate em = DocValidator.date(d.get("issued_at"));
        LocalDate sa = DocValidator.date(d.get("entry_exit_date"));
        if (em != null && sa != null && (em.getYear() != sa.getYear() || em.getMonthValue() != sa.getMonthValue())) {
            add("ERR-3.3", Severity.ALERTA, "entry_exit_date", "Emitida em " + month(em) + " e com saída em " + month(sa)
                            + ": o ICMS e a receita seguem a data da saída.",
                    "Confirmar o mês da saída antes de apurar.");
        }
    }

    // ================================================================== valores dos itens

    private void itemValues() {
        List<String> unitMismatch = new ArrayList<String>();
        for (int i = 0; i < dets.size(); i++) {
            Element prod = X.child(dets.get(i), "prod");
            String n = "Item " + (i + 1) + ": ";
            BigDecimal vProd = Num.get(prod, "vProd");
            BigDecimal qCom = Num.get(prod, "qCom");
            BigDecimal vUnCom = Num.get(prod, "vUnCom");
            // ERR-5.1
            if (vProd != null && qCom != null && vUnCom != null) {
                BigDecimal calc = Num.mul2(qCom, vUnCom);
                if (Num.differ(calc, vProd, Num.CENT)) {
                    add("ERR-5.1", Severity.ALERTA, FiscalDoc.itemKey(i, "value"), n + "vProd " + Num.fmt(vProd)
                            + " ≠ quantidade × valor unitário (" + Num.fmt(qCom) + " × " + Num.fmt(vUnCom) + " = " + Num.fmt(calc) + ").",
                            "Conferir quantidade, valor unitário e valor do item.");
                }
            }
            BigDecimal qTrib = Num.get(prod, "qTrib");
            BigDecimal vUnTrib = Num.get(prod, "vUnTrib");
            if (vProd != null && qTrib != null && vUnTrib != null) {
                BigDecimal calc = Num.mul2(qTrib, vUnTrib);
                if (Num.differ(calc, vProd, Num.CENT)) {
                    add("ERR-5.1", Severity.ALERTA, FiscalDoc.itemKey(i, "value"), n + "vProd " + Num.fmt(vProd)
                            + " ≠ quantidade tributável × valor unitário tributável (" + Num.fmt(calc) + ").",
                            "Conferir a unidade tributável e a conversão.");
                }
            }
            // ERR-5.5 (parcial)
            BigDecimal vDesc = Num.get(prod, "vDesc");
            if (vProd != null && Num.positive(vDesc)) {
                BigDecimal gross = vProd.add(Num.z(prod, "vFrete")).add(Num.z(prod, "vSeg")).add(Num.z(prod, "vOutro"));
                if (vDesc.compareTo(gross) > 0) {
                    add("ERR-5.5", Severity.ALERTA, FiscalDoc.itemKey(i, "value"), n + "desconto " + Num.fmt(vDesc)
                            + " maior que o valor do item com acessórios (" + Num.fmt(gross) + ").", "Conferir o desconto.");
                }
            }
            // ERR-16.2
            String uCom = X.text(prod, "uCom");
            String uTrib = X.text(prod, "uTrib");
            if (!Fields.NA.equals(uCom) && !Fields.NA.equals(uTrib) && !unit(uCom).equals(unit(uTrib))
                    && qCom != null && qTrib != null && qCom.compareTo(qTrib) == 0
                    && vUnCom != null && vUnTrib != null && vUnCom.compareTo(vUnTrib) == 0) {
                unitMismatch.add((i + 1) + " (" + uCom + " × " + uTrib + ")");
            }
        }
        if (!unitMismatch.isEmpty()) {
            add("ERR-16.2", Severity.ALERTA, "items.unit", "Unidades comercial e tributável diferentes, com quantidade e valor "
                    + "iguais (conversão 1:1), nos itens " + join(unitMismatch) + ".",
                    "Conferir a unidade tributável e o fator de conversão (registro 0220 da EFD).");
        }
        // ERR-5.5 no total
        BigDecimal vDescT = Num.get(tot, "vDesc");
        BigDecimal vProdT = Num.get(tot, "vProd");
        if (Num.positive(vDescT) && vProdT != null && vDescT.compareTo(vProdT) > 0) {
            add("ERR-5.5", Severity.ALERTA, "discount_value", "Desconto total " + Num.fmt(vDescT) + " maior que o total de produtos "
                    + Num.fmt(vProdT) + ".", "Conferir os descontos.");
        }
    }

    // ================================================================== totais (ERR-5.2)

    private static final String[] TOTAL_FIELDS = {"vBC", "vICMS", "vICMSDeson", "vFCP", "vBCST", "vST", "vFCPST", "vFCPSTRet",
            "vFrete", "vSeg", "vDesc", "vOutro", "vII", "vIPI", "vIPIDevol", "vPIS", "vCOFINS"};

    private void totals() {
        if (tot == null) {
            return;
        }
        Map<String, BigDecimal> sum = new LinkedHashMap<String, BigDecimal>();
        for (String k : TOTAL_FIELDS) {
            sum.put(k, BigDecimal.ZERO);
        }
        for (Element det : dets) {
            Element prod = X.child(det, "prod");
            Element imp = X.child(det, "imposto");
            Element icms = X.first(X.child(imp, "ICMS"));
            acc(sum, "vBC", Num.z(icms, "vBC"));
            acc(sum, "vICMS", Num.z(icms, "vICMS"));
            acc(sum, "vICMSDeson", Num.z(icms, "vICMSDeson"));
            acc(sum, "vFCP", Num.z(icms, "vFCP"));
            acc(sum, "vBCST", Num.z(icms, "vBCST"));
            acc(sum, "vST", Num.z(icms, "vICMSST"));
            acc(sum, "vFCPST", Num.z(icms, "vFCPST"));
            acc(sum, "vFCPSTRet", Num.z(icms, "vFCPSTRet"));
            acc(sum, "vFrete", Num.z(prod, "vFrete"));
            acc(sum, "vSeg", Num.z(prod, "vSeg"));
            acc(sum, "vDesc", Num.z(prod, "vDesc"));
            acc(sum, "vOutro", Num.z(prod, "vOutro"));
            acc(sum, "vII", Num.z(imp, "II", "vII"));
            acc(sum, "vIPI", Num.z(imp, "IPI", "IPITrib", "vIPI"));
            acc(sum, "vIPIDevol", Num.z(det, "impostoDevol", "IPI", "vIPIDevol"));
            if (X.child(imp, "ISSQN") == null) { // PIS/Cofins de serviço vão para ISSQNtot
                acc(sum, "vPIS", Num.z(X.first(X.child(imp, "PIS")), "vPIS"));
                acc(sum, "vCOFINS", Num.z(X.first(X.child(imp, "COFINS")), "vCOFINS"));
            }
        }
        List<String> diffs = new ArrayList<String>();
        for (String k : TOTAL_FIELDS) {
            BigDecimal total = Num.get(tot, k);
            BigDecimal s = sum.get(k);
            if ((total == null && s.signum() != 0) || Num.differ(total, s, Num.TWO_CENTS)) {
                diffs.add(k + " (total " + Num.fmt(total) + " × itens " + Num.fmt(s) + ")");
            }
        }
        if (!diffs.isEmpty()) {
            add("ERR-5.2", Severity.ALERTA, "totals", "Totais da nota diferentes da soma dos itens: " + join(diffs) + ".",
                    "Conferir o XML; a EFD (C100/C190) e a apuração usam os totais e os itens.");
        }
    }

    // ================================================================== frete e pagamento

    private void freightAndPayment() {
        // ERR-5.7
        String modFrete = X.text(inf, "transp", "modFrete");
        BigDecimal vFrete = Num.get(tot, "vFrete");
        if (Num.positive(vFrete)) {
            String why = null;
            if ("9".equals(modFrete)) {
                why = "modFrete = 9 (sem frete)";
            } else if ("4".equals(modFrete)) {
                why = "modFrete = 4 (transporte próprio do destinatário)";
            } else if ("1".equals(modFrete)) {
                why = "modFrete = 1 (frete por conta do destinatário/FOB)";
            }
            if (why != null) {
                add("ERR-5.7", Severity.ALERTA, "freight_value", "Frete de " + Num.fmt(vFrete) + " somado à nota com " + why + ".",
                        "Confirmar quem contratou o frete; frete cobrado pelo emitente integra a base do ICMS e a receita.");
            }
        }
        // ERR-5.8 — pagamentos
        BigDecimal vNF = Num.get(tot, "vNF");
        Element pag = X.child(inf, "pag");
        if (pag != null && vNF != null) {
            BigDecimal paid = BigDecimal.ZERO;
            boolean any = false;
            boolean noPayment = false;
            for (Element dp : X.children(pag, "detPag")) {
                if ("90".equals(X.text(dp, "tPag"))) {
                    noPayment = true;
                }
                BigDecimal v = Num.get(dp, "vPag");
                if (v != null) {
                    paid = paid.add(v);
                    any = true;
                }
            }
            paid = paid.subtract(Num.z(pag, "vTroco"));
            if (any && !noPayment && Num.differ(paid, vNF, Num.TWO_CENTS)) {
                add("ERR-5.8", Severity.ALERTA, "pag", "Pagamentos informados (" + Num.fmt(paid) + ", já descontado o troco) diferem do valor da nota ("
                        + Num.fmt(vNF) + ").", "Conferir as formas de pagamento.");
            }
        }
        // ERR-5.8 — fatura e duplicatas
        Element cobr = X.child(inf, "cobr");
        if (cobr != null) {
            Element fat = X.child(cobr, "fat");
            BigDecimal vOrig = Num.get(fat, "vOrig");
            BigDecimal vLiq = Num.get(fat, "vLiq");
            if (vOrig != null && vLiq != null && Num.differ(vOrig.subtract(Num.z(fat, "vDesc")), vLiq, Num.TWO_CENTS)) {
                add("ERR-5.8", Severity.ALERTA, "cobr", "Fatura: valor original " + Num.fmt(vOrig) + " − desconto ≠ valor líquido "
                        + Num.fmt(vLiq) + ".", "Conferir a fatura.");
            }
            List<Element> dups = X.children(cobr, "dup");
            if (!dups.isEmpty() && vLiq != null) {
                BigDecimal s = BigDecimal.ZERO;
                for (Element dup : dups) {
                    s = s.add(Num.z(dup, "vDup"));
                }
                if (Num.differ(s, vLiq, Num.TWO_CENTS)) {
                    add("ERR-5.8", Severity.ALERTA, "cobr", "Soma das duplicatas (" + Num.fmt(s) + ") ≠ valor líquido da fatura ("
                            + Num.fmt(vLiq) + ").", "Conferir as parcelas.");
                }
            }
        }
    }

    // ================================================================== ICMS e ICMS-ST

    private void icms() {
        boolean wrongRegime = false;
        List<String> st60NoData = new ArrayList<String>();
        for (int i = 0; i < dets.size(); i++) {
            Element det = dets.get(i);
            Element prod = X.child(det, "prod");
            Element imp = X.child(det, "imposto");
            Element g = X.first(X.child(imp, "ICMS"));
            if (g == null) {
                continue;
            }
            String group = X.name(g);
            String n = "Item " + (i + 1) + ": ";
            String cfop = cfop(i);
            String cst = cst(i);
            String suffix = cfop.length() == 4 ? cfop.substring(1) : "";
            BigDecimal vBC = Num.get(g, "vBC");
            BigDecimal pICMS = Num.get(g, "pICMS");
            BigDecimal vICMS = Num.get(g, "vICMS");

            // ERR-6.8 — CRT × CST/CSOSN
            boolean sn = group.startsWith("ICMSSN");
            if ((("1".equals(crt) || "4".equals(crt)) && !sn) || (("2".equals(crt) || "3".equals(crt)) && sn)) {
                wrongRegime = true;
            }

            // ERR-7.7 — CST corrigido × grupo do XML
            Set<String> allowed = GROUP_CSTS.get(group);
            if (d.isEdited(FiscalDoc.itemKey(i, "cst")) && allowed != null && !allowed.contains(cst)) {
                add("ERR-7.7", Severity.ALERTA, FiscalDoc.itemKey(i, "cst"), n + "CST corrigido para " + cst + ", mas o XML traz o grupo "
                        + group + " (CST " + d.itemOriginal(i, "cst") + "): base e imposto do XML correspondem ao CST original.",
                        "Rever a correção ou obter nota corrigida do emitente.");
            }

            // ERR-6.1 — ICMS = base × alíquota
            if (!"ICMS51".equals(group) && vBC != null && pICMS != null && vICMS != null) {
                BigDecimal calc = Num.pct(vBC, pICMS);
                if (Num.differ(calc, vICMS, Num.CENT)) {
                    add("ERR-6.1", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "ICMS " + Num.fmt(vICMS) + " ≠ base "
                            + Num.fmt(vBC) + " × " + Num.fmt(pICMS) + "% = " + Num.fmt(calc) + ".", "Conferir base, alíquota e valor do ICMS.");
                }
            }
            BigDecimal pFCP = Num.get(g, "pFCP");
            BigDecimal vFCP = Num.get(g, "vFCP");
            if (pFCP != null && vFCP != null) {
                BigDecimal base = Num.get(g, "vBCFCP") != null ? Num.get(g, "vBCFCP") : vBC;
                if (base != null && Num.differ(Num.pct(base, pFCP), vFCP, Num.CENT)) {
                    add("ERR-6.1", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "FCP " + Num.fmt(vFCP) + " ≠ base × "
                            + Num.fmt(pFCP) + "% = " + Num.fmt(Num.pct(base, pFCP)) + ".", "Conferir o FCP.");
                }
            }

            // ERR-6.2 — base do ICMS (valor da operação)
            if (vBC != null && "3".equals(X.text(g, "modBC")) && !cfop.startsWith("3")
                    && set("ICMS00", "ICMS10", "ICMS20", "ICMS51", "ICMS70").contains(group)) {
                checkIcmsBase(i, prod, imp, g, vBC);
            }

            // ERR-6.3 — alíquota interestadual
            if ("2".equals(idDest) && "1".equals(tpNF) && cfop.startsWith("6") && pICMS != null && Num.positive(vICMS)
                    && set("ICMS00", "ICMS10", "ICMS20", "ICMS70", "ICMS90").contains(group)
                    && BrDocs.isValidUf(emitUf) && BrDocs.isValidUf(destUf) && !emitUf.equalsIgnoreCase(destUf)) {
                String orig = X.text(g, "orig");
                BigDecimal expected = IMPORTED_ORIGINS.contains(orig) ? new BigDecimal("4")
                        : SOUTH_SOUTHEAST.contains(emitUf.toUpperCase()) && !SOUTH_SOUTHEAST.contains(destUf.toUpperCase())
                        ? new BigDecimal("7") : new BigDecimal("12");
                if (pICMS.compareTo(expected) != 0) {
                    add("ERR-6.3", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "alíquota interestadual " + Num.fmt(pICMS)
                            + "%, esperada " + expected.toPlainString() + "% (origem " + orig + ", " + emitUf + " → " + destUf + ").",
                            "Conferir a origem da mercadoria (4% para importados) e a alíquota.");
                }
            }

            // ERR-6.6 — CST × CFOP (ST)
            if (cfop.startsWith("5") || cfop.startsWith("6")) {
                if (set("401", "402", "403").contains(suffix) && !ST_SUBSTITUTE_CSTS.contains(cst)) {
                    add("ERR-6.6", Severity.ALERTA, FiscalDoc.itemKey(i, "cst"), n + "CFOP " + cfop + " (contribuinte substituto) com CST "
                            + cst + " (sem retenção de ST).", "Conferir o CFOP e a retenção do ICMS-ST.");
                } else if ("405".equals(suffix) && !set("60", "500").contains(cst)) {
                    add("ERR-6.6", Severity.ALERTA, FiscalDoc.itemKey(i, "cst"), n + "CFOP " + cfop + " (mercadoria com ST já retida) com CST "
                            + cst + " (esperado 60 ou CSOSN 500).", "Conferir o CFOP e o CST.");
                } else if (set("101", "102").contains(suffix) && set("60", "500").contains(cst)) {
                    add("ERR-6.6", Severity.ALERTA, FiscalDoc.itemKey(i, "cst"), n + "CFOP " + cfop + " (venda sem ST) com CST " + cst
                            + " (ST já retida); o CFOP esperado é x.405.", "Conferir o CFOP e o CST.");
                }
            }

            // ERR-6.7 — CST tributado sem imposto
            if (set("ICMS00", "ICMS10", "ICMS20").contains(group) && (Num.isZero(vICMS) || Num.isZero(pICMS))) {
                add("ERR-6.7", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "CST " + d.itemOriginal(i, "cst")
                        + " (tributado) com ICMS zerado.", "Conferir a tributação: isenção e não incidência usam CST 40/41.");
            }

            // ERR-6.10 — diferimento
            if ("ICMS51".equals(group)) {
                BigDecimal vOp = Num.get(g, "vICMSOp");
                BigDecimal pDif = Num.get(g, "pDif");
                BigDecimal vDif = Num.get(g, "vICMSDif");
                if (vOp != null && vBC != null && pICMS != null && Num.differ(Num.pct(vBC, pICMS), vOp, Num.CENT)) {
                    add("ERR-6.10", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "ICMS da operação " + Num.fmt(vOp)
                            + " ≠ base × alíquota (" + Num.fmt(Num.pct(vBC, pICMS)) + ").", "Conferir o diferimento.");
                }
                if (vOp != null && pDif != null && vDif != null && Num.differ(Num.pct(vOp, pDif), vDif, Num.CENT)) {
                    add("ERR-6.10", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "ICMS diferido " + Num.fmt(vDif)
                            + " ≠ " + Num.fmt(pDif) + "% do ICMS da operação (" + Num.fmt(Num.pct(vOp, pDif)) + ").", "Conferir o diferimento.");
                }
                if (vOp != null && vDif != null && vICMS != null && Num.differ(vOp.subtract(vDif), vICMS, Num.CENT)) {
                    add("ERR-6.10", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "ICMS devido " + Num.fmt(vICMS)
                            + " ≠ ICMS da operação − diferido (" + Num.fmt(vOp.subtract(vDif)) + ").", "Conferir o diferimento.");
                }
            }

            // ERR-6.12 — exportação com ICMS
            if (isExportOutflow(cfop) && Num.positive(vICMS)) {
                add("ERR-6.12", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "CFOP " + cfop + " (exportação) com ICMS de "
                        + Num.fmt(vICMS) + ".", "Exportação é imune ao ICMS; conferir o CST e o CFOP.");
            }

            // ERR-8.4 — ICMS-ST
            BigDecimal vBCST = Num.get(g, "vBCST");
            BigDecimal pST = Num.get(g, "pICMSST");
            BigDecimal vST = Num.get(g, "vICMSST");
            if (vBCST != null && pST != null && vST != null && set("ICMS10", "ICMS70", "ICMS90", "ICMSPart").contains(group)) {
                BigDecimal calc = Num.pct(vBCST, pST).subtract(Num.z(g, "vICMS"));
                if (calc.signum() < 0) {
                    calc = BigDecimal.ZERO;
                }
                if (Num.differ(calc, vST, Num.TWO_CENTS)) {
                    add("ERR-8.4", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "ICMS-ST " + Num.fmt(vST) + " ≠ base ST "
                            + Num.fmt(vBCST) + " × " + Num.fmt(pST) + "% − ICMS próprio = " + Num.fmt(calc) + ".",
                            "Conferir a base (MVA/pauta), a alíquota e o abatimento do ICMS próprio.");
                }
            }
            BigDecimal pFCPST = Num.get(g, "pFCPST");
            BigDecimal vFCPST = Num.get(g, "vFCPST");
            BigDecimal vBCFCPST = Num.get(g, "vBCFCPST");
            if (pFCPST != null && vFCPST != null && vBCFCPST != null && Num.differ(Num.pct(vBCFCPST, pFCPST), vFCPST, Num.CENT)) {
                add("ERR-8.4", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "FCP-ST " + Num.fmt(vFCPST) + " ≠ base × "
                        + Num.fmt(pFCPST) + "% = " + Num.fmt(Num.pct(vBCFCPST, pFCPST)) + ".", "Conferir o FCP-ST.");
            }

            // ERR-8.7 — CST 60 sem ST retido
            if (("ICMS60".equals(group) || "ICMSSN500".equals(group)) && X.child(g, "vBCSTRet") == null && X.child(g, "vICMSSTRet") == null) {
                st60NoData.add(String.valueOf(i + 1));
            }
        }
        if (wrongRegime) {
            add("ERR-6.8", Severity.ALERTA, "items.cst", "CRT do emitente = " + crt + ", mas os itens usam "
                    + (("1".equals(crt) || "4".equals(crt)) ? "CST do regime normal" : "CSOSN do Simples Nacional") + ".",
                    "CRT 1 e 4 usam CSOSN; CRT 2 e 3 usam CST. Conferir o regime do emitente.");
        }
        if (!st60NoData.isEmpty()) {
            add("ERR-8.7", Severity.ALERTA, "items.cst", "CST 60/CSOSN 500 sem os valores do ICMS-ST retido (vBCSTRet, vICMSSTRet) nos itens "
                    + join(st60NoData) + ".", "Sem esses valores não há como pedir ressarcimento/complemento nem preencher o C176.");
        }
    }

    private void checkIcmsBase(int i, Element prod, Element imp, Element g, BigDecimal vBC) {
        BigDecimal vProd = Num.get(prod, "vProd");
        if (vProd == null) {
            return;
        }
        BigDecimal gross = vProd.add(Num.z(prod, "vFrete")).add(Num.z(prod, "vSeg")).add(Num.z(prod, "vOutro")).subtract(Num.z(prod, "vDesc"));
        BigDecimal vIPI = Num.z(imp, "IPI", "IPITrib", "vIPI");
        BigDecimal factor = BigDecimal.ONE;
        BigDecimal pRed = Num.get(g, "pRedBC");
        if (pRed != null) {
            factor = BigDecimal.ONE.subtract(pRed.divide(Num.HUNDRED, 10, RoundingMode.HALF_UP));
        }
        BigDecimal noIpi = gross.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal withIpi = gross.add(vIPI).multiply(factor).setScale(2, RoundingMode.HALF_UP);
        String n = "Item " + (i + 1) + ": ";
        boolean matchNo = !Num.differ(vBC, noIpi, Num.TWO_CENTS);
        boolean matchWith = !Num.differ(vBC, withIpi, Num.TWO_CENTS);
        if (!matchNo && !matchWith) {
            add("ERR-6.2", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "base do ICMS " + Num.fmt(vBC) + " não confere com o valor "
                    + "do item + frete + seguro + outras − desconto" + (pRed != null ? " com redução de " + Num.fmt(pRed) + "%" : "")
                    + " (" + Num.fmt(noIpi) + (vIPI.signum() > 0 ? "; com IPI: " + Num.fmt(withIpi) : "") + ").",
                    "Conferir as despesas acessórias, o desconto e a redução de base.");
        } else if (vIPI.signum() > 0 && "1".equals(indFinal) && matchNo && !matchWith) {
            add("ERR-6.2", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "venda a consumidor final com IPI fora da base do ICMS "
                    + "(base " + Num.fmt(vBC) + "; com IPI seria " + Num.fmt(withIpi) + ").",
                    "O IPI integra a base do ICMS quando a mercadoria vai para consumo ou ativo (LC 87/1996, art. 13, §2º).");
        } else if (vIPI.signum() > 0 && "0".equals(indFinal) && matchWith && !matchNo) {
            add("ERR-6.2", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "operação entre contribuintes (indFinal = 0) com IPI na base "
                    + "do ICMS (sem IPI seria " + Num.fmt(noIpi) + ").",
                    "Se a mercadoria não vai para consumo ou ativo, o IPI fica fora da base; se vai, indFinal deveria ser 1.");
        }
    }

    // ================================================================== DIFAL e destinatário

    private void difal() {
        String indIEDest = X.text(dest, "indIEDest");
        String ie = X.text(dest, "IE");
        // ERR-9.2
        if (dest != null) {
            if ("1".equals(indIEDest) && Fields.NA.equals(ie)) {
                add("ERR-9.2", Severity.ALERTA, "recipient_ie", "Destinatário marcado como contribuinte (indIEDest = 1) sem IE.",
                        "Conferir a inscrição estadual do destinatário.");
            } else if (("2".equals(indIEDest) || "9".equals(indIEDest)) && !Fields.NA.equals(ie)) {
                add("ERR-9.2", Severity.ALERTA, "recipient_ie", "Destinatário com IE " + ie + ", mas indIEDest = " + indIEDest
                        + ("2".equals(indIEDest) ? " (isento)." : " (não contribuinte)."), "Conferir a situação do destinatário no cadastro da UF.");
            }
            if ("9".equals(indIEDest) && "0".equals(indFinal) && "1".equals(tpNF) && !foreign) {
                add("ERR-9.2", Severity.ALERTA, "recipient_ie", "Venda a não contribuinte (indIEDest = 9) sem indicar consumidor final (indFinal = 0).",
                        "Operação com não contribuinte é operação com consumidor final.");
            }
            if (foreign && !Fields.NA.equals(indIEDest) && !"9".equals(indIEDest)) {
                add("ERR-9.2", Severity.ALERTA, "recipient_ie", "Destinatário no exterior com indIEDest = " + indIEDest + " (esperado 9).",
                        "Conferir os indicadores do destinatário.");
            }
        }
        // ERR-9.1 — DIFAL ausente
        boolean normalRegime = "2".equals(crt) || "3".equals(crt);
        if ("2".equals(idDest) && "1".equals(indFinal) && "9".equals(indIEDest) && "1".equals(tpNF) && normalRegime
                && issued != null && !issued.isBefore(LocalDate.of(2016, 1, 1))) {
            List<String> missing = new ArrayList<String>();
            for (int i = 0; i < dets.size(); i++) {
                Element imp = X.child(dets.get(i), "imposto");
                Element g = X.first(X.child(imp, "ICMS"));
                String cfop = cfop(i);
                if (cfop.startsWith("6") && !isDevolution(cfop) && Num.positive(Num.get(g, "vICMS")) && X.child(imp, "ICMSUFDest") == null) {
                    missing.add(String.valueOf(i + 1));
                }
            }
            if (!missing.isEmpty()) {
                add("ERR-9.1", Severity.ALERTA, "items.difal", "Venda interestadual a consumidor final não contribuinte sem o grupo do DIFAL "
                        + "(ICMSUFDest) nos itens " + join(missing) + ".", "Calcular e recolher o DIFAL para a UF de destino.");
            }
        }
        // ERR-9.3 — partilha e valor do DIFAL
        for (int i = 0; i < dets.size(); i++) {
            Element imp = X.child(dets.get(i), "imposto");
            Element u = X.child(imp, "ICMSUFDest");
            if (u == null) {
                continue;
            }
            String n = "Item " + (i + 1) + ": ";
            BigDecimal part = Num.get(u, "pICMSInterPart");
            if (part != null && issued != null && issued.getYear() >= 2019 && part.compareTo(Num.HUNDRED) != 0) {
                add("ERR-9.3", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "partilha do DIFAL com " + Num.fmt(part)
                        + "% para a UF de destino (desde 2019 é 100%).", "Conferir o percentual de partilha.");
            }
            BigDecimal pInter = Num.get(u, "pICMSInter");
            if (pInter != null && !Num.eq(pInter, "4") && !Num.eq(pInter, "7") && !Num.eq(pInter, "12")) {
                add("ERR-9.3", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "alíquota interestadual do DIFAL " + Num.fmt(pInter)
                        + "% (válidas: 4%, 7% ou 12%).", "Conferir a alíquota interestadual.");
            }
            BigDecimal vBCUF = Num.get(u, "vBCUFDest");
            BigDecimal pUF = Num.get(u, "pICMSUFDest");
            BigDecimal vUF = Num.get(u, "vICMSUFDest");
            if (vBCUF != null && pUF != null && pInter != null && vUF != null) {
                BigDecimal share = part == null ? BigDecimal.ONE : part.divide(Num.HUNDRED, 10, RoundingMode.HALF_UP);
                BigDecimal single = Num.pct(vBCUF, pUF.subtract(pInter)).multiply(share).setScale(2, RoundingMode.HALF_UP);
                BigDecimal own = Num.z(X.first(X.child(imp, "ICMS")), "vICMS");
                BigDecimal dual = Num.pct(vBCUF, pUF).subtract(own).multiply(share).setScale(2, RoundingMode.HALF_UP);
                if (Num.differ(single, vUF, Num.TWO_CENTS) && Num.differ(dual, vUF, Num.TWO_CENTS)) {
                    add("ERR-9.3", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "DIFAL " + Num.fmt(vUF) + " não confere com a base "
                            + Num.fmt(vBCUF) + " × (" + Num.fmt(pUF) + "% − " + Num.fmt(pInter) + "%) = " + Num.fmt(single)
                            + " nem com o cálculo por base dupla (" + Num.fmt(dual) + ").", "Conferir a base e as alíquotas do DIFAL.");
                }
            }
            BigDecimal pFCPUF = Num.get(u, "pFCPUFDest");
            BigDecimal vFCPUF = Num.get(u, "vFCPUFDest");
            BigDecimal vBCFCPUF = Num.get(u, "vBCFCPUFDest") != null ? Num.get(u, "vBCFCPUFDest") : vBCUF;
            if (pFCPUF != null && vFCPUF != null && vBCFCPUF != null && Num.differ(Num.pct(vBCFCPUF, pFCPUF), vFCPUF, Num.CENT)) {
                add("ERR-9.3", Severity.ALERTA, FiscalDoc.itemKey(i, "icms_value"), n + "FCP do destino " + Num.fmt(vFCPUF) + " ≠ base × "
                        + Num.fmt(pFCPUF) + "% = " + Num.fmt(Num.pct(vBCFCPUF, pFCPUF)) + ".", "Conferir o FCP da UF de destino.");
            }
        }
    }

    // ================================================================== IPI

    private void ipi() {
        for (int i = 0; i < dets.size(); i++) {
            Element det = dets.get(i);
            Element ipi = X.path(det, "imposto", "IPI");
            if (ipi == null) {
                continue;
            }
            String n = "Item " + (i + 1) + ": ";
            Element trib = X.child(ipi, "IPITrib");
            Element nt = X.child(ipi, "IPINT");
            String cst = X.text(trib != null ? trib : nt, "CST");
            // ERR-10.2
            if (cst.matches("\\d{2}")) {
                int c = Integer.parseInt(cst);
                if ("1".equals(tpNF) && c < 50) {
                    add("ERR-10.2", Severity.ALERTA, FiscalDoc.itemKey(i, "ipi_value"), n + "CST do IPI " + cst
                            + " é de entrada, mas a nota é de saída (tpNF = 1).", "Saídas usam CST do IPI 50 a 99.");
                } else if ("0".equals(tpNF) && c >= 50) {
                    add("ERR-10.2", Severity.ALERTA, FiscalDoc.itemKey(i, "ipi_value"), n + "CST do IPI " + cst
                            + " é de saída, mas a nota é de entrada (tpNF = 0).", "Entradas usam CST do IPI 00 a 49.");
                }
            }
            // ERR-10.3
            if (trib != null) {
                BigDecimal vIPI = Num.get(trib, "vIPI");
                BigDecimal vBC = Num.get(trib, "vBC");
                BigDecimal pIPI = Num.get(trib, "pIPI");
                BigDecimal qUnid = Num.get(trib, "qUnid");
                BigDecimal vUnid = Num.get(trib, "vUnid");
                if (vIPI != null && vBC != null && pIPI != null && Num.differ(Num.pct(vBC, pIPI), vIPI, Num.CENT)) {
                    add("ERR-10.3", Severity.ALERTA, FiscalDoc.itemKey(i, "ipi_value"), n + "IPI " + Num.fmt(vIPI) + " ≠ base " + Num.fmt(vBC)
                            + " × " + Num.fmt(pIPI) + "% = " + Num.fmt(Num.pct(vBC, pIPI)) + ".", "Conferir base, alíquota e valor do IPI.");
                } else if (vIPI != null && qUnid != null && vUnid != null && Num.differ(Num.mul2(qUnid, vUnid), vIPI, Num.CENT)) {
                    add("ERR-10.3", Severity.ALERTA, FiscalDoc.itemKey(i, "ipi_value"), n + "IPI " + Num.fmt(vIPI) + " ≠ quantidade × valor "
                            + "por unidade = " + Num.fmt(Num.mul2(qUnid, vUnid)) + ".", "Conferir a alíquota específica do IPI.");
                }
                Element prod = X.child(det, "prod");
                BigDecimal vProd = Num.get(prod, "vProd");
                if (vBC != null && vProd != null && !cfop(i).startsWith("3")) {
                    BigDecimal full = vProd.add(Num.z(prod, "vFrete")).add(Num.z(prod, "vSeg")).add(Num.z(prod, "vOutro"));
                    BigDecimal net = full.subtract(Num.z(prod, "vDesc"));
                    if (Num.differ(vBC, full, Num.TWO_CENTS) && Num.differ(vBC, net, Num.TWO_CENTS)) {
                        add("ERR-10.3", Severity.ALERTA, FiscalDoc.itemKey(i, "ipi_value"), n + "base do IPI " + Num.fmt(vBC)
                                + " diferente do valor do item com frete, seguro e outras despesas (" + Num.fmt(net) + ").",
                                "Conferir a base do IPI (RIPI, art. 190).");
                    }
                }
            }
        }
    }

    // ================================================================== PIS/Cofins

    private void pisCofins() {
        // ERR-11.1 — nota própria de saída de empresa do Lucro Presumido
        boolean presumido = false;
        if (d.direction() == Direction.SAIDA && issued != null && company != null) {
            RegimePeriod rp = company.regimeOn(issued);
            presumido = rp != null && rp.regime() == TaxRegime.LUCRO_PRESUMIDO;
        }
        boolean wrongRate = false;
        List<String> exportWithPc = new ArrayList<String>();
        for (int i = 0; i < dets.size(); i++) {
            Element det = dets.get(i);
            Element imp = X.child(det, "imposto");
            Element prod = X.child(det, "prod");
            String n = "Item " + (i + 1) + ": ";
            String cfop = cfop(i);
            Element pis = X.first(X.child(imp, "PIS"));
            Element cof = X.first(X.child(imp, "COFINS"));
            if (presumido && (Num.eq(Num.get(pis, "pPIS"), "1.65") || Num.eq(Num.get(cof, "pCOFINS"), "7.6"))) {
                wrongRate = true;
            }
            checkContribution(i, n, "PIS", pis, "pPIS", "vPIS", prod, cfop);
            checkContribution(i, n, "Cofins", cof, "pCOFINS", "vCOFINS", prod, cfop);
            // ERR-11.6
            if (isExportOutflow(cfop) && (Num.positive(Num.get(pis, "vPIS")) || Num.positive(Num.get(cof, "vCOFINS")))) {
                exportWithPc.add((i + 1) + " (" + cfop + ")");
            }
        }
        if (wrongRate) {
            add("ERR-11.1", Severity.ALERTA, "pis_value", "Nota da empresa (Lucro Presumido) com PIS 1,65% ou Cofins 7,6% (regime não cumulativo).",
                    "No Lucro Presumido o regime é cumulativo: PIS 0,65% e Cofins 3%.");
        }
        if (!exportWithPc.isEmpty()) {
            add("ERR-11.6", Severity.ALERTA, "pis_value", "Exportação com PIS/Cofins destacado nos itens " + join(exportWithPc) + ".",
                    "Receita de exportação não sofre PIS/Cofins; conferir o CST.");
        }
    }

    private void checkContribution(int i, String n, String name, Element g, String pTag, String vTag, Element prod, String cfop) {
        if (g == null) {
            return;
        }
        BigDecimal vBC = Num.get(g, "vBC");
        BigDecimal p = Num.get(g, pTag);
        BigDecimal v = Num.get(g, vTag);
        if (vBC != null && p != null && v != null && Num.differ(Num.pct(vBC, p), v, Num.CENT)) {
            add("ERR-11.5", Severity.ALERTA, FiscalDoc.itemKey(i, vTag.equals("vPIS") ? "pis_value" : "cofins_value"), n + name + " "
                    + Num.fmt(v) + " ≠ base " + Num.fmt(vBC) + " × " + Num.fmt(p) + "% = " + Num.fmt(Num.pct(vBC, p)) + ".",
                    "Conferir base, alíquota e valor.");
        }
        BigDecimal qBC = Num.get(g, "qBCProd");
        BigDecimal vAliqProd = Num.get(g, "vAliqProd");
        if (qBC != null && vAliqProd != null && v != null && Num.differ(Num.mul2(qBC, vAliqProd), v, Num.CENT)) {
            add("ERR-11.5", Severity.ALERTA, FiscalDoc.itemKey(i, vTag.equals("vPIS") ? "pis_value" : "cofins_value"), n + name + " "
                    + Num.fmt(v) + " ≠ quantidade × alíquota por unidade = " + Num.fmt(Num.mul2(qBC, vAliqProd)) + ".", "Conferir o cálculo por unidade.");
        }
        BigDecimal vProd = Num.get(prod, "vProd");
        if (vBC != null && vProd != null && "1".equals(tpNF) && (cfop.startsWith("5") || cfop.startsWith("6"))) {
            BigDecimal ceiling = vProd.add(Num.z(prod, "vFrete")).add(Num.z(prod, "vSeg")).add(Num.z(prod, "vOutro")).subtract(Num.z(prod, "vDesc"));
            if (vBC.subtract(ceiling).compareTo(Num.TWO_CENTS) > 0) {
                add("ERR-11.5", Severity.ALERTA, FiscalDoc.itemKey(i, vTag.equals("vPIS") ? "pis_value" : "cofins_value"), n + "base do "
                        + name + " " + Num.fmt(vBC) + " maior que o valor da operação (" + Num.fmt(ceiling) + "): IPI ou ICMS-ST incluídos?",
                        "IPI e ICMS-ST não integram a receita bruta (Lei 9.718/1998, art. 3º, §2º, I).");
            }
        }
    }

    // ================================================================== diversos

    private void misc() {
        // ERR-12.1 (ISSQN na NF-e)
        for (int i = 0; i < dets.size(); i++) {
            Element iss = X.path(dets.get(i), "imposto", "ISSQN");
            BigDecimal vBC = Num.get(iss, "vBC");
            BigDecimal aliq = Num.get(iss, "vAliq");
            BigDecimal v = Num.get(iss, "vISSQN");
            if (vBC != null && aliq != null && v != null && Num.differ(Num.pct(vBC, aliq), v, Num.CENT)) {
                add("ERR-12.1", Severity.ALERTA, FiscalDoc.itemKey(i, "value"), "Item " + (i + 1) + ": ISS " + Num.fmt(v) + " ≠ base "
                        + Num.fmt(vBC) + " × " + Num.fmt(aliq) + "% = " + Num.fmt(Num.pct(vBC, aliq)) + ".", "Conferir base, alíquota e valor do ISS.");
            }
        }
        // ERR-13.10 — compra de produtor rural pessoa física (nota própria de entrada)
        boolean ownEntry = company != null && "0".equals(tpNF)
                && BrDocs.alphanumeric(X.text(emit, "CNPJ")).equals(BrDocs.alphanumeric(company.document()));
        if (ownEntry && X.child(dest, "CPF") != null) {
            for (int i = 0; i < dets.size(); i++) {
                String cfop = cfop(i);
                if (cfop.length() == 4 && (cfop.startsWith("1") || cfop.startsWith("2")) && set("101", "102").contains(cfop.substring(1))) {
                    add("ERR-13.10", Severity.ALERTA, "recipient_doc", "Nota própria de entrada (compra) de pessoa física: se for produtor rural, "
                                    + "há contribuição a reter e informar no EFD-Reinf (R-2055).",
                            "Confirmar se o fornecedor é produtor rural e recolher a contribuição retida [validar alíquotas].");
                    break;
                }
            }
        }
        // ERR-16.5 — processo ou benefício citado
        Element adic = X.child(inf, "infAdic");
        if (adic != null) {
            String text = (X.text(adic, "infCpl") + " " + X.text(adic, "infAdFisco")).toLowerCase();
            boolean proc = X.child(adic, "procRef") != null;
            boolean cites = text.matches("(?s).*(liminar|mandado de seguran|decis[aã]o judicial|tutela|processo (judicial|administrativo)|regime especial).*");
            if (proc || cites) {
                add("ERR-16.5", Severity.ALERTA, "inf_adic", "A nota cita " + (proc ? "processo (procRef)" : "processo, decisão ou regime especial")
                                + " nas informações adicionais.",
                        "Registrar na EFD (C110/C111, e C113/C197 se houver ajuste) e guardar o documento citado.");
            }
        }
    }

    // ================================================================== reforma (IBS/CBS)

    private void reform() {
        if (issued == null) {
            return;
        }
        // ERR-17.1 — obrigatoriedade do grupo
        LocalDate start = "3".equals(crt) ? IBSCBS_CRT3
                : ("1".equals(crt) || "2".equals(crt) || "4".equals(crt)) ? IBSCBS_CRT124 : null;
        if (start != null && !issued.isBefore(start) && "1".equals(X.text(ide, "tpAmb"))) {
            List<String> missing = new ArrayList<String>();
            for (int i = 0; i < dets.size(); i++) {
                if (X.path(dets.get(i), "imposto", "IBSCBS") == null) {
                    missing.add(String.valueOf(i + 1));
                }
            }
            if (!missing.isEmpty()) {
                add("ERR-17.1", Severity.ALERTA, "items.ibscbs", "Emitida em " + issued + " (CRT " + crt + ") sem o grupo IBS/CBS nos itens "
                                + join(missing) + "; obrigatório desde " + start + " [validar data].",
                        "Em 2026 a dispensa do recolhimento depende do cumprimento das obrigações acessórias (LC 214/2025, art. 348).");
            }
        }
        // ERR-17.3 — valores
        for (int i = 0; i < dets.size(); i++) {
            Element ibscbs = X.path(dets.get(i), "imposto", "IBSCBS");
            Element g = X.child(ibscbs, "gIBSCBS");
            if (g == null) {
                continue;
            }
            String n = "Item " + (i + 1) + ": ";
            BigDecimal vBC = Num.get(g, "vBC");
            List<String> problems = new ArrayList<String>();
            BigDecimal vUF = checkReformPart(vBC, X.child(g, "gIBSUF"), "pIBSUF", "vIBSUF", "IBS-UF", problems);
            BigDecimal vMun = checkReformPart(vBC, X.child(g, "gIBSMun"), "pIBSMun", "vIBSMun", "IBS-Mun", problems);
            checkReformPart(vBC, X.child(g, "gCBS"), "pCBS", "vCBS", "CBS", problems);
            BigDecimal vIBS = Num.get(g, "vIBS");
            if (vIBS != null && vUF != null && vMun != null && X.child(ibscbs, "gCredPres") == null
                    && X.path(g, "gIBSCredPres") == null && Num.differ(vUF.add(vMun), vIBS, Num.CENT)) {
                problems.add("vIBS " + Num.fmt(vIBS) + " ≠ IBS-UF + IBS-Mun (" + Num.fmt(vUF.add(vMun)) + ")");
            }
            if (issued.getYear() == 2026 && "000001".equals(X.text(ibscbs, "cClassTrib"))) {
                rate2026(X.child(g, "gCBS"), "pCBS", "0.9", "CBS", problems);
                rate2026(X.child(g, "gIBSUF"), "pIBSUF", "0.1", "IBS-UF", problems);
                rate2026(X.child(g, "gIBSMun"), "pIBSMun", "0", "IBS-Mun", problems);
            }
            if (!problems.isEmpty()) {
                add("ERR-17.3", Severity.ALERTA, FiscalDoc.itemKey(i, "value"), n + join(problems) + ".",
                        "Conferir base, alíquotas e valores de IBS/CBS (cClassTrib " + X.text(ibscbs, "cClassTrib") + ").");
            }
        }
    }

    /** Confere valor = base × alíquota (efetiva, se houver redução). Devolve o valor informado. */
    private static BigDecimal checkReformPart(BigDecimal vBC, Element part, String pTag, String vTag, String label, List<String> problems) {
        if (part == null) {
            return null;
        }
        BigDecimal v = Num.get(part, vTag);
        if (vBC == null || v == null || X.child(part, "gDif") != null || X.child(part, "gDevTrib") != null) {
            return v;
        }
        BigDecimal rate = Num.get(part, "gRed", "pAliqEfet");
        if (rate == null) {
            rate = Num.get(part, pTag);
        }
        if (rate != null && Num.differ(Num.pct(vBC, rate), v, Num.CENT)) {
            problems.add(label + " " + Num.fmt(v) + " ≠ base " + Num.fmt(vBC) + " × " + Num.fmt(rate) + "% = " + Num.fmt(Num.pct(vBC, rate)));
        }
        return v;
    }

    private static void rate2026(Element part, String pTag, String expected, String label, List<String> problems) {
        BigDecimal p = Num.get(part, pTag);
        if (part != null && X.child(part, "gRed") == null && p != null && p.compareTo(new BigDecimal(expected)) != 0) {
            problems.add(label + " com alíquota " + Num.fmt(p) + "% (2026, tributação integral: " + expected + "%) [validar]");
        }
    }

    // ================================================================== utilitários

    private void add(String rule, Severity s, String field, String msg, String sug) {
        out.add(new Finding(rule, s, field, msg, sug));
    }

    /** CFOP efetivo do item (correção do usuário, se houver). */
    private String cfop(int i) {
        if (i < d.itemCount()) {
            return d.itemGet(i, "cfop");
        }
        return X.text(X.child(dets.get(i), "prod"), "CFOP");
    }

    /** CST/CSOSN efetivo do item. */
    private String cst(int i) {
        if (i < d.itemCount()) {
            return d.itemGet(i, "cst");
        }
        Element g = X.first(X.path(dets.get(i), "imposto", "ICMS"));
        String c = X.text(g, "CST");
        return Fields.NA.equals(c) ? X.text(g, "CSOSN") : c;
    }

    /** Saída para o exterior (7.xxx, exceto devoluções) ou com fim específico de exportação (5/6.501, 5/6.502). */
    static boolean isExportOutflow(String cfop) {
        if (cfop == null || !cfop.matches("\\d{4}")) {
            return false;
        }
        String suffix = cfop.substring(1);
        return (cfop.startsWith("7") && !isDevolution(cfop))
                || ((cfop.startsWith("5") || cfop.startsWith("6")) && ("501".equals(suffix) || "502".equals(suffix)));
    }

    static boolean isDevolution(String cfop) {
        if (cfop == null || !cfop.matches("\\d{4}")) {
            return false;
        }
        int s = Integer.parseInt(cfop.substring(1));
        return (s >= 201 && s <= 212) || (s >= 410 && s <= 413) || s == 503 || s == 504 || s == 553 || s == 555 || s == 556
                || (s >= 660 && s <= 662) || s == 918 || s == 919;
    }

    private static final Map<String, String> UNIT_SYNONYMS = new HashMap<String, String>();

    static {
        for (String u : new String[]{"UN", "UND", "UNID", "UNIDADE", "UNI", "UNIT", "U"}) {
            UNIT_SYNONYMS.put(u, "UN");
        }
        for (String u : new String[]{"PC", "PÇ", "PCS", "PECA", "PEÇA", "PECAS", "PEÇAS"}) {
            UNIT_SYNONYMS.put(u, "PC");
        }
        for (String u : new String[]{"KG", "KGS", "QUILO", "QUILOGRAMA"}) {
            UNIT_SYNONYMS.put(u, "KG");
        }
        for (String u : new String[]{"L", "LT", "LTS", "LITRO", "LITROS"}) {
            UNIT_SYNONYMS.put(u, "L");
        }
        for (String u : new String[]{"M", "MT", "MTS", "METRO", "METROS"}) {
            UNIT_SYNONYMS.put(u, "M");
        }
        for (String u : new String[]{"M2", "M²", "MT2"}) {
            UNIT_SYNONYMS.put(u, "M2");
        }
        for (String u : new String[]{"M3", "M³", "MT3"}) {
            UNIT_SYNONYMS.put(u, "M3");
        }
        for (String u : new String[]{"CX", "CXA", "CAIXA"}) {
            UNIT_SYNONYMS.put(u, "CX");
        }
        for (String u : new String[]{"PAR", "PR", "PARES"}) {
            UNIT_SYNONYMS.put(u, "PAR");
        }
    }

    /** Unidade normalizada: sinônimos de grafia (UN/UND/UNID, PC/PÇ...) não são conversão. */
    static String unit(String u) {
        String k = u.trim().toUpperCase().replaceAll("[\\s.]", "");
        String v = UNIT_SYNONYMS.get(k);
        return v != null ? v : k;
    }

    private static void acc(Map<String, BigDecimal> m, String k, BigDecimal v) {
        m.put(k, m.get(k).add(v));
    }

    private static String month(LocalDate dt) {
        return String.format("%02d/%d", dt.getMonthValue(), dt.getYear());
    }

    static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        int max = Math.min(parts.size(), 8);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(i));
        }
        if (parts.size() > max) {
            sb.append(" e mais ").append(parts.size() - max);
        }
        return sb.toString();
    }

    static Set<String> set(String... v) {
        return new HashSet<String>(Arrays.asList(v));
    }
}
