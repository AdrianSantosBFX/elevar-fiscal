package br.com.facilitasig.ref;

import br.com.facilitasig.db.DataDirectory;
import br.com.facilitasig.db.Database;
import br.com.facilitasig.db.DatabaseException;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Consulta às tabelas de referência (banco {@code elevar-fiscal-ref.db}, separado do banco das empresas).
 *
 * <p>Toda consulta de código é feita na data do fato e devolve um {@link Lookup} com uma das quatro
 * situações de {@link LookupStatus}. Quando a tabela não permite afirmar (data fora da cobertura, tabela
 * não carregada, código ausente numa tabela que não traz os códigos extintos), a resposta é
 * {@link LookupStatus#SEM_COBERTURA}, nunca "encontrado".
 */
public final class ReferenceService {

    public static final String KIND_NCM = "NCM";
    public static final String KIND_CST = "CST";
    public static final String KIND_ICMS_INTER = ReferenceLoader.KIND_ICMS_INTER;
    public static final String KIND_ICMS_INTERNA = ReferenceLoader.KIND_ICMS_INTERNA;
    public static final String KIND_ISS = ReferenceLoader.KIND_ISS;

    private static final java.util.Set<String> UFS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT", "PA", "PB", "PE", "PI",
            "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO"));
    /** Origens da mercadoria (CST, Tabela A) sujeitas aos 4% interestaduais (Res. Senado 13/2012). */
    private static final java.util.Set<String> IMPORTED_ORIGINS = new java.util.HashSet<String>(
            java.util.Arrays.asList("1", "2", "3", "8"));

    private final Database db;

    public ReferenceService(Database db) {
        this.db = db;
    }

    /** Abre (e cria, se preciso) o banco de referência no arquivo indicado. */
    public static ReferenceService open(File file) {
        return new ReferenceService(new Database(file, RefSchema.migrations()));
    }

    public static ReferenceService openDefault() {
        return open(DataDirectory.referenceDatabaseFile());
    }

    /** Carrega ou atualiza as tabelas embutidas no sistema. */
    public List<ReferenceLoader.Entry> loadBundled() {
        return load(ReferenceLoader.classpath());
    }

    public List<ReferenceLoader.Entry> load(ReferenceLoader.Source source) {
        return new ReferenceLoader(db, source).loadAll();
    }

    /** Carga das tabelas embutidas com aviso de andamento (a de ISS leva alguns segundos na primeira vez). */
    public List<ReferenceLoader.Entry> loadBundled(ReferenceLoader.Progress progress) {
        return new ReferenceLoader(db, ReferenceLoader.classpath()).withProgress(progress).loadAll();
    }

    /** Importa um arquivo de alíquotas de ISS baixado do Portal NFS-e (ZIP, TXT ou CSV). */
    public ReferenceLoader.Entry importIss(File file, ReferenceLoader.Progress progress) {
        return importIss(file, ReferenceLoader.classpath(), progress);
    }

    ReferenceLoader.Entry importIss(File file, ReferenceLoader.Source source, ReferenceLoader.Progress progress) {
        return new ReferenceLoader(db, source).withProgress(progress).importIssFile(file);
    }

    public File file() {
        return db.file();
    }

    // ------------------------------------------------------------------ conjuntos

    public List<Dataset> datasets() {
        return datasetsWhere("1 = 1", null);
    }

    public List<Dataset> datasetsOfKind(String kind) {
        return datasetsWhere("kind = ?", kind);
    }

    private List<Dataset> datasetsWhere(String where, String arg) {
        List<Dataset> out = new ArrayList<Dataset>();
        String sql = "SELECT code, file, kind, title, grade, source, source_date, license, coverage_from, conclusive_from, "
                + "notes, sha256, row_count, loaded_at, origin FROM ref_dataset WHERE " + where + " ORDER BY kind, code";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (arg != null) {
                ps.setString(1, arg);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Dataset(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            SourceGrade.valueOf(rs.getString(5)), rs.getString(6), Validity.date(rs.getString(7)),
                            nz(rs.getString(8)), Validity.date(rs.getString(9)), Validity.date(rs.getString(10)),
                            nz(rs.getString(11)), rs.getString(12), rs.getInt(13), rs.getString(14), rs.getString(15)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler as tabelas de referência", e);
        }
        return out;
    }

    public Dataset dataset(String code) {
        for (Dataset d : datasets()) {
            if (d.code().equals(code)) {
                return d;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ NCM

    /** Confere um código NCM (com ou sem pontos) na data do fato. */
    public Lookup<Ncm> lookupNcm(String code, LocalDate day) {
        String digits = digits(code);
        List<Ncm> rows = digits.isEmpty() ? new ArrayList<Ncm>() : queryNcm("code = ?", digits, null, 10);
        return decide(KIND_NCM, "NCM " + (code == null ? "" : code.trim()), rows, day, new Versioned<Ncm>() {
            @Override
            public Validity validity(Ncm n) {
                return n.validity();
            }

            @Override
            public String dataset(Ncm n) {
                return n.dataset();
            }
        });
    }

    /**
     * Pesquisa por código (prefixo, com ou sem pontos) ou por palavras da descrição (todas, sem
     * diferenciar acentos e maiúsculas).
     *
     * @param vigentesEm  só códigos vigentes nesta data; nulo = todos
     * @param onlyItems   só códigos de 8 dígitos (os que vão na nota)
     */
    public List<Ncm> searchNcm(String query, LocalDate vigentesEm, boolean onlyItems, int limit) {
        String q = query == null ? "" : query.trim();
        StringBuilder where = new StringBuilder("1 = 1");
        List<String> args = new ArrayList<String>();
        if (q.replaceAll("[\\d.\\s-]", "").isEmpty() && !digits(q).isEmpty()) {
            where.append(" AND code LIKE ?");
            args.add(digits(q) + "%");
        } else {
            for (String word : searchKey(q).split(" ")) {
                if (!word.isEmpty()) {
                    where.append(" AND search_text LIKE ?");
                    args.add("%" + word + "%");
                }
            }
        }
        if (onlyItems) {
            where.append(" AND length(code) = 8");
        }
        if (vigentesEm != null) {
            where.append(" AND (vig_ini IS NULL OR vig_ini <= ?) AND (vig_fim IS NULL OR vig_fim >= ?)");
            args.add(vigentesEm.toString());
            args.add(vigentesEm.toString());
        }
        return queryNcm(where.toString(), null, args, limit);
    }

    /** O código e seus níveis superiores (capítulo, posição, subposições), do mais geral ao mais específico. */
    public List<Ncm> ncmHierarchy(String code) {
        String digits = digits(code);
        List<Ncm> out = new ArrayList<Ncm>();
        for (int len = 2; len <= digits.length(); len++) {
            List<Ncm> level = queryNcm("code = ?", digits.substring(0, len), null, 1);
            out.addAll(level);
        }
        return out;
    }

    public int ncmCount() {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM ref_ncm");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao contar a NCM", e);
        }
    }

    private List<Ncm> queryNcm(String where, String single, List<String> args, int limit) {
        List<Ncm> out = new ArrayList<Ncm>();
        String sql = "SELECT code, formatted, description, vig_ini, vig_fim, legal_act, dataset FROM ref_ncm WHERE "
                + where + " ORDER BY code, vig_ini LIMIT " + Math.max(1, limit);
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (single != null) {
                ps.setString(i++, single);
            }
            if (args != null) {
                for (String a : args) {
                    ps.setString(i++, a);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Ncm(rs.getString(1), rs.getString(2), rs.getString(3),
                            Validity.parse(rs.getString(4), rs.getString(5)), nz(rs.getString(6)), rs.getString(7)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar a NCM", e);
        }
        return out;
    }

    // ------------------------------------------------------------------ CST, CSOSN, Origem

    /** Códigos de uma tabela; {@code vigentesEm} nulo = todos, inclusive os fora de vigência. */
    public List<CstCode> cstCodes(CstTable table, LocalDate vigentesEm) {
        List<CstCode> out = new ArrayList<CstCode>();
        for (CstCode c : queryCst(table, null)) {
            if (vigentesEm == null || c.validity().contains(vigentesEm)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Confere um código na data do fato (aceita "0" para Origem e "6" ou "06" para CST de 2 dígitos). */
    public Lookup<CstCode> lookupCst(CstTable table, String code, LocalDate day) {
        String normalized = table.normalize(code);
        List<CstCode> rows = queryCst(table, normalized);
        return decide(KIND_CST, table.label() + " " + normalized, rows, day, new Versioned<CstCode>() {
            @Override
            public Validity validity(CstCode n) {
                return n.validity();
            }

            @Override
            public String dataset(CstCode n) {
                return n.dataset();
            }
        });
    }

    private List<CstCode> queryCst(CstTable table, String code) {
        List<CstCode> out = new ArrayList<CstCode>();
        String sql = "SELECT code, description, vig_ini, vig_fim, legal_basis, note, dataset FROM ref_cst WHERE tbl = ?"
                + (code == null ? "" : " AND code = ?") + " ORDER BY code, vig_ini";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table.name());
            if (code != null) {
                ps.setString(2, code);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CstCode(table, rs.getString(1), rs.getString(2),
                            Validity.parse(rs.getString(3), rs.getString(4)), rs.getString(5), rs.getString(6), rs.getString(7)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar " + table.label(), e);
        }
        return out;
    }

    // ------------------------------------------------------------------ ICMS

    private static final Versioned<IcmsRate> ICMS_VERSION = new Versioned<IcmsRate>() {
        @Override
        public Validity validity(IcmsRate r) {
            return r.validity();
        }

        @Override
        public String dataset(IcmsRate r) {
            return r.dataset();
        }
    };

    /** Alíquota interna geral (modal) da UF na data, sem o fundo de pobreza (informado à parte). */
    public Lookup<IcmsRate> icmsInternal(String uf, LocalDate day) {
        String u = uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
        if (!UFS.contains(u)) {
            return new Lookup<IcmsRate>(LookupStatus.NAO_ENCONTRADO, null, "UF inválida: " + uf);
        }
        Lookup<IcmsRate> l = decide(KIND_ICMS_INTERNA, "Alíquota interna de ICMS de " + u,
                queryIcmsInterna("uf = ?", u), day, ICMS_VERSION);
        return appended(l, l.value() == null ? "" : " Alíquota " + l.value().display() + ": é a alíquota interna geral (modal); "
                + "produtos com alíquota própria, redução de base ou ST não estão nesta tabela.");
    }

    /**
     * Alíquota interestadual de ICMS na data. Origem da mercadoria (CST, Tabela A) 1, 2, 3 ou 8 → 4% (Res.
     * Senado 13/2012, a partir de 2013); nula = considerada nacional. Mesma UF → alíquota interna.
     */
    public Lookup<IcmsRate> icmsInterstate(String from, String to, String goodsOrigin, LocalDate day) {
        String o = from == null ? "" : from.trim().toUpperCase(Locale.ROOT);
        String d = to == null ? "" : to.trim().toUpperCase(Locale.ROOT);
        if (!UFS.contains(o) || !UFS.contains(d)) {
            return new Lookup<IcmsRate>(LookupStatus.NAO_ENCONTRADO, null, "UF inválida: " + from + " → " + to);
        }
        if (o.equals(d)) {
            return icmsInternal(o, day);
        }
        String what = "Alíquota interestadual de ICMS " + o + " → " + d;
        boolean imported = goodsOrigin != null && IMPORTED_ORIGINS.contains(goodsOrigin.trim());
        if (imported) {
            List<IcmsRate> rows = queryIcmsInter("uf_origem = ? AND uf_destino = ? AND tipo = 'IMPORTADO'", o, d);
            Lookup<IcmsRate> l = decide(KIND_ICMS_INTER, what + " (importado, origem " + goodsOrigin.trim() + ")",
                    rows, day, ICMS_VERSION);
            if (l.status() != LookupStatus.FORA_DE_VIGENCIA) {
                return l;
            }
            // antes de 2013 não havia a alíquota de 4%: vale a regra geral
        }
        Lookup<IcmsRate> l = decide(KIND_ICMS_INTER, what, queryIcmsInter("uf_origem = ? AND uf_destino = ? AND tipo = 'GERAL'", o, d),
                day, ICMS_VERSION);
        if (goodsOrigin == null || goodsOrigin.trim().isEmpty()) {
            return appended(l, " Origem da mercadoria não informada: considerada nacional (importados, origem 1, 2, 3 ou 8: 4%).");
        }
        return l;
    }

    /**
     * Matriz do dia: alíquotas internas (diagonal) e interestaduais vigentes na data, para a tela. Pares sem
     * alíquota vigente ficam de fora.
     */
    public List<IcmsRate> icmsMatrix(LocalDate day, boolean imported) {
        List<IcmsRate> out = new ArrayList<IcmsRate>();
        for (IcmsRate r : queryIcmsInterna("1 = 1")) {
            if (r.validity().contains(day) && covered(KIND_ICMS_INTERNA, day)) {
                out.add(r);
            }
        }
        for (IcmsRate r : queryIcmsInter("tipo = ?", imported ? IcmsRate.IMPORTADO : IcmsRate.GERAL)) {
            if (r.validity().contains(day) && covered(KIND_ICMS_INTER, day)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Histórico da alíquota interna da UF (todas as vigências registradas). */
    public List<IcmsRate> icmsInternalHistory(String uf) {
        return queryIcmsInterna("uf = ?", uf);
    }

    private boolean covered(String kind, LocalDate day) {
        for (Dataset d : datasetsOfKind(kind)) {
            if (d.covers(day)) {
                return true;
            }
        }
        return false;
    }

    private List<IcmsRate> queryIcmsInterna(String where, String... args) {
        List<IcmsRate> out = new ArrayList<IcmsRate>();
        String sql = "SELECT uf, aliq_x100, fcp_x100, vig_ini, vig_fim, legal_basis, note, dataset FROM ref_icms_interna WHERE "
                + where + " ORDER BY uf, vig_ini";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int rate = rs.getInt(2);
                    int fcp = rs.getInt(3);
                    boolean noFcp = rs.wasNull();
                    out.add(new IcmsRate(rs.getString(1), rs.getString(1), IcmsRate.INTERNA, Rates.fromX100(rate),
                            noFcp ? null : Rates.fromX100(fcp), Validity.parse(rs.getString(4), rs.getString(5)),
                            rs.getString(6), rs.getString(7), rs.getString(8)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar a alíquota interna de ICMS", e);
        }
        return out;
    }

    private List<IcmsRate> queryIcmsInter(String where, String... args) {
        List<IcmsRate> out = new ArrayList<IcmsRate>();
        String sql = "SELECT uf_origem, uf_destino, tipo, aliq_x100, vig_ini, vig_fim, legal_basis, note, dataset "
                + "FROM ref_icms_inter WHERE " + where + " ORDER BY uf_origem, uf_destino, vig_ini";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new IcmsRate(rs.getString(1), rs.getString(2), rs.getString(3), Rates.fromX100(rs.getInt(4)),
                            null, Validity.parse(rs.getString(5), rs.getString(6)), rs.getString(7), rs.getString(8),
                            rs.getString(9)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar a alíquota interestadual de ICMS", e);
        }
        return out;
    }

    private static <T> Lookup<T> appended(Lookup<T> l, String extra) {
        return extra.isEmpty() ? l : new Lookup<T>(l.status(), l.value(), l.message() + extra);
    }

    // ------------------------------------------------------------------ ISS

    /** Pesquisa municípios por nome (sem acentos) ou código IBGE; {@code uf} nula ou vazia = todas. */
    public List<Municipality> searchMunicipalities(String text, String uf, int limit) {
        String q = text == null ? "" : text.trim();
        StringBuilder where = new StringBuilder("1 = 1");
        List<String> args = new ArrayList<String>();
        if (!q.isEmpty() && digits(q).equals(q.replaceAll("\\s", ""))) {
            where.append(" AND CAST(ibge AS TEXT) LIKE ?");
            args.add(digits(q) + "%");
        } else {
            for (String w : searchKey(q).split(" ")) {
                if (!w.isEmpty()) {
                    where.append(" AND search_text LIKE ?");
                    args.add("%" + w + "%");
                }
            }
        }
        if (uf != null && !uf.trim().isEmpty()) {
            where.append(" AND uf = ?");
            args.add(uf.trim().toUpperCase(Locale.ROOT));
        }
        List<Municipality> out = new ArrayList<Municipality>();
        String sql = "SELECT ibge, uf, name FROM ref_municipio WHERE " + where + " ORDER BY uf, search_text LIMIT " + Math.max(1, limit);
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) {
                ps.setString(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Municipality(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar municípios", e);
        }
        return out;
    }

    public Municipality municipality(int ibge) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT ibge, uf, name FROM ref_municipio WHERE ibge = ?")) {
            ps.setInt(1, ibge);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Municipality(rs.getInt(1), rs.getString(2), rs.getString(3)) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar o município", e);
        }
    }

    public int issCount() {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM ref_iss_aliquota");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao contar as alíquotas de ISS", e);
        }
    }

    /** Descrições dos subitens da lista da LC 116/2003 (ex.: "01.01" → "Análise e desenvolvimento de sistemas"). */
    public java.util.Map<String, String> lc116() {
        java.util.Map<String, String> out = new java.util.TreeMap<String, String>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT subitem, description FROM ref_lc116");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar a lista da LC 116", e);
        }
        return out;
    }

    /**
     * Alíquota de ISS do município (código IBGE) para o serviço na data. O serviço pode ser o cTribNac da
     * NFS-e nacional (6 dígitos, ex.: 010101) ou o código de 9 dígitos do Portal (01.01.01.000).
     *
     * <p>Tudo que o arquivo do Portal não permite afirmar é SEM_COBERTURA: município fora da tabela (ex.:
     * capitais com emissor próprio), serviço não parametrizado, data anterior à parametrização ou duas
     * alíquotas diferentes vigentes ao mesmo tempo.
     */
    public Lookup<IssRate> issRate(int ibge, String service, LocalDate day) {
        String d = digits(service);
        if (d.length() == 6) {
            d = d + "000";
        }
        if (d.length() != 9) {
            return new Lookup<IssRate>(LookupStatus.NAO_ENCONTRADO, null, "Código de serviço inválido: " + service
                    + " (use o cTribNac de 6 dígitos ou o código de 9 dígitos do Portal NFS-e).");
        }
        int code = Integer.parseInt(d);
        Lookup<IssRate> pre = issPreconditions(ibge, day);
        if (pre != null) {
            return pre;
        }
        Municipality m = municipality(ibge);
        List<int[]> rows = issRows("ibge = ? AND servico = ?", ibge, code);
        return decideIss(m, code, rows, day, lc116());
    }

    /** Todas as alíquotas do município na data, uma por código de serviço parametrizado. */
    public List<Lookup<IssRate>> issRatesOf(int ibge, LocalDate day) {
        List<Lookup<IssRate>> out = new ArrayList<Lookup<IssRate>>();
        Lookup<IssRate> pre = issPreconditions(ibge, day);
        if (pre != null) {
            out.add(pre);
            return out;
        }
        Municipality m = municipality(ibge);
        java.util.Map<String, String> lc = lc116();
        java.util.Map<Integer, List<int[]>> byService = new java.util.TreeMap<Integer, List<int[]>>();
        for (int[] r : issRows("ibge = ?", ibge)) {
            List<int[]> list = byService.get(r[0]);
            if (list == null) {
                list = new ArrayList<int[]>();
                byService.put(r[0], list);
            }
            list.add(r);
        }
        for (java.util.Map.Entry<Integer, List<int[]>> e : byService.entrySet()) {
            out.add(decideIss(m, e.getKey(), e.getValue(), day, lc));
        }
        return out;
    }

    private Lookup<IssRate> issPreconditions(int ibge, LocalDate day) {
        if (day == null) {
            throw new IllegalArgumentException("A consulta exige a data do fato");
        }
        if (datasetsOfKind(KIND_ISS).isEmpty()) {
            return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, null, "Tabela de alíquotas de ISS não carregada.");
        }
        if (municipality(ibge) == null) {
            return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, null, "O município " + ibge + " não está na tabela de "
                    + "alíquotas do Portal NFS-e (municípios com emissor próprio, como São Paulo, Rio de Janeiro e Brasília, "
                    + "não constam). Indeterminado: conferir na legislação municipal.");
        }
        return null;
    }

    /** Linhas {servico, vig_ini, vig_fim (0 = sem fim), aliq_x100 (-1 = sem alíquota no Portal)}. */
    private List<int[]> issRows(String where, int... args) {
        List<int[]> out = new ArrayList<int[]>();
        String sql = "SELECT servico, vig_ini, vig_fim, aliq_x100 FROM ref_iss_aliquota WHERE " + where + " ORDER BY servico, vig_ini";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setInt(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int service = rs.getInt(1);
                    int start = rs.getInt(2);
                    int end = rs.getInt(3);
                    if (rs.wasNull()) {
                        end = 0;
                    }
                    int rate = rs.getInt(4);
                    if (rs.wasNull()) {
                        rate = -1;
                    }
                    out.add(new int[]{service, start, end, rate});
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar as alíquotas de ISS", e);
        }
        return out;
    }

    private static Lookup<IssRate> decideIss(Municipality m, int service, List<int[]> rows, LocalDate day,
                                             java.util.Map<String, String> lc) {
        String svc = IssImporter.formatService(service);
        String what = "ISS de " + m.name() + "/" + m.uf() + ", serviço " + svc;
        String desc = lc.get(svc.substring(0, 5));
        if (rows.isEmpty()) {
            return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, null, what + ": o município não parametrizou este serviço "
                    + "no Portal NFS-e. Indeterminado.");
        }
        int d = IssImporter.toInt(day);
        List<int[]> valid = new ArrayList<int[]>();
        java.util.Set<Integer> rates = new java.util.TreeSet<Integer>();
        for (int[] r : rows) {
            if (r[1] <= d && (r[2] == 0 || r[2] >= d)) {
                valid.add(r);
                rates.add(r[3]);
            }
        }
        if (rates.size() == 1 && rates.contains(-1)) {
            return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, null, what + ": o Portal publicou a vigência desde "
                    + Validity.br(IssImporter.toDate(valid.get(valid.size() - 1)[1])) + " sem alíquota (campo vazio). "
                    + "Indeterminado: conferir na legislação municipal.");
        }
        if (rates.size() == 1) {
            int[] r = valid.get(valid.size() - 1);
            IssRate rate = rate(m, service, desc, r);
            return new Lookup<IssRate>(LookupStatus.ENCONTRADO, rate, what + ": " + rate.rateText() + " em " + Validity.br(day)
                    + " (" + rate.validity() + "). Fonte: Portal NFS-e (oficial).");
        }
        if (rates.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (int[] r : valid) {
                sb.append(sb.length() == 0 ? "" : "; ").append(r[3] < 0 ? "sem alíquota" : Rates.pct(Rates.fromX100(r[3])) + "%").append(" desde ")
                        .append(Validity.br(IssImporter.toDate(r[1])));
            }
            return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, null,
                    what + ": o Portal informa mais de uma alíquota vigente em " + Validity.br(day) + " (" + sb
                            + "). Indeterminado: conferir na legislação municipal.");
        }
        int first = rows.get(0)[1];
        int[] last = rows.get(rows.size() - 1);
        if (d < first) {
            return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, null, what + ": a parametrização do município no Portal "
                    + "começa em " + Validity.br(IssImporter.toDate(first)) + "; antes disso a tabela não informa a alíquota. Indeterminado.");
        }
        return new Lookup<IssRate>(LookupStatus.SEM_COBERTURA, last[3] < 0 ? null : rate(m, service, desc, last), what + ": nenhuma vigência do "
                + "Portal cobre " + Validity.br(day) + ". Indeterminado.");
    }

    private static IssRate rate(Municipality m, int service, String desc, int[] r) {
        return new IssRate(m, service, desc, Rates.fromX100(r[3]),
                new Validity(IssImporter.toDate(r[1]), r[2] == 0 ? null : IssImporter.toDate(r[2])));
    }

    // ------------------------------------------------------------------ obrigações acessórias

    /** Catálogo de obrigações acessórias (Lucro Presumido e Lucro Real), na ordem: federais, estaduais, municipais. */
    public List<Obligation> obligations() {
        List<Obligation> out = new ArrayList<Obligation>();
        String sql = "SELECT codigo, uf, nome, esfera, regimes, periodicidade, prazo_regra, prazo_texto, condicao, derivavel, "
                + "notas_sustentam, base_legal, vig_ini, vig_fim, substituida_por, observacao FROM ref_obrigacao ORDER BY "
                + "CASE esfera WHEN 'FEDERAL' THEN 1 WHEN 'ESTADUAL' THEN 2 ELSE 3 END, "
                + "CASE WHEN vig_fim IS NULL OR vig_fim >= date('now') THEN 0 ELSE 1 END, nome, uf";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Obligation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                        rs.getString(12), Validity.parse(rs.getString(13), rs.getString(14)), rs.getString(15), rs.getString(16)));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao consultar as obrigações acessórias", e);
        }
        return out;
    }

    // ------------------------------------------------------------------ decisão comum

    private interface Versioned<T> {
        Validity validity(T row);

        String dataset(T row);
    }

    private <T> Lookup<T> decide(String kind, String what, List<T> rows, LocalDate day, Versioned<T> v) {
        if (day == null) {
            throw new IllegalArgumentException("A consulta exige a data do fato");
        }
        List<Dataset> sets = datasetsOfKind(kind);
        if (sets.isEmpty()) {
            return new Lookup<T>(LookupStatus.SEM_COBERTURA, null, "Tabela " + kind + " não carregada.");
        }
        String when = Validity.br(day);
        boolean covered = false;
        for (Dataset d : sets) {
            covered |= d.covers(day);
        }
        if (!covered) {
            return new Lookup<T>(LookupStatus.SEM_COBERTURA, null, "A tabela " + kind + " carregada não cobre "
                    + when + " (cobertura a partir de " + Validity.br(earliestCoverage(sets)) + ").");
        }
        T closest = null;
        for (T row : rows) {
            if (v.validity(row).contains(day)) {
                Dataset d = find(sets, v.dataset(row));
                return new Lookup<T>(LookupStatus.ENCONTRADO, row, what + " vigente em " + when + " ("
                        + v.validity(row) + "). Fonte: " + (d == null ? v.dataset(row) : d.title() + " — " + d.grade().label())
                        + ".");
            }
            closest = row;
        }
        if (closest != null) {
            return new Lookup<T>(LookupStatus.FORA_DE_VIGENCIA, closest, what + " existe, mas não estava vigente em "
                    + when + " (vigência " + v.validity(closest) + ").");
        }
        for (Dataset d : sets) {
            if (d.covers(day) && !d.absenceConclusive(day)) {
                return new Lookup<T>(LookupStatus.SEM_COBERTURA, null, what + " não consta na tabela, mas ela só traz os "
                        + "códigos vigentes a partir de " + Validity.br(d.conclusiveFrom()) + "; em " + when
                        + " o código pode ter existido e sido extinto depois. Indeterminado.");
            }
        }
        return new Lookup<T>(LookupStatus.NAO_ENCONTRADO, null, what + " não existe na tabela.");
    }

    private static LocalDate earliestCoverage(List<Dataset> sets) {
        LocalDate min = null;
        for (Dataset d : sets) {
            if (d.coverageFrom() != null && (min == null || d.coverageFrom().isBefore(min))) {
                min = d.coverageFrom();
            }
        }
        return min;
    }

    private static Dataset find(List<Dataset> sets, String code) {
        for (Dataset d : sets) {
            if (d.code().equals(code)) {
                return d;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ utilitários

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Texto para busca: minúsculas, sem acentos e sem pontuação, palavras separadas por um espaço. */
    public static String searchKey(String text) {
        String n = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
