package br.com.facilitasig.db;

import java.io.File;

/**
 * Pasta de dados do sistema, FORA da pasta do programa, para que atualizar a versão
 * não apague o banco. Ordem: propriedade -Dfacilitasig.home, variável FACILITASIG_HOME,
 * %APPDATA%\FacilitaSig (Windows) ou ~/.facilita-sig.
 */
public final class DataDirectory {

    public static final String DB_FILE_NAME = "facilita-sig.db";

    private DataDirectory() {
    }

    public static File resolve() {
        String override = System.getProperty("facilitasig.home");
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv("FACILITASIG_HOME");
        }
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

    public static File databaseFile() {
        return new File(resolve(), DB_FILE_NAME);
    }
}
