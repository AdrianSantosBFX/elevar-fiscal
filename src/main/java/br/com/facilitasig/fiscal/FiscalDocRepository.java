package br.com.facilitasig.fiscal;

import br.com.facilitasig.db.Database;
import br.com.facilitasig.db.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistência das notas importadas, itens, correções (overrides) e achados. */
public final class FiscalDocRepository {

    private final Database db;

    public FiscalDocRepository(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ gravação

    /** Insere a nota (com itens e achados) e devolve o id. */
    public long insert(FiscalDoc d) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("company_id", d.companyId());
                row.put("direction", d.direction().name());
                row.put("kind", d.kind().name());
                row.put("access_key", d.accessKey());
                row.put("status", d.status().name());
                row.put("duplicate_of", d.duplicateOf());
                row.put("xml_hash", d.xmlHash());
                row.put("source_path", d.sourcePath());
                row.put("imported_at", LocalDateTime.now().withNano(0).toString());
                row.put("xml", d.xml());
                for (String k : Fields.DOC) {
                    row.put(k, d.original(k));
                }
                long id = insertRow(c, "fiscal_doc", row);
                d.setId(id);
                int seq = 0;
                for (Map<String, String> it : d.items()) {
                    Map<String, Object> r = new LinkedHashMap<String, Object>();
                    r.put("doc_id", id);
                    r.put("seq", ++seq);
                    for (String k : Fields.ITEM) {
                        r.put(k, it.get(k));
                    }
                    insertRow(c, "fiscal_doc_item", r);
                }
                saveFindings(c, id, d.findings());
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao gravar a nota", e);
        }
    }

    /** Grava correções, achados e situação após edição/revalidação. */
    public void saveReview(FiscalDoc d) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                exec(c, "DELETE FROM fiscal_doc_override WHERE doc_id = ?", d.id());
                String now = LocalDateTime.now().withNano(0).toString();
                for (Map.Entry<String, String> e : d.overrides().entrySet()) {
                    Map<String, Object> r = new LinkedHashMap<String, Object>();
                    r.put("doc_id", d.id());
                    r.put("field", e.getKey());
                    r.put("original", originalOf(d, e.getKey()));
                    r.put("value", e.getValue());
                    r.put("edited_at", now);
                    insertRow(c, "fiscal_doc_override", r);
                }
                exec(c, "DELETE FROM fiscal_doc_finding WHERE doc_id = ?", d.id());
                saveFindings(c, d.id(), d.findings());
                try (PreparedStatement ps = c.prepareStatement("UPDATE fiscal_doc SET status = ? WHERE id = ?")) {
                    ps.setString(1, d.status().name());
                    ps.setLong(2, d.id());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao gravar a revisão da nota", e);
        }
    }

    private static String originalOf(FiscalDoc d, String key) {
        if (key.startsWith("item.")) {
            String[] p = key.split("\\.", 3);
            int idx = Integer.parseInt(p[1]) - 1;
            return d.itemOriginal(idx, p[2]);
        }
        return d.original(key);
    }

    private static void saveFindings(Connection c, long id, List<Finding> findings) throws SQLException {
        int seq = 0;
        for (Finding f : findings) {
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("doc_id", id);
            r.put("seq", ++seq);
            r.put("rule_id", f.ruleId());
            r.put("severity", f.severity().name());
            r.put("field", f.field());
            r.put("message", f.message());
            r.put("suggestion", f.suggestion());
            insertRow(c, "fiscal_doc_finding", r);
        }
    }

    /** Exclui a nota somente se ela for da empresa informada. Devolve false se não excluiu. */
    public boolean delete(long companyId, long id) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM fiscal_doc WHERE id = ? AND company_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, companyId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao excluir a nota", e);
        }
    }

    private static void exec(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static long insertRow(Connection c, String table, Map<String, Object> row) throws SQLException {
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        for (String k : row.keySet()) {
            if (cols.length() > 0) {
                cols.append(", ");
                marks.append(", ");
            }
            cols.append(k);
            marks.append('?');
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + table + " (" + cols + ") VALUES (" + marks + ")",
                Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            for (Object v : row.values()) {
                ps.setObject(i++, v);
            }
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                return k.next() ? k.getLong(1) : 0L;
            }
        }
    }

    // ------------------------------------------------------------------ leitura

    private static final String CORE = "id, company_id, direction, kind, access_key, status, duplicate_of, xml_hash, source_path, imported_at";

    private static String docColumns() {
        StringBuilder sb = new StringBuilder(CORE);
        for (String k : Fields.DOC) {
            sb.append(", ").append(k);
        }
        return sb.toString();
    }

    private static FiscalDoc read(ResultSet rs) throws SQLException {
        FiscalDoc d = new FiscalDoc();
        d.setId(rs.getLong("id"));
        d.setCompanyId(rs.getLong("company_id"));
        d.setDirection(Direction.valueOf(rs.getString("direction")));
        d.setKind(DocKind.valueOf(rs.getString("kind")));
        d.setAccessKey(rs.getString("access_key"));
        d.setStatus(DocStatus.valueOf(rs.getString("status")));
        d.setDuplicateOf(rs.getLong("duplicate_of"));
        d.setXmlHash(rs.getString("xml_hash"));
        d.setSourcePath(rs.getString("source_path"));
        d.setImportedAt(rs.getString("imported_at"));
        for (String k : Fields.DOC) {
            d.put(k, rs.getString(k));
        }
        return d;
    }

    /** Notas do sentido (sem XML nem itens), com correções e achados, para a grade. */
    public List<FiscalDoc> list(long companyId, Direction dir) {
        Map<Long, FiscalDoc> byId = new LinkedHashMap<Long, FiscalDoc>();
        try (Connection c = db.connect()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + docColumns()
                    + " FROM fiscal_doc WHERE company_id = ? AND direction = ? ORDER BY id")) {
                ps.setLong(1, companyId);
                ps.setString(2, dir.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        FiscalDoc d = read(rs);
                        byId.put(d.id(), d);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT o.doc_id, o.field, o.value FROM fiscal_doc_override o "
                    + "JOIN fiscal_doc d ON d.id = o.doc_id WHERE d.company_id = ? AND d.direction = ?")) {
                ps.setLong(1, companyId);
                ps.setString(2, dir.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byId.get(rs.getLong(1)).overrides().put(rs.getString(2), rs.getString(3));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT f.* FROM fiscal_doc_finding f "
                    + "JOIN fiscal_doc d ON d.id = f.doc_id WHERE d.company_id = ? AND d.direction = ? ORDER BY f.doc_id, f.seq")) {
                ps.setLong(1, companyId);
                ps.setString(2, dir.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byId.get(rs.getLong("doc_id")).findings().add(readFinding(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao listar as notas", e);
        }
        return new ArrayList<FiscalDoc>(byId.values());
    }

    private static Finding readFinding(ResultSet rs) throws SQLException {
        String sug = rs.getString("suggestion");
        return new Finding(rs.getString("rule_id"), Severity.valueOf(rs.getString("severity")),
                rs.getString("field"), rs.getString("message"), sug);
    }

    /**
     * Nota completa da empresa informada; null se não existir ou for de outra empresa.
     * É a única leitura de nota individual disponível fora do pacote: nada mostra nota alheia.
     */
    public FiscalDoc load(long companyId, long id) {
        FiscalDoc d = load(id);
        return d != null && d.companyId() == companyId ? d : null;
    }

    /** Nota completa: itens, correções, achados e XML (uso interno; a tela usa {@link #load(long, long)}). */
    FiscalDoc load(long id) {
        try (Connection c = db.connect()) {
            FiscalDoc d;
            try (PreparedStatement ps = c.prepareStatement("SELECT " + docColumns() + ", xml FROM fiscal_doc WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    d = read(rs);
                    d.setXml(rs.getString("xml"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM fiscal_doc_item WHERE doc_id = ? ORDER BY seq")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> it = FiscalDoc.newItem();
                        for (String k : Fields.ITEM) {
                            it.put(k, rs.getString(k));
                        }
                        d.items().add(it);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT field, value FROM fiscal_doc_override WHERE doc_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.overrides().put(rs.getString(1), rs.getString(2));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM fiscal_doc_finding WHERE doc_id = ? ORDER BY seq")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.findings().add(readFinding(rs));
                    }
                }
            }
            return d;
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler a nota", e);
        }
    }

    /** Id da nota original (não duplicada) com a mesma chave no sentido, ou 0. */
    public long findOriginal(long companyId, Direction dir, String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM fiscal_doc WHERE company_id = ? AND direction = ? AND access_key = ? AND status <> 'DUPLICADA'")) {
            ps.setLong(1, companyId);
            ps.setString(2, dir.name());
            ps.setString(3, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao procurar a nota", e);
        }
    }

    /** Já existe registro de duplicidade para este mesmo arquivo (mesmo hash) da nota original? */
    public boolean duplicateRecorded(long companyId, Direction dir, String key, String hash) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM fiscal_doc WHERE company_id = ? AND direction = ? AND access_key = ? AND xml_hash = ?")) {
            ps.setLong(1, companyId);
            ps.setString(2, dir.name());
            ps.setString(3, key);
            ps.setString(4, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao procurar a nota", e);
        }
    }

    // ------------------------------------------------------------------ eventos

    /** Grava o evento; devolve false se o mesmo evento (chave, tipo, sequência) já existia. */
    public boolean insertEvent(FiscalEvent ev) {
        try (Connection c = db.connect()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM fiscal_event "
                    + "WHERE company_id = ? AND access_key = ? AND event_type = ? AND seq = ?")) {
                ps.setLong(1, ev.companyId());
                ps.setString(2, ev.accessKey());
                ps.setString(3, ev.type());
                ps.setInt(4, ev.seq());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("company_id", ev.companyId());
            r.put("access_key", ev.accessKey());
            r.put("event_type", ev.type());
            r.put("seq", ev.seq());
            r.put("event_at", ev.eventAt());
            r.put("protocol_status", ev.protocolStatus());
            r.put("description", ev.description());
            r.put("xml_hash", ev.xmlHash());
            r.put("source_path", ev.sourcePath());
            r.put("imported_at", LocalDateTime.now().withNano(0).toString());
            r.put("xml", ev.xml());
            ev.setId(insertRow(c, "fiscal_event", r));
            return true;
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao gravar o evento", e);
        }
    }

    /** Eventos registrados para a chave de acesso, em ordem de tipo e sequência. */
    public List<FiscalEvent> events(long companyId, String accessKey) {
        List<FiscalEvent> out = new ArrayList<FiscalEvent>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, company_id, access_key, event_type, seq, event_at, protocol_status, description, xml_hash, source_path "
                        + "FROM fiscal_event WHERE company_id = ? AND access_key = ? ORDER BY event_type, seq")) {
            ps.setLong(1, companyId);
            ps.setString(2, accessKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FiscalEvent ev = new FiscalEvent();
                    ev.setId(rs.getLong(1));
                    ev.setCompanyId(rs.getLong(2));
                    ev.setAccessKey(rs.getString(3));
                    ev.setType(rs.getString(4));
                    ev.setSeq(rs.getInt(5));
                    ev.setEventAt(rs.getString(6));
                    ev.setProtocolStatus(rs.getString(7));
                    ev.setDescription(rs.getString(8));
                    ev.setXmlHash(rs.getString(9));
                    ev.setSourcePath(rs.getString(10));
                    out.add(ev);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler os eventos", e);
        }
        return out;
    }

    /** Notas (não duplicadas) da empresa com a chave, em qualquer sentido. */
    public List<Long> docIdsByKey(long companyId, String accessKey) {
        List<Long> out = new ArrayList<Long>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM fiscal_doc WHERE company_id = ? AND access_key = ? AND status <> 'DUPLICADA' ORDER BY id")) {
            ps.setLong(1, companyId);
            ps.setString(2, accessKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao procurar a nota", e);
        }
        return out;
    }

    public Map<DocStatus, Integer> counts(long companyId, Direction dir) {
        Map<DocStatus, Integer> m = new EnumMap<DocStatus, Integer>(DocStatus.class);
        for (DocStatus s : DocStatus.values()) {
            m.put(s, 0);
        }
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT status, COUNT(*) FROM fiscal_doc WHERE company_id = ? AND direction = ? GROUP BY status")) {
            ps.setLong(1, companyId);
            ps.setString(2, dir.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    m.put(DocStatus.valueOf(rs.getString(1)), rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao contar as notas", e);
        }
        return m;
    }
}
