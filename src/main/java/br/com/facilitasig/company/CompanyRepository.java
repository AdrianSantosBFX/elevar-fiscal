package br.com.facilitasig.company;

import br.com.facilitasig.db.Database;
import br.com.facilitasig.db.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistência das empresas no SQLite interno. */
public final class CompanyRepository {

    private static final String NONE = Declared.NONE_TEXT;
    private static final String INFORMED = "INFORMADO";
    private static final String NOT_HAVE = "NAO_POSSUI";

    private final Database db;

    public CompanyRepository(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ gravação

    /** Valida e grava (insere ou atualiza). Devolve a empresa com o id preenchido. */
    public Company save(Company c) {
        List<String> errors = CompanyValidator.validate(c);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);
            try {
                String now = LocalDateTime.now().withNano(0).toString();
                Map<String, Object> row = toRow(c);
                if (c.id() == null) {
                    row.put("created_at", now);
                    row.put("updated_at", now);
                    c.setId(insert(conn, "company", row));
                } else {
                    row.put("updated_at", now);
                    update(conn, row, c.id());
                    for (String t : new String[]{"company_cnae", "company_state_reg", "company_regime"}) {
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + t + " WHERE company_id = ?")) {
                            ps.setLong(1, c.id());
                            ps.executeUpdate();
                        }
                    }
                }
                saveChildren(conn, c);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE") && e.getMessage().contains("document")) {
                throw new IllegalArgumentException("Já existe uma empresa cadastrada com este documento.");
            }
            throw new DatabaseException("Falha ao gravar a empresa", e);
        }
        return c;
    }

    private Map<String, Object> toRow(Company c) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("id_type", c.idType().name());
        r.put("document", c.document());
        r.put("establishment", c.establishment().name());
        r.put("cnpj_root", c.idType() == IdType.CNPJ ? c.cnpjRoot() : "NÃO SE APLICA");
        r.put("legal_name", c.legalName());
        putDeclared(r, "trade_name", c.tradeName());
        r.put("opening_date", c.openingDate().toString());
        r.put("size", c.size().name());
        r.put("legal_nature_code", c.legalNatureCode());
        r.put("legal_nature_description", c.legalNatureDescription());
        r.put("cad_situation", c.situation().name());
        r.put("cad_situation_date", c.situationDate().toString());
        putDeclared(r, "cad_reason", c.situationReason());
        putDeclared(r, "special_situation", c.specialSituation());
        putDeclared(r, "special_situation_date", map(c.specialSituationDate()));
        r.put("street", c.street());
        r.put("number", c.number());
        putDeclared(r, "complement", c.complement());
        r.put("district", c.district());
        r.put("zip", c.zip());
        r.put("city", c.city());
        r.put("ibge_code", c.ibgeCode());
        r.put("uf", c.uf());
        putDeclared(r, "email", c.email());
        putDeclared(r, "phone", c.phone());
        putDeclared(r, "efr", c.efr());
        putDeclared(r, "website", c.website());
        putDeclared(r, "company_group", c.companyGroup());
        putDeclared(r, "nire", c.nire());
        putDeclared(r, "other_identifiers", c.otherIdentifiers());
        r.put("main_cnae_code", c.mainCnae().code());
        r.put("main_cnae_description", c.mainCnae().description());
        r.put("secondary_cnae_status", c.secondaryCnaes().isEmpty() ? NOT_HAVE : INFORMED);
        putDeclared(r, "municipal_reg", c.municipalRegistration());
        putDeclared(r, "municipal_reg_date", map(c.municipalRegistrationDate()));
        return r;
    }

    private static Declared<String> map(Declared<LocalDate> d) {
        return d.isPresent() ? Declared.of(d.get().toString()) : Declared.<String>none();
    }

    private static void putDeclared(Map<String, Object> row, String col, Declared<String> d) {
        row.put(col, d.isPresent() ? d.get() : NONE);
        row.put(col + "_status", d.isPresent() ? INFORMED : NOT_HAVE);
    }

    private void saveChildren(Connection conn, Company c) throws SQLException {
        int seq = 0;
        for (Cnae n : c.secondaryCnaes()) {
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("company_id", c.id());
            r.put("seq", ++seq);
            r.put("code", n.code());
            r.put("description", n.description());
            insert(conn, "company_cnae", r);
        }
        for (StateRegistration s : c.stateRegistrations()) {
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("company_id", c.id());
            r.put("uf", s.uf());
            r.put("situation", s.situation().name());
            r.put("number", s.number());
            putDeclared(r, "reg_date", map(s.registrationDate()));
            insert(conn, "company_state_reg", r);
        }
        for (RegimePeriod p : c.regimePeriods()) {
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("company_id", c.id());
            r.put("regime", p.regime().name());
            r.put("start_date", p.start().toString());
            putDeclared(r, "end_date", map(p.end()));
            putDeclared(r, "note", p.note());
            insert(conn, "company_regime", r);
        }
    }

    private static long insert(Connection conn, String table, Map<String, Object> row) throws SQLException {
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
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + table + " (" + cols + ") VALUES (" + marks + ")", Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, row);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private static void update(Connection conn, Map<String, Object> row, long id) throws SQLException {
        StringBuilder set = new StringBuilder();
        for (String k : row.keySet()) {
            if (set.length() > 0) {
                set.append(", ");
            }
            set.append(k).append(" = ?");
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE company SET " + set + " WHERE id = ?")) {
            int i = bind(ps, row);
            ps.setLong(i, id);
            ps.executeUpdate();
        }
    }

    private static int bind(PreparedStatement ps, Map<String, Object> row) throws SQLException {
        int i = 1;
        for (Object v : row.values()) {
            ps.setObject(i++, v);
        }
        return i;
    }

    // ------------------------------------------------------------------ leitura

    public List<Company> findAll() {
        List<Company> list = new ArrayList<Company>();
        try (Connection conn = db.connect(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM company ORDER BY cnpj_root, document")) {
            while (rs.next()) {
                list.add(read(conn, rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao listar empresas", e);
        }
        return list;
    }

    public Company findById(long id) {
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement("SELECT * FROM company WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(conn, rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler a empresa", e);
        }
    }

    public Company findByDocument(IdType type, String document) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM company WHERE id_type = ? AND document = ?")) {
            ps.setString(1, type.name());
            ps.setString(2, BrDocs.alphanumeric(document));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(conn, rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler a empresa", e);
        }
    }

    public void delete(long id) {
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement("DELETE FROM company WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao excluir a empresa", e);
        }
    }

    private static Declared<String> text(ResultSet rs, String col) throws SQLException {
        return NOT_HAVE.equals(rs.getString(col + "_status")) ? Declared.<String>none() : Declared.of(rs.getString(col));
    }

    private static Declared<LocalDate> date(ResultSet rs, String col) throws SQLException {
        return NOT_HAVE.equals(rs.getString(col + "_status")) ? Declared.<LocalDate>none()
                : Declared.of(LocalDate.parse(rs.getString(col)));
    }

    private Company read(Connection conn, ResultSet rs) throws SQLException {
        Company c = new Company();
        c.setId(rs.getLong("id"));
        c.setIdType(IdType.valueOf(rs.getString("id_type")));
        c.setDocument(rs.getString("document"));
        c.setEstablishment(EstablishmentType.valueOf(rs.getString("establishment")));
        c.setLegalName(rs.getString("legal_name"));
        c.setTradeName(text(rs, "trade_name"));
        c.setOpeningDate(LocalDate.parse(rs.getString("opening_date")));
        c.setSize(CompanySize.valueOf(rs.getString("size")));
        c.setLegalNature(rs.getString("legal_nature_code"), rs.getString("legal_nature_description"));
        c.setSituation(CadastralSituation.valueOf(rs.getString("cad_situation")));
        c.setSituationDate(LocalDate.parse(rs.getString("cad_situation_date")));
        c.setSituationReason(text(rs, "cad_reason"));
        c.setSpecialSituation(text(rs, "special_situation"));
        c.setSpecialSituationDate(date(rs, "special_situation_date"));
        c.setStreet(rs.getString("street"));
        c.setNumber(rs.getString("number"));
        c.setComplement(text(rs, "complement"));
        c.setDistrict(rs.getString("district"));
        c.setZip(rs.getString("zip"));
        c.setCity(rs.getString("city"));
        c.setIbgeCode(rs.getString("ibge_code"));
        c.setUf(rs.getString("uf"));
        c.setEmail(text(rs, "email"));
        c.setPhone(text(rs, "phone"));
        c.setEfr(text(rs, "efr"));
        c.setWebsite(text(rs, "website"));
        c.setCompanyGroup(text(rs, "company_group"));
        c.setNire(text(rs, "nire"));
        c.setOtherIdentifiers(text(rs, "other_identifiers"));
        c.setMainCnae(new Cnae(rs.getString("main_cnae_code"), rs.getString("main_cnae_description")));
        c.setMunicipalRegistration(text(rs, "municipal_reg"));
        c.setMunicipalRegistrationDate(date(rs, "municipal_reg_date"));

        long id = c.id();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM company_cnae WHERE company_id = ? ORDER BY seq")) {
            ps.setLong(1, id);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    c.secondaryCnaes().add(new Cnae(r.getString("code"), r.getString("description")));
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM company_state_reg WHERE company_id = ? ORDER BY uf")) {
            ps.setLong(1, id);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    c.stateRegistrations().add(new StateRegistration(r.getString("uf"),
                            IeSituation.valueOf(r.getString("situation")), r.getString("number"), date(r, "reg_date")));
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM company_regime WHERE company_id = ? ORDER BY start_date")) {
            ps.setLong(1, id);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    c.regimePeriods().add(new RegimePeriod(TaxRegime.valueOf(r.getString("regime")),
                            LocalDate.parse(r.getString("start_date")), date(r, "end_date"), text(r, "note")));
                }
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ configurações

    public String setting(String key) {
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement("SELECT value FROM app_setting WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler configuração", e);
        }
    }

    public void putSetting(String key, String value) {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO app_setting (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao gravar configuração", e);
        }
    }
}
