package br.com.facilitasig.db;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;


/**
 * Pasta de dados do sistema, FORA da pasta do programa, para que atualizar a versão
 * não apague o banco. Aceita -Delevarfiscal.home / ELEVAR_FISCAL_HOME
 * e os nomes antigos -Dfacilitasig.home / FACILITASIG_HOME.
 * Sem configuração, continua em %APPDATA%\FacilitaSig (Windows) ou
 * ~/.facilita-sig, para preservar os bancos existentes após a mudança de marca.
 */
public final class DataDirectory {

    public static final String DB_FILE_NAME = "elevar-fiscal.db";
    private static final String LEGACY_DB_FILE_NAME = "facilita-sig.db";
    /** Banco de tabelas de referência (NCM, CST...): separado para que trocar tabelas nunca afete os dados das empresas. */
    public static final String REF_DB_FILE_NAME = "elevar-fiscal-ref.db";
    private static final String LEGACY_REF_DB_FILE_NAME = "facilita-sig-ref.db";

    private DataDirectory() {
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public static File resolve() {
        String override = firstNotBlank(
                System.getProperty("elevarfiscal.home"),
                System.getenv("ELEVAR_FISCAL_HOME"),
                System.getProperty("facilitasig.home"),
                System.getenv("FACILITASIG_HOME"));
        File dir;
        if (override != null && !override.trim().isEmpty()) {
            dir = new File(override.trim());
        } else {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                dir = new File(appData, "FacilitaSig");
            } else {
                dir = new File(System.getProperty("user.home"), ".facilita-sig");
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta de dados: " + dir);
        }
        return dir;
    }

    /** Banco principal: mantém os dados do nome antigo ao migrar para Elevar Fiscal. */
    public static File databaseFile() {
        return resolveDatabase(DB_FILE_NAME, LEGACY_DB_FILE_NAME);
    }

    /** Banco de referências: preserva tabelas e importações feitas na versão anterior. */
    public static File referenceDatabaseFile() {
        return resolveDatabase(REF_DB_FILE_NAME, LEGACY_REF_DB_FILE_NAME);
    }

    /**
     * Se existir apenas o nome antigo, cria uma CÓPIA segura no novo nome.
     * O banco original é mantido como respaldo. Nunca sobrescreve um banco novo.
     * A aplicação deve estar fechada em todas as outras instâncias ao migrar.
     */
    private static File resolveDatabase(String newName, String oldName) {
        File dir = resolve();
        File target = new File(dir, newName);
        if (target.exists()) return target;

        File legacy = new File(dir, oldName);
        if (!legacy.isFile()) return target;
        if (new File(dir, oldName + "-wal").exists()
                || new File(dir, oldName + "-journal").exists()) {
            throw new IllegalStateException("O banco antigo pode estar em uso: " + legacy
                    + ". Feche todas as instâncias do Facilita.sig/Elevar Fiscal "
                    + "e faça um backup antes de tentar novamente.");
        }

        Path temp = null;
        try {
            temp = Files.createTempFile(dir.toPath(), ".elevar-migracao-", ".tmp");
            Files.copy(legacy.toPath(), temp, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temp) != Files.size(legacy.toPath())) {
                throw new IOException("A cópia do banco ficou incompleta");
            }
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target.toPath());
            }
            return target;
        } catch (IOException e) {
            // Uma outra instância pode ter terminado a migração nesse intervalo.
            if (target.isFile()) return target;
            throw new IllegalStateException("Não foi possível copiar " + oldName + " para " + newName
                    + ". O banco antigo não foi alterado: " + legacy, e);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }
}
