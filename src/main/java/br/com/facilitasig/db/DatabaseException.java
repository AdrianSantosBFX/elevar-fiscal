package br.com.facilitasig.db;

/** Erro de acesso ao banco interno. */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
