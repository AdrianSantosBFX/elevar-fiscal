package br.com.facilitasig.ref;

import br.com.facilitasig.db.Database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Carrega no banco de referência os conjuntos de dados embutidos no sistema ({@code /ref/manifest.tsv}).
 *
 * <p>Idempotente: um conjunto só é recarregado quando o hash SHA-256 do arquivo muda. Cada conjunto
 * é carregado numa transação própria; se o arquivo tiver erro, a versão anterior continua valendo e o
 * erro aparece no relatório. Conjuntos que saíram do manifesto são removidos.
 */
public final class ReferenceLoader {

    /** De onde vêm os arquivos (por padrão, o próprio JAR, pasta /ref). */
    public interface Source {
        InputStream open(String name) throws IOException;
    }

    public static Source classpath() {
        return name -> {
            InputStream in = ReferenceLoader.class.getResourceAsStream("/ref/" + name);
            if (in == null) {
                throw new IOException("Arquivo de referência não encontrado no sistema: /ref/" + name);
            }
            return in;
        };
    }

    public enum Action { CARREGADO, SEM_ALTERACAO, REMOVIDO, ERRO }

    /** Resultado da carga de um conjunto. */
    public static final class Entry {
        private final String dataset;
        private final Action action;
        private final int rows;
        private final String message;

        Entry(String dataset, Action action, int rows, String message) {
            this.dataset = dataset;
            this.action = action;
            this.rows = rows;
            this.message = message;
        }

        public String dataset() {
            return dataset;
        }

        public Action action() {
            return action;
        }

        public int rows() {
            return rows;
        }

        public String message() {
            return message;
        }

        @Override
        public String toString() {
            return dataset + ": " + action + " (" + rows + " linhas)" + (message.isEmpty() ? "" : " — " + message);
        }
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Andamento das cargas longas (ISS). */
    public interface Progress {
        void message(String text);
    }

    private final Database db;
    private final Source source;
    private Progress progress;

    public ReferenceLoader(Database db, Source source) {
        this.db = db;
        this.source = source;
    }

    public ReferenceLoader withProgress(Progress p) {
        this.progress = p;
        return this;
    }

    private void say(String text) {
        if (progress != null) {
            progress.message(text);
        }
    }

    /** Situação de um conjunto já carregado. */
    private static final class Loaded {
        final String hash;
        final String origin;
        final String kind;
        final java.time.LocalDate sourceDate;

        Loaded(String hash, String origin, String kind, java.time.LocalDate sourceDate) {
            this.hash = hash;
            this.origin = origin;
            this.kind = kind;
            this.sourceDate = sourceDate;
        }
    }

    public List<Entry> loadAll() {
        List<Entry> report = new ArrayList<Entry>();
        List<Dataset> manifest;
        try {
            manifest = readManifest(read("manifest.tsv"));
        } catch (IOException | RuntimeException e) {
            report.add(new Entry("manifest", Action.ERRO, 0, "Manifesto ilegível: " + e.getMessage()));
            return report;
        }
        Map<String, Loaded> loaded = loadedDatasets();
        Set<String> listed = new HashSet<String>();
        for (Dataset d : manifest) {
            listed.add(d.code());
            Loaded current = loaded.get(d.code());
            if (current != null && Dataset.ORIGIN_IMPORTED.equals(current.origin) && current.sourceDate != null
                    && d.sourceDate() != null && !current.sourceDate.isBefore(d.sourceDate())) {
                report.add(new Entry(d.code(), Action.SEM_ALTERACAO, 0, "Mantido o arquivo importado pelo usuário, de "
                        + Validity.br(current.sourceDate) + " (o embutido é de " + Validity.br(d.sourceDate()) + ")"));
                continue;
            }
            report.add(loadOne(d, current == null || !Dataset.ORIGIN_BUNDLED.equals(current.origin) ? null : current.hash));
        }
        for (String code : loaded.keySet()) {
            if (!listed.contains(code)) {
                remove(code, loaded.get(code).kind);
                report.add(new Entry(code, Action.REMOVIDO, 0, "Saiu do manifesto"));
            }
        }
        return report;
    }

    private Entry loadOne(Dataset d, String currentHash) {
        byte[] bytes;
        try {
            bytes = read(d.file());
        } catch (IOException e) {
            return new Entry(d.code(), Action.ERRO, 0, e.getMessage());
        }
        String hash = sha256(bytes);
        if (!hash.equals(currentHash)) {
            say("Carregando tabela de referência: " + d.title() + "...");
        }
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                if (hash.equals(currentHash)) {
                    updateMetadata(c, d);
                    c.commit();
                    return new Entry(d.code(), Action.SEM_ALTERACAO, rowCount(c, d.code()), "");
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM ref_dataset WHERE code = ?")) {
                    del.setString(1, d.code());
                    del.executeUpdate();
                }
                if (KIND_ISS.equals(d.kind())) {
                    IssImporter.Stats st;
                    try (InputStream in = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes))) {
                        st = loadIss(c, d.withLoad(hash, 0, LocalDateTime.now().format(STAMP)), in, d.file());
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Arquivo " + d.file() + " ilegível: " + e.getMessage(), e);
                    }
                    c.commit();
                    return new Entry(d.code(), Action.CARREGADO, st.rows, st.summary());
                }
                List<String[]> rows = parse(d, bytes);
                Dataset withLoad = d.withLoad(hash, rows.size(), LocalDateTime.now().format(STAMP));
                insertDataset(c, withLoad);
                if ("NCM".equals(d.kind())) {
                    insertNcm(c, d.code(), rows);
                } else if ("CST".equals(d.kind())) {
                    insertCst(c, d.code(), rows);
                } else if (KIND_ICMS_INTER.equals(d.kind())) {
                    insertIcmsInter(c, d.code(), rows);
                } else if (KIND_ICMS_INTERNA.equals(d.kind())) {
                    insertIcmsInterna(c, d.code(), rows);
                } else if (KIND_LC116.equals(d.kind())) {
                    insertLc116(c, d.code(), rows);
                } else if (KIND_OBRIGACAO.equals(d.kind())) {
                    insertObligations(c, d.code(), rows);
                } else {
                    throw new IllegalArgumentException("Tipo de tabela desconhecido: " + d.kind());
                }
                c.commit();
                return new Entry(d.code(), Action.CARREGADO, rows.size(), "");
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                return new Entry(d.code(), Action.ERRO, 0, e.getMessage() + " (mantida a versão anterior, se houver)");
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return new Entry(d.code(), Action.ERRO, 0, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ leitura

    private byte[] read(String name) throws IOException {
        try (InputStream raw = source.open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = raw.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static List<String> lines(byte[] bytes, boolean gzip) throws IOException {
        InputStream in = new ByteArrayInputStream(bytes);
        if (gzip) {
            in = new GZIPInputStream(in);
        }
        List<String> out = new ArrayList<String>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == '﻿') {
                    line = line.substring(1);
                }
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    static List<Dataset> readManifest(byte[] bytes) throws IOException {
        List<Dataset> out = new ArrayList<Dataset>();
        Set<String> codes = new HashSet<String>();
        for (String line : lines(bytes, false)) {
            String[] f = line.split("\t", -1);
            if (f.length != 11) {
                throw new IllegalArgumentException("Linha do manifesto com " + f.length + " campos (esperado 11): " + line);
            }
            if (!codes.add(f[0].trim())) {
                throw new IllegalArgumentException("Conjunto repetido no manifesto: " + f[0]);
            }
            out.add(new Dataset(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), SourceGrade.valueOf(f[4].trim()),
                    f[5].trim(), Validity.date(f[6]), f[7].trim(), Validity.date(f[8]), Validity.date(f[9]),
                    f[10].trim(), "", 0, ""));
        }
        return out;
    }

    private static List<String[]> parse(Dataset d, byte[] bytes) {
        int fields = fieldCount(d.kind());
        List<String[]> rows = new ArrayList<String[]>();
        try {
            int n = 0;
            for (String line : lines(bytes, d.file().endsWith(".gz"))) {
                n++;
                String[] f = line.split("\t", -1);
                if (f.length != fields) {
                    throw new IllegalArgumentException(d.file() + ", linha " + n + ": " + f.length
                            + " campos (esperado " + fields + ")");
                }
                for (int i = 0; i < f.length; i++) {
                    f[i] = f[i].trim();
                }
                rows.add(f);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo " + d.file() + " ilegível: " + e.getMessage(), e);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Arquivo " + d.file() + " sem linhas");
        }
        return rows;
    }

    static final String KIND_ICMS_INTER = "ICMS_INTER";
    static final String KIND_ICMS_INTERNA = "ICMS_INTERNA";
    static final String KIND_LC116 = "LC116";
    static final String KIND_ISS = "ISS";
    static final String KIND_OBRIGACAO = "OBRIGACAO";

    private static int fieldCount(String kind) {
        if ("NCM".equals(kind)) {
            return 6;
        } else if (KIND_ICMS_INTER.equals(kind)) {
            return 8;
        } else if (KIND_LC116.equals(kind)) {
            return 2;
        } else if (KIND_OBRIGACAO.equals(kind)) {
            return 16;
        }
        return 7; // CST e ICMS_INTERNA
    }

    // ------------------------------------------------------------------ ISS

    private IssImporter.Stats loadIss(Connection c, Dataset d, InputStream in, String name) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM ref_dataset WHERE code = ?")) {
            del.setString(1, d.code());
            del.executeUpdate();
        }
        insertDataset(c, d);
        final String title = d.title();
        IssImporter imp = new IssImporter(c, d.code(), rows -> say("Carregando " + title + ": "
                + String.format("%,d", rows).replace(',', '.') + " alíquotas..."));
        imp.begin();
        try {
            imp.read(in, name);
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo " + name + " ilegível: " + e.getMessage(), e);
        }
        IssImporter.Stats st = imp.finish();
        try (PreparedStatement ps = c.prepareStatement("UPDATE ref_dataset SET row_count = ?, notes = ? WHERE code = ?")) {
            ps.setInt(1, st.rows);
            ps.setString(2, (d.notes().isEmpty() ? "" : d.notes() + " ") + "Carga: " + st.summary());
            ps.setString(3, d.code());
            ps.executeUpdate();
        }
        return st;
    }

    /**
     * Importa um arquivo de alíquotas de ISS baixado pelo usuário no Portal NFS-e (ZIP oficial, TXT consolidado
     * ou CSV de uma UF). Substitui o conjunto "iss" inteiro; se der erro, a versão anterior continua valendo.
     * A data da fonte vem do nome do arquivo (aliquotas-municipios-AAAAMMDD...) ou, na falta, da data do arquivo.
     */
    public Entry importIssFile(java.io.File file) {
        Dataset base = null;
        try {
            for (Dataset d : readManifest(read("manifest.tsv"))) {
                if (KIND_ISS.equals(d.kind())) {
                    base = d;
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Entry("iss", Action.ERRO, 0, "Manifesto ilegível: " + e.getMessage());
        }
        if (base == null) {
            return new Entry("iss", Action.ERRO, 0, "O sistema não tem a tabela de ISS no manifesto");
        }
        java.time.LocalDate sourceDate = sourceDateOf(file);
        String hash;
        try {
            hash = sha256(java.nio.file.Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            return new Entry(base.code(), Action.ERRO, 0, e.getMessage());
        }
        Dataset d = base.imported(file.getName(), base.title(), "Arquivo importado pelo usuário: " + file.getName()
                + " (Portal NFS-e, gov.br/nfse — Alíquotas)", sourceDate)
                .withLoad(hash, 0, LocalDateTime.now().format(STAMP)).withNotes("");
        say("Importando " + file.getName() + "...");
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                IssImporter.Stats st;
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    st = importZip(c, d, file);
                } else {
                    try (InputStream in = new java.io.FileInputStream(file)) {
                        st = loadIss(c, d, in, file.getName());
                    }
                }
                c.commit();
                return new Entry(d.code(), Action.CARREGADO, st.rows, st.summary());
            } catch (SQLException | IOException | RuntimeException e) {
                c.rollback();
                return new Entry(d.code(), Action.ERRO, 0, e.getMessage() + " (mantida a versão anterior)");
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return new Entry(d.code(), Action.ERRO, 0, e.getMessage());
        }
    }

    /** No ZIP oficial vale o TXT consolidado; sem ele, todos os CSV (um por UF). */
    private IssImporter.Stats importZip(Connection c, Dataset d, java.io.File file) throws IOException, SQLException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            List<java.util.zip.ZipEntry> txt = new ArrayList<java.util.zip.ZipEntry>();
            List<java.util.zip.ZipEntry> csv = new ArrayList<java.util.zip.ZipEntry>();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                String n = e.getName().toLowerCase();
                if (n.endsWith(".txt")) {
                    txt.add(e);
                } else if (n.endsWith(".csv")) {
                    csv.add(e);
                }
            }
            if (txt.size() == 1) {
                try (InputStream in = zip.getInputStream(txt.get(0))) {
                    return loadIss(c, d, in, txt.get(0).getName());
                }
            }
            if (csv.isEmpty()) {
                throw new IllegalArgumentException("O ZIP não tem o TXT nem os CSV de alíquotas do Portal NFS-e");
            }
            try (PreparedStatement del = c.prepareStatement("DELETE FROM ref_dataset WHERE code = ?")) {
                del.setString(1, d.code());
                del.executeUpdate();
            }
            insertDataset(c, d);
            IssImporter imp = new IssImporter(c, d.code(), rows -> say("Importando: " + rows + " alíquotas..."));
            imp.begin();
            for (java.util.zip.ZipEntry e : csv) {
                try (InputStream in = zip.getInputStream(e)) {
                    imp.read(in, e.getName());
                }
            }
            IssImporter.Stats st = imp.finish();
            try (PreparedStatement ps = c.prepareStatement("UPDATE ref_dataset SET row_count = ?, notes = ? WHERE code = ?")) {
                ps.setInt(1, st.rows);
                ps.setString(2, "Carga: " + st.summary());
                ps.setString(3, d.code());
                ps.executeUpdate();
            }
            return st;
        }
    }

    static java.time.LocalDate sourceDateOf(java.io.File file) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(20\\d{6})").matcher(file.getName());
        if (m.find()) {
            try {
                return java.time.LocalDate.parse(m.group(1), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            } catch (java.time.format.DateTimeParseException ignored) {
                // segue para a data do arquivo
            }
        }
        return java.time.Instant.ofEpochMilli(file.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ gravação

    private Map<String, Loaded> loadedDatasets() {
        Map<String, Loaded> out = new HashMap<String, Loaded>();
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT code, sha256, origin, kind, source_date FROM ref_dataset")) {
            while (rs.next()) {
                out.put(rs.getString(1), new Loaded(rs.getString(2), rs.getString(3), rs.getString(4),
                        Validity.date(rs.getString(5))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler o banco de referência: " + e.getMessage(), e);
        }
        return out;
    }

    private void remove(String code, String kind) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM ref_dataset WHERE code = ?")) {
            if (KIND_ISS.equals(kind)) {
                IssImporter.clear(c);
            }
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao remover o conjunto " + code + ": " + e.getMessage(), e);
        }
    }

    private static int rowCount(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT row_count FROM ref_dataset WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static String iso(java.time.LocalDate d) {
        return d == null ? null : d.toString();
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static void insertDataset(Connection c, Dataset d) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_dataset (code, kind, title, grade, source, "
                + "source_date, license, coverage_from, conclusive_from, notes, file, sha256, row_count, loaded_at, origin) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, d.code());
            ps.setString(2, d.kind());
            ps.setString(3, d.title());
            ps.setString(4, d.grade().name());
            ps.setString(5, d.source());
            ps.setString(6, iso(d.sourceDate()));
            ps.setString(7, nullIfEmpty(d.license()));
            ps.setString(8, iso(d.coverageFrom()));
            ps.setString(9, iso(d.conclusiveFrom()));
            ps.setString(10, nullIfEmpty(d.notes()));
            ps.setString(11, d.file());
            ps.setString(12, d.sha256());
            ps.setInt(13, d.rowCount());
            ps.setString(14, d.loadedAt());
            ps.setString(15, d.origin());
            ps.executeUpdate();
        }
    }

    private static void updateMetadata(Connection c, Dataset d) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE ref_dataset SET kind = ?, title = ?, grade = ?, source = ?, "
                + "source_date = ?, license = ?, coverage_from = ?, conclusive_from = ?, notes = ?, file = ? WHERE code = ?")) {
            ps.setString(1, d.kind());
            ps.setString(2, d.title());
            ps.setString(3, d.grade().name());
            ps.setString(4, d.source());
            ps.setString(5, iso(d.sourceDate()));
            ps.setString(6, nullIfEmpty(d.license()));
            ps.setString(7, iso(d.coverageFrom()));
            ps.setString(8, iso(d.conclusiveFrom()));
            ps.setString(9, nullIfEmpty(d.notes()));
            ps.setString(10, d.file());
            ps.setString(11, d.code());
            ps.executeUpdate();
        }
    }

    /**
     * O texto de busca de cada código junta as descrições dos níveis superiores (capítulo, posição,
     * subposições), como a "descrição concatenada" oficial: "cavalos reprodutores" acha 0101.21.00.
     */
    private static void insertNcm(Connection c, String dataset, List<String[]> rows) throws SQLException {
        Map<String, String> byCode = new HashMap<String, String>();
        for (String[] f : rows) {
            byCode.put(f[0], f[2]);
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_ncm (code, formatted, description, search_text, "
                + "vig_ini, vig_fim, legal_act, dataset) VALUES (?,?,?,?,?,?,?,?)")) {
            for (String[] f : rows) {
                StringBuilder path = new StringBuilder(f[1]);
                for (int len = 2; len < f[0].length(); len++) {
                    String parent = byCode.get(f[0].substring(0, len));
                    if (parent != null) {
                        path.append(' ').append(parent);
                    }
                }
                path.append(' ').append(f[2]);
                Validity v = Validity.parse(f[3], f[4]);
                ps.setString(1, f[0]);
                ps.setString(2, f[1]);
                ps.setString(3, f[2]);
                ps.setString(4, ReferenceService.searchKey(path.toString()));
                ps.setString(5, iso(v.start()));
                ps.setString(6, iso(v.end()));
                ps.setString(7, nullIfEmpty(f[5]));
                ps.setString(8, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertCst(Connection c, String dataset, List<String[]> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_cst (tbl, code, description, vig_ini, vig_fim, "
                + "legal_basis, note, dataset) VALUES (?,?,?,?,?,?,?,?)")) {
            for (String[] f : rows) {
                CstTable table = CstTable.valueOf(f[0]);
                Validity v = Validity.parse(f[3], f[4]);
                ps.setString(1, table.name());
                ps.setString(2, table.normalize(f[1]));
                ps.setString(3, f[2]);
                ps.setString(4, iso(v.start()));
                ps.setString(5, iso(v.end()));
                ps.setString(6, f[5]);
                ps.setString(7, nullIfEmpty(f[6]));
                ps.setString(8, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static int x100(String text) {
        return IssImporter.rateX100(text);
    }

    private static Integer x100OrNull(String text) {
        return text == null || text.isEmpty() ? null : x100(text);
    }

    private static void insertIcmsInter(Connection c, String dataset, List<String[]> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_icms_inter (uf_origem, uf_destino, tipo, aliq_x100, "
                + "vig_ini, vig_fim, legal_basis, note, dataset) VALUES (?,?,?,?,?,?,?,?,?)")) {
            for (String[] f : rows) {
                Validity v = Validity.parse(f[4], f[5]);
                ps.setString(1, f[0]);
                ps.setString(2, f[1]);
                ps.setString(3, f[2]);
                ps.setInt(4, x100(f[3]));
                ps.setString(5, iso(v.start()));
                ps.setString(6, iso(v.end()));
                ps.setString(7, f[6]);
                ps.setString(8, nullIfEmpty(f[7]));
                ps.setString(9, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertIcmsInterna(Connection c, String dataset, List<String[]> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_icms_interna (uf, aliq_x100, fcp_x100, vig_ini, "
                + "vig_fim, legal_basis, note, dataset) VALUES (?,?,?,?,?,?,?,?)")) {
            for (String[] f : rows) {
                Validity v = Validity.parse(f[3], f[4]);
                ps.setString(1, f[0]);
                ps.setInt(2, x100(f[1]));
                Integer fcp = x100OrNull(f[2]);
                if (fcp == null) {
                    ps.setNull(3, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(3, fcp);
                }
                ps.setString(4, iso(v.start()));
                ps.setString(5, iso(v.end()));
                ps.setString(6, f[5]);
                ps.setString(7, nullIfEmpty(f[6]));
                ps.setString(8, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertObligations(Connection c, String dataset, List<String[]> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_obrigacao (codigo, uf, nome, esfera, regimes, "
                + "periodicidade, prazo_regra, prazo_texto, condicao, derivavel, notas_sustentam, base_legal, vig_ini, vig_fim, "
                + "substituida_por, observacao, dataset) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (String[] f : rows) {
                Obligation.parseRegimes(f[4]);              // valida a coluna
                br.com.facilitasig.oa.DeadlineRule.parse(f[6]);  // valida a regra de prazo
                Validity v = Validity.parse(f[12], f[13]);
                for (int i = 0; i < 12; i++) {
                    ps.setString(i + 1, i == 10 ? nullIfEmpty(f[i]) : f[i]);
                }
                ps.setString(13, iso(v.start()));
                ps.setString(14, iso(v.end()));
                ps.setString(15, nullIfEmpty(f[14]));
                ps.setString(16, nullIfEmpty(f[15]));
                ps.setString(17, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertLc116(Connection c, String dataset, List<String[]> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_lc116 (subitem, description, dataset) VALUES (?,?,?)")) {
            for (String[] f : rows) {
                ps.setString(1, f[0]);
                ps.setString(2, f[1]);
                ps.setString(3, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
