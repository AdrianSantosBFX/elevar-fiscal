package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validação da etapa de importação. Regras IMP-nnn (estrutura, identidade, coerência interna
 * e cadastro). Não aplica ainda as regras tributárias do catálogo lp-08; usa os valores
 * efetivos (XML + correções do usuário). Ausência de dado nunca conta como conforme.
 */
public final class DocValidator {

    private static final BigDecimal TOL_ITEMS = new BigDecimal("0.02");
    private static final BigDecimal TOL_TOTAL = new BigDecimal("0.05");

    private DocValidator() {
    }

    public static List<Finding> validate(FiscalDoc d, Company company, LocalDate today) {
        List<Finding> out = new ArrayList<Finding>();
        boolean nfe = d.kind() == DocKind.NFE;
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
        if (Fields.NA.equals(uf) || !BrDocs.isValidUf(uf)) {
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
            validateNfeTotals(d, out);
            validateNfeItems(d, company, out);
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
        return out;
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

    private static void validateNfeTotals(FiscalDoc d, List<Finding> out) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean all = true;
        for (int i = 0; i < d.itemCount(); i++) {
            BigDecimal v = dec(d.itemGet(i, "value"));
            if (v == null) {
                all = false;
            } else {
                sum = sum.add(v);
            }
        }
        BigDecimal prods = dec(d.get("products_value"));
        if (all && prods != null && sum.subtract(prods).abs().compareTo(TOL_ITEMS) > 0) {
            out.add(new Finding("IMP-007", Severity.ALERTA, "products_value",
                    "Soma dos itens (" + sum.toPlainString() + ") difere do total de produtos (" + prods.toPlainString() + ").",
                    "Conferir os valores dos itens no XML."));
        }
        BigDecimal vnf = dec(d.get("total_value"));
        if (vnf == null || prods == null) {
            out.add(new Finding("IMP-008", Severity.ERRO, "total_value", "Valor total da nota ou de produtos ausente.", null));
            return;
        }
        BigDecimal calc = prods.subtract(z(d, "discount_value")).subtract(z(d, "deson_value")).add(z(d, "st_value"))
                .add(z(d, "fcpst_value")).add(z(d, "freight_value")).add(z(d, "insurance_value")).add(z(d, "other_value"))
                .add(z(d, "ii_value")).add(z(d, "ipi_value")).add(z(d, "ipi_devol_value"));
        if (calc.subtract(vnf).abs().compareTo(TOL_TOTAL) > 0) {
            out.add(new Finding("IMP-008", Severity.ALERTA, "total_value",
                    "Valor da nota (" + vnf.toPlainString() + ") difere do calculado pelos totais (" + calc.toPlainString() + ").",
                    "Conferir descontos, frete, seguro, outras despesas e impostos no total."));
        }
    }

    private static void validateNfeItems(FiscalDoc d, Company company, List<Finding> out) {
        boolean ownEntry = BrDocs.alphanumeric(d.get("issuer_doc")).equals(BrDocs.alphanumeric(company.document()))
                && "0".equals(d.get("tp_nf"));
        String issuerUf = d.get("issuer_uf");
        String recipUf = d.get("recipient_uf");
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
            boolean okFirst = ownEntry ? (first == '1' || first == '2' || first == '3') : (first == '5' || first == '6' || first == '7');
            if (!okFirst) {
                out.add(new Finding("IMP-009", Severity.ALERTA, FiscalDoc.itemKey(i, "cfop"),
                        n + "CFOP " + cfop + " incompatível com " + (ownEntry ? "nota própria de entrada" : "o ponto de vista do emitente") + ".",
                        "Conferir o CFOP."));
            } else if (BrDocs.isValidUf(issuerUf) && BrDocs.isValidUf(recipUf)) {
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
