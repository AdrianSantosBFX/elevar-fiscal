package br.com.facilitasig.company;

import java.util.HashMap;
import java.util.Map;

/** Utilitários de documentos brasileiros: CNPJ (inclusive alfanumérico), CPF, CEP, CNAE, UF e IBGE. */
public final class BrDocs {

    private static final Map<String, String> UF_CODES = new HashMap<String, String>();

    static {
        String[] pairs = {"RO11", "AC12", "AM13", "RR14", "PA15", "AP16", "TO17", "MA21", "PI22", "CE23",
                "RN24", "PB25", "PE26", "AL27", "SE28", "BA29", "MG31", "ES32", "RJ33", "SP35", "PR41",
                "SC42", "RS43", "MS50", "MT51", "GO52", "DF53"};
        for (String p : pairs) {
            UF_CODES.put(p.substring(0, 2), p.substring(2));
        }
    }

    private BrDocs() {
    }

    public static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Mantém letras e dígitos, em maiúsculas. */
    public static String alphanumeric(String s) {
        return s == null ? "" : s.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
    }

    public static boolean isValidUf(String uf) {
        return uf != null && UF_CODES.containsKey(uf.trim().toUpperCase());
    }

    public static String[] ufs() {
        String[] all = UF_CODES.keySet().toArray(new String[0]);
        java.util.Arrays.sort(all);
        return all;
    }

    /** Código IBGE de 7 dígitos coerente com a UF (2 primeiros dígitos). */
    public static boolean isValidIbge(String code, String uf) {
        String d = digits(code);
        return d.length() == 7 && isValidUf(uf) && d.startsWith(UF_CODES.get(uf.trim().toUpperCase()));
    }

    public static boolean isValidCep(String cep) {
        return digits(cep).length() == 8;
    }

    public static String formatCep(String cep) {
        String d = digits(cep);
        return d.length() == 8 ? d.substring(0, 5) + "-" + d.substring(5) : cep;
    }

    public static String formatCnae(String code) {
        String d = digits(code);
        if (d.length() != 7) {
            return code;
        }
        return d.substring(0, 2) + "." + d.substring(2, 4) + "-" + d.substring(4, 5) + "-" + d.substring(5);
    }

    // ---- CNPJ

    private static int charValue(char c) {
        return c - '0'; // '0'..'9' => 0..9; 'A'..'Z' => 17..42 (regra do CNPJ alfanumérico)
    }

    private static char dv(String base, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += charValue(base.charAt(i)) * weights[i];
        }
        int rest = sum % 11;
        return (char) ('0' + (rest < 2 ? 0 : 11 - rest));
    }

    private static final int[] W1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] W2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public static boolean isValidCnpj(String cnpj) {
        String c = alphanumeric(cnpj);
        if (!c.matches("[0-9A-Z]{12}[0-9]{2}") || c.matches("(.)\\1{13}")) {
            return false;
        }
        char d1 = dv(c.substring(0, 12), W1);
        char d2 = dv(c.substring(0, 12) + d1, W2);
        return c.charAt(12) == d1 && c.charAt(13) == d2;
    }

    /** Dois dígitos verificadores de uma base de 12 caracteres (útil em testes e geração). */
    public static String cnpjCheckDigits(String base12) {
        String b = alphanumeric(base12);
        char d1 = dv(b, W1);
        char d2 = dv(b + d1, W2);
        return "" + d1 + d2;
    }

    /** UF a partir do código IBGE de município (2 primeiros dígitos), ou "" se desconhecida. */
    public static String ufFromIbge(String ibge) {
        String d = digits(ibge);
        if (d.length() < 2) {
            return "";
        }
        for (Map.Entry<String, String> e : UF_CODES.entrySet()) {
            if (e.getValue().equals(d.substring(0, 2))) {
                return e.getKey();
            }
        }
        return "";
    }

    public static String formatCnpj(String cnpj) {
        String c = alphanumeric(cnpj);
        if (c.length() != 14) {
            return cnpj;
        }
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/"
                + c.substring(8, 12) + "-" + c.substring(12);
    }

    /** Ordem do estabelecimento (posições 9 a 12): 0001 = matriz. */
    public static String cnpjOrder(String cnpj) {
        String c = alphanumeric(cnpj);
        return c.length() == 14 ? c.substring(8, 12) : "";
    }

    public static String cnpjRoot(String cnpj) {
        String c = alphanumeric(cnpj);
        return c.length() == 14 ? c.substring(0, 8) : "";
    }

    // ---- CPF

    public static boolean isValidCpf(String cpf) {
        String c = digits(cpf);
        if (c.length() != 11 || c.matches("(.)\\1{10}")) {
            return false;
        }
        for (int t = 9; t <= 10; t++) {
            int sum = 0;
            for (int i = 0; i < t; i++) {
                sum += (c.charAt(i) - '0') * (t + 1 - i);
            }
            int d = (sum * 10) % 11;
            if (d == 10) {
                d = 0;
            }
            if (c.charAt(t) - '0' != d) {
                return false;
            }
        }
        return true;
    }

    public static String formatCpf(String cpf) {
        String c = digits(cpf);
        return c.length() == 11 ? c.substring(0, 3) + "." + c.substring(3, 6) + "." + c.substring(6, 9) + "-" + c.substring(9) : cpf;
    }

    public static String formatDocument(IdType type, String doc) {
        switch (type) {
            case CNPJ: return formatCnpj(doc);
            case CPF: return formatCpf(doc);
            default: return doc;
        }
    }
}
