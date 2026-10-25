package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validação da etapa de importação. Regras IMP-nnn (estrutura, identidade, coerência interna
 * e cadastro) e, desde a v0.1.1, verificações ERR-g.n que usam apenas o XML da nota e os seus
 * eventos (ver {@link RuleCatalog}). Não aplica ainda as regras que dependem de tabelas,
 * parâmetros ou de outras notas. Ausência de dado nunca conta como conforme.
 */
public final class DocValidator {

    private static final BigDecimal TOL_ITEMS = new BigDecimal("0.02");
    private static final BigDecimal TOL_TOTAL = new BigDecimal("0.05");

    private DocValidator() {
    }

    public static List<Finding> validate(FiscalDoc d, Company company, LocalDate today) {
        return validate(d, company, today, Collections.<FiscalEvent>emptyList());
    }

    /**
     * Regras IMP-nnn (v0.0.1) seguidas das verificações ERR-g.n (v0.1.1), que releem o XML
     * original da nota e consideram os eventos registrados para a chave (cancelamento, CC-e).
     */
    public static List<Finding> validate(FiscalDoc d, Company company, LocalDate today, List<FiscalEvent> events) {
        List<Finding> out = new ArrayList<Finding>();
        boolean nfe = d.kind() == DocKind.NFE;
        Element root = parsedXml(d);
        Element ide = nfe && root != null ? X.path(nfeInf(root), "ide") : null;
        String me = BrDocs.alphanumeric(company.document());
        String issuer = BrDocs.alphanumeric(d.get("issuer_doc"));
        String recipient = BrDocs.alphanumeric(d.get("recipient_doc"));

        // ---- identidade: a nota pertence à empresa ativa neste sentido?
        boolean ownEntry = nfe && issuer.equals(me) && "0".equals(d.get("tp_nf"));
        if (d.direction() == Direction.SAIDA) {
            if (!issuer.equals(me)) {
                out.add(new Finding("IMP-003", Severity.ERRO, "issuer_doc",
                        (nfe ? "Emitente " : "Prestador ") + d.get("issuer_doc") + " é diferente da empresa ativa (" + company.documentFormatted() + ").",
                        "Importe esta nota na empresa correta ou no módulo de Entrada."));
            }
        } else if (!recipient.equals(me) && !ownEntry) {
            out.add(new Finding("IMP-003", Severity.ERRO, "recipient_doc",
                    (nfe ? "Destinatário " : "Tomador ") + d.get("recipient_doc") + " é diferente da empresa ativa (" + company.documentFormatted() + ").",
                    "Importe esta nota na empresa correta ou no módulo de Saída."));
        }

        // ---- documento autorizado e em produção
        if ("2".equals(d.get("environment"))) {
            out.add(new Finding("IMP-014", Severity.ERRO, "environment",
                    "Documento emitido em ambiente de homologação (sem valor fiscal).", "Excluir a nota."));
        }
        String cStat = d.get("protocol_status");
        if (nfe) {
            if (Fields.NA.equals(cStat)) {
                out.add(new Finding("IMP-006", Severity.ALERTA, "protocol_status",
                        "XML sem protocolo de autorização (não é nfeProc).", "Obter o XML autorizado na SEFAZ ou com o emitente."));
            } else if (cStat.equals("110") || cStat.equals("301") || cStat.equals("302") || cStat.equals("303")) {
                out.add(new Finding("IMP-005", Severity.ERRO, "protocol_status",
                        "NF-e DENEGADA (cStat " + cStat + ").", "Não usar na apuração; excluir ou manter apenas para registro."));
            } else if (!cStat.equals("100") && !cStat.equals("150")) {
                out.add(new Finding("IMP-005", Severity.ALERTA, "protocol_status",
                        "NF-e sem autorização de uso vigente (cStat " + cStat + ", ex.: 101 = cancelada).", "Conferir a situação na SEFAZ."));
            }
        }

        // ---- chave de acesso
        if (nfe) {
            validateKey(d, out);
        }

        // ---- participantes
        checkDoc(out, "issuer_doc", d.get("issuer_doc"), nfe ? "emitente" : "prestador");
        checkDoc(out, "recipient_doc", d.get("recipient_doc"), nfe ? "destinatário" : "tomador");
        String uf = d.partyGet("uf");
        String ibge = d.partyGet("ibge");
        if ("EX".equalsIgnoreCase(uf) || (!nfe && root != null && foreignTaker(root) && d.direction() == Direction.SAIDA)) {
            // participante no exterior: sem UF/município brasileiros (ver ERR-2.6)
        } else if (Fields.NA.equals(uf) || !BrDocs.isValidUf(uf)) {
            out.add(new Finding("IMP-015", Severity.ALERTA, d.partyPrefix() + "uf",
                    "UF do participante ausente ou inválida.", "Informar a UF do participante."));
        } else if (!Fields.NA.equals(ibge) && !BrDocs.isValidIbge(ibge, uf)) {
            out.add(new Finding("IMP-015", Severity.ALERTA, d.partyPrefix() + "ibge",
                    "Código IBGE do município não confere com a UF.", "Corrigir o município ou a UF do participante."));
        } else if (Fields.NA.equals(ibge)) {
            out.add(new Finding("IMP-015", Severity.ALERTA, d.partyPrefix() + "ibge",
                    "Município do participante não informado.", "Informar o código IBGE do município."));
        }

        // ---- datas
        LocalDate issued = date(d.get("issued_at"));
        LocalDate entryExit = date(d.get("entry_exit_date"));
        if (issued == null) {
            out.add(new Finding("IMP-010", Severity.ERRO, "issued_at", "Data de emissão ausente ou inválida.", null));
        } else if (issued.isAfter(today)) {
            out.add(new Finding("IMP-010", Severity.ERRO, "issued_at", "Data de emissão no futuro (" + issued + ").", null));
        }
        if (entryExit == null) {
            out.add(new Finding("IMP-011", Severity.ERRO, "entry_exit_date",
                    "Data de entrada/saída ausente ou inválida.", "Informar a data (dd/mm/aaaa)."));
        } else if (issued != null && d.direction() == Direction.ENTRADA && entryExit.isBefore(issued)) {
            out.add(new Finding("IMP-011", Severity.ALERTA, "entry_exit_date",
                    "Data de entrada anterior à emissão.", "Conferir a data de entrada."));
        }

        // ---- totais e itens
        if (d.itemCount() == 0) {
            out.add(new Finding("IMP-007", Severity.ERRO, "items", "Documento sem itens.", null));
        }
        if (nfe) {
            validateNfeTotals(d, root, out);
            validateNfeItems(d, ide, out);
        } else {
            if (Fields.NA.equals(d.get("service_code"))) {
                out.add(new Finding("IMP-012", Severity.ALERTA, "service_code",
                        "Código de tributação nacional (cTribNac) ausente.", "Conferir a classificação do serviço."));
            }
            BigDecimal iss = dec(d.get("iss_value"));
            if ("SIM".equals(d.get("iss_retained")) && (iss == null || iss.signum() == 0)) {
                out.add(new Finding("IMP-013", Severity.ALERTA, "iss_value",
                        "ISS marcado como retido, mas sem valor de ISS.", "Conferir a retenção e o valor do ISS."));
            }
            if (Fields.NA.equals(d.get("total_value")) || dec(d.get("total_value")) == null) {
                out.add(new Finding("IMP-007", Severity.ERRO, "total_value", "Valor do serviço ausente.", null));
            }
        }

        // ---- v0.1.1: verificações que leem apenas o XML e os eventos
        if (root != null) {
            signature(root, out);
            if (nfe) {
                eventChecks(events, out);
                NfeXmlChecks.run(d, root, company, out);
            } else {
                NfseXmlChecks.run(d, root, company, out);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ v0.1.1

    /** XML original relido para as verificações; null se não houver ou não for legível. */
    static Element parsedXml(FiscalDoc d) {
        if (d.kind() == DocKind.DESCONHECIDO || d.xml() == null || d.xml().trim().isEmpty()) {
            return null;
        }
        try {
            return FiscalService.parseXml(d.xml().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static Element nfeInf(Element root) {
        Element nfe = "NFe".equals(X.name(root)) ? root : X.child(root, "NFe");
        return X.child(nfe, "infNFe");
    }

    private static boolean foreignTaker(Element nfseRoot) {
        Element toma = X.path(nfseRoot, "infNFSe", "DPS", "infDPS", "toma");
        return toma != null && (X.child(toma, "NIF") != null || X.child(toma, "cNaoNIF") != null || X.path(toma, "end", "endExt") != null);
    }

    /** ERR-1.6 — assinatura digital. */
    private static void signature(Element root, List<Finding> out) {
        SignatureCheck.Result r = SignatureCheck.verify(root.getOwnerDocument());
        switch (r.outcome) {
            case VALIDA:
                return;
            case AUSENTE:
                out.add(new Finding("ERR-1.6", Severity.ALERTA, "assinatura", r.detail,
                        "Obter o XML original (autorizado e assinado) com o emitente ou no portal."));
                return;
            case NAO_VERIFICADA:
                out.add(new Finding("ERR-1.6", Severity.ALERTA, "assinatura", "Assinatura não verificada: " + r.detail,
                        "Conferir o XML no portal da SEFAZ/NFS-e."));
                return;
            default:
                out.add(new Finding("ERR-1.6", Severity.ERRO, "assinatura", "Assinatura digital inválida: " + r.detail,
                        "Não usar este arquivo; obter o XML original com o emitente ou no portal."));
        }
    }

    private static final String[] CCE_FORBIDDEN = {"valor", "base de calculo", "aliquota", "quantidade", "preco", "desconto",
            "imposto", "icms", "ipi", "cnpj", "cpf", "razao social", "remetente", "destinatario", "data de emissao", "data de saida"};

    /** ERR-1.1 (cancelamento por evento) e ERR-1.10 (CC-e com campo vedado). */
    private static void eventChecks(List<FiscalEvent> events, List<Finding> out) {
        FiscalEvent lastCce = null;
        for (FiscalEvent ev : events) {
            boolean registered = "135".equals(ev.protocolStatus()) || "136".equals(ev.protocolStatus()) || "155".equals(ev.protocolStatus());
            if (ev.isCancellation() && registered) {
                out.add(new Finding("ERR-1.1", Severity.ERRO, "evento", "NF-e CANCELADA por evento registrado em "
                                + shortDate(ev.eventAt()) + " (cStat " + ev.protocolStatus() + ")"
                                + (Fields.NA.equals(ev.description()) ? "." : ": " + ev.description() + "."),
                        "Não usar na apuração. Na EFD: COD_SIT 02 (ou 03 se o período já tiver sido entregue)."));
            } else if (ev.isCorrection() && registered && (lastCce == null || ev.seq() > lastCce.seq())) {
                lastCce = ev;
            }
        }
        if (lastCce != null && !Fields.NA.equals(lastCce.description())) {
            String text = java.text.Normalizer.normalize(lastCce.description().toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            List<String> hits = new ArrayList<String>();
            for (String term : CCE_FORBIDDEN) {
                if (text.matches("(?s).*\\b" + term + "\\b.*")) {
                    hits.add(term);
                }
            }
            if (!hits.isEmpty()) {
                out.add(new Finding("ERR-1.10", Severity.ALERTA, "evento", "Carta de correção nº " + lastCce.seq() + " menciona "
                                + NfeXmlChecks.join(hits) + ". CC-e não corrige valores, base, alíquota, quantidade, participante nem datas: "
                                + "a correção não tem efeito sobre esses campos.",
                        "Se o erro é de valor ou de participante, exigir nota complementar, devolução ou cancelamento."));
            }
        }
    }

    private static String shortDate(String s) {
        LocalDate dt = date(s);
        return dt == null ? s : String.format("%02d/%02d/%d", dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear());
    }

    /**
     * A nota pertence à empresa no sentido informado? Saída: emitente (prestador) é a empresa.
     * Entrada: destinatário (tomador) é a empresa, ou nota própria de entrada (emitente = empresa
     * e tpNF = 0). Compara o CNPJ completo: matriz e filiais são empresas diferentes.
     */
    public static boolean belongsTo(FiscalDoc d, Direction direction, Company company) {
        String me = BrDocs.alphanumeric(company.document());
        String issuer = BrDocs.alphanumeric(d.original("issuer_doc"));
        String recipient = BrDocs.alphanumeric(d.original("recipient_doc"));
        if (direction == Direction.SAIDA) {
            return issuer.equals(me);
        }
        boolean ownEntry = d.kind() == DocKind.NFE && issuer.equals(me) && "0".equals(d.original("tp_nf"));
        return recipient.equals(me) || ownEntry;
    }

    public static DocStatus statusOf(List<Finding> findings) {
        DocStatus s = DocStatus.CONFORME;
        for (Finding f : findings) {
            if (f.severity() == Severity.ERRO) {
                return DocStatus.ERRO;
            }
            s = DocStatus.ALERTA;
        }
        return s;
    }

    // ------------------------------------------------------------------ partes

    /** Dígito verificador da chave de 44 posições (módulo 11, pesos 2 a 9 da direita para a esquerda). */
    public static int keyCheckDigit(String first43) {
        int sum = 0;
        int w = 2;
        for (int i = first43.length() - 1; i >= 0; i--) {
            sum += (first43.charAt(i) - '0') * w;
            w = w == 9 ? 2 : w + 1;
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private static void validateKey(FiscalDoc d, List<Finding> out) {
        String k = d.accessKey();
        if (!k.matches("\\d{44}")) {
            out.add(new Finding("IMP-002", Severity.ERRO, "access_key", "Chave de acesso não tem 44 dígitos.", null));
            return;
        }
        if (keyCheckDigit(k.substring(0, 43)) != k.charAt(43) - '0') {
            out.add(new Finding("IMP-002", Severity.ERRO, "access_key", "Dígito verificador da chave de acesso inválido.", null));
        }
        String issuer = BrDocs.alphanumeric(d.original("issuer_doc"));
        if (!issuer.isEmpty() && !Fields.NA.equals(d.original("issuer_doc")) && !k.substring(6, 20).equals(issuer)) {
            out.add(new Finding("IMP-002", Severity.ERRO, "access_key", "CNPJ da chave difere do CNPJ do emitente.", null));
        }
        if (!k.substring(20, 22).equals(pad(d.original("model"), 2))) {
            out.add(new Finding("IMP-002", Severity.ERRO, "access_key", "Modelo da chave difere do modelo do documento.", null));
        }
        if (!stripZeros(k.substring(25, 34)).equals(stripZeros(d.original("number")))) {
            out.add(new Finding("IMP-002", Severity.ERRO, "access_key", "Número da chave difere do número da nota.", null));
        }
    }

    private static void validateNfeTotals(FiscalDoc d, Element root, List<Finding> out) {
        // Itens fora do total (indTot = 0) e serviços com ISSQN (NF-e conjugada) não entram no vProd.
        Element inf = root == null ? null : nfeInf(root);
        List<Element> dets = X.children(inf, "det");
        boolean aligned = dets.size() == d.itemCount();
        BigDecimal sumAll = BigDecimal.ZERO;
        BigDecimal sumIn = BigDecimal.ZERO;
        BigDecimal sumGoodsIn = BigDecimal.ZERO;
        BigDecimal outOfTotal = BigDecimal.ZERO;
        boolean all = true;
        for (int i = 0; i < d.itemCount(); i++) {
            BigDecimal v = dec(d.itemGet(i, "value"));
            if (v == null) {
                all = false;
                continue;
            }
            boolean in = !aligned || !"0".equals(X.text(X.child(dets.get(i), "prod"), "indTot"));
            boolean service = aligned && X.path(dets.get(i), "imposto", "ISSQN") != null;
            sumAll = sumAll.add(v);
            if (in) {
                sumIn = sumIn.add(v);
                if (!service) {
                    sumGoodsIn = sumGoodsIn.add(v);
                }
            } else {
                outOfTotal = outOfTotal.add(v);
            }
        }
        BigDecimal prods = dec(d.get("products_value"));
        if (all && prods != null) {
            boolean okIn = sumIn.subtract(prods).abs().compareTo(TOL_ITEMS) <= 0
                    || sumGoodsIn.subtract(prods).abs().compareTo(TOL_ITEMS) <= 0;
            boolean okAll = sumAll.subtract(prods).abs().compareTo(TOL_ITEMS) <= 0;
            if (!okIn && okAll && outOfTotal.signum() > 0) {
                out.add(new Finding("ERR-5.4", Severity.ALERTA, "products_value",
                        "Itens marcados como fora do total (indTot = 0, " + outOfTotal.toPlainString() + ") foram somados ao total de produtos ("
                                + prods.toPlainString() + ").", "Conferir o indicador indTot dos itens e o total da nota."));
            } else if (!okIn) {
                out.add(new Finding("IMP-007", Severity.ALERTA, "products_value",
                        "Soma dos itens (" + sumIn.toPlainString() + ") difere do total de produtos (" + prods.toPlainString() + ").",
                        "Conferir os valores dos itens no XML."));
            }
        }
        String finNFe = inf == null ? Fields.NA : X.text(inf, "ide", "finNFe");
        BigDecimal vnf = dec(d.get("total_value"));
        if (vnf == null || prods == null) {
            out.add(new Finding("IMP-008", Severity.ERRO, "total_value", "Valor total da nota ou de produtos ausente.", null));
            return;
        }
        BigDecimal calc = prods.subtract(z(d, "discount_value")).subtract(z(d, "deson_value")).add(z(d, "st_value"))
                .add(z(d, "fcpst_value")).add(z(d, "freight_value")).add(z(d, "insurance_value")).add(z(d, "other_value"))
                .add(z(d, "ii_value")).add(z(d, "ipi_value")).add(z(d, "ipi_devol_value"));
        BigDecimal vServ = inf == null ? null : dec(X.text(inf, "total", "ISSQNtot", "vServ"));
        boolean okWithServ = vServ != null && calc.add(vServ).subtract(vnf).abs().compareTo(TOL_TOTAL) <= 0;
        // Complementar (2) e ajuste (3) podem não seguir a fórmula do vNF (ver ERR-4.7).
        boolean adjustment = "2".equals(finNFe) || "3".equals(finNFe);
        if (!adjustment && !okWithServ && calc.subtract(vnf).abs().compareTo(TOL_TOTAL) > 0) {
            out.add(new Finding("IMP-008", Severity.ALERTA, "total_value",
                    "Valor da nota (" + vnf.toPlainString() + ") difere do calculado pelos totais (" + calc.toPlainString() + ").",
                    "Conferir descontos, frete, seguro, outras despesas e impostos no total."));
        }
    }

    private static void validateNfeItems(FiscalDoc d, Element ide, List<Finding> out) {
        // O CFOP segue o tipo da nota (tpNF): 0 = entrada (1/2/3), 1 = saída (5/6/7), seja qual for o emitente.
        boolean entryNote = "0".equals(d.get("tp_nf"));
        String issuerUf = d.get("issuer_uf");
        String recipUf = d.get("recipient_uf");
        // Venda presencial a consumidor final de outra UF é operação interna (indPres = 1 e indFinal = 1).
        boolean presentialFinal = ide != null && "1".equals(X.text(ide, "indPres")) && "1".equals(X.text(ide, "indFinal"));
        for (int i = 0; i < d.itemCount(); i++) {
            String n = "Item " + (i + 1) + ": ";
            String ncm = d.itemGet(i, "ncm");
            if (!ncm.matches("\\d{8}") && !ncm.equals("00")) {
                out.add(new Finding("IMP-009", Severity.ALERTA, FiscalDoc.itemKey(i, "ncm"),
                        n + "NCM ausente ou inválido (" + ncm + ").", "Informar o NCM de 8 dígitos."));
            }
            String cfop = d.itemGet(i, "cfop");
            if (!cfop.matches("\\d{4}")) {
                out.add(new Finding("IMP-009", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"),
                        n + "CFOP ausente ou inválido (" + cfop + ").", "Informar o CFOP de 4 dígitos."));
                continue;
            }
            char first = cfop.charAt(0);
            boolean okFirst = entryNote ? (first == '1' || first == '2' || first == '3') : (first == '5' || first == '6' || first == '7');
            if (!okFirst) {
                out.add(new Finding("IMP-009", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"),
                        n + "CFOP " + cfop + " incompatível com nota de " + (entryNote ? "entrada (tpNF = 0)" : "saída (tpNF = 1)") + ".",
                        "Conferir o CFOP."));
            } else if (BrDocs.isValidUf(issuerUf) && BrDocs.isValidUf(recipUf) && !presentialFinal) {
                boolean same = issuerUf.equalsIgnoreCase(recipUf);
                if ((first == '5' || first == '1') && !same) {
                    out.add(new Finding("IMP-016", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"),
                            n + "CFOP " + cfop + " é de operação interna, mas as UFs diferem (" + issuerUf + " → " + recipUf + ").",
                            "Conferir o CFOP ou a UF do participante."));
                } else if ((first == '6' || first == '2') && same) {
                    out.add(new Finding("IMP-016", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"),
                            n + "CFOP " + cfop + " é interestadual, mas as UFs são iguais (" + issuerUf + ").",
                            "Conferir o CFOP ou a UF do participante."));
                }
            }
        }
    }

    private static void checkDoc(List<Finding> out, String field, String doc, String role) {
        if (Fields.NA.equals(doc)) {
            return;
        }
        String a = BrDocs.alphanumeric(doc);
        boolean ok = a.length() == 14 ? BrDocs.isValidCnpj(a) : a.length() == 11 ? BrDocs.isValidCpf(a) : true;
        if (!ok) {
            out.add(new Finding("IMP-004", Severity.ALERTA, field,
                    "CNPJ/CPF do " + role + " com dígito verificador inválido (" + doc + ").", "Conferir o documento do " + role + "."));
        }
    }

    // ------------------------------------------------------------------ utilitários

    public static LocalDate date(String s) {
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static BigDecimal dec(String s) {
        if (s == null || Fields.NA.equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal z(FiscalDoc d, String key) {
        BigDecimal v = dec(d.get(key));
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String pad(String s, int n) {
        String d = s == null ? "" : s;
        while (d.length() < n) {
            d = "0" + d;
        }
        return d;
    }

    private static String stripZeros(String s) {
        String t = s == null ? "" : s.replaceFirst("^0+", "");
        return t;
    }
}
