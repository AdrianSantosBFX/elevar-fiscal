package br.com.facilitasig.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Banco SQLite interno (arquivo único). O esquema é versionado por PRAGMA user_version;
 * cada migração roda em transação. Mantido em arquivo único, sem journal persistente,
 * para facilitar a futura exportação/importação do banco entre versões do sistema.
 */
public final class Database {

    private final File file;
    private final String[][] migrations;

    /** Banco principal do sistema (empresas, notas, achados), com o esquema de {@link Schema}. */
    public Database(File file) {
        this(file, Schema.migrations());
    }

    /**
     * Banco com esquema próprio (ex.: o banco de referência, {@code elevar-fiscal-ref.db}). Cada
     * banco controla a sua versão de esquema por PRAGMA user_version, independentemente dos outros.
     */
    public Database(File file, String[][] migrations) {
        this.file = file;
        this.migrations = migrations;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver SQLite (sqlite-jdbc) não encontrado. "
                    + "Mantenha a pasta lib ao lado do elevar-fiscal.jar.", e);
        }
        migrate();
    }

    public static Database openDefault() {
        return new Database(DataDirectory.databaseFile());
    }

    public File file() {
        return file;
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = DELETE");
        }
        return c;
    }

    public int schemaVersion() {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao ler a versão do esquema", e);
        }
    }

    private void migrate() {
        try (Connection c = connect()) {
            int current;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                current = rs.next() ? rs.getInt(1) : 0;
            }
            if (current > migrations.length) {
                throw new DatabaseException("O banco foi criado por uma versão mais nova do sistema (esquema "
                        + current + "). Atualize o programa.", null);
            }
            for (int v = current; v < migrations.length; v++) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (String sql : migrations[v]) {
                        st.execute(sql);
                    }
                    st.execute("PRAGMA user_version = " + (v + 1));
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Falha ao preparar o banco de dados", e);
        }
    }
}
