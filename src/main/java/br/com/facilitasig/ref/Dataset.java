package br.com.facilitasig.ref;

import java.time.LocalDate;

/**
 * Um conjunto de dados de referência (uma linha do {@code manifest.tsv}), com o registro da carga:
 * de onde veio, grau da fonte, cobertura, hash do arquivo e quantas linhas tem.
 */
public final class Dataset {

    private final String code;
    private final String file;
    private final String kind;
    private final String title;
    private final SourceGrade grade;
    private final String source;
    private final LocalDate sourceDate;
    private final String license;
    private final LocalDate coverageFrom;
    private final LocalDate conclusiveFrom;
    private final String notes;
    private final String sha256;
    private final int rowCount;
    private final String loadedAt;
    private final String origin;

    Dataset(String code, String file, String kind, String title, SourceGrade grade, String source,
            LocalDate sourceDate, String license, LocalDate coverageFrom, LocalDate conclusiveFrom,
            String notes, String sha256, int rowCount, String loadedAt) {
        this(code, file, kind, title, grade, source, sourceDate, license, coverageFrom, conclusiveFrom, notes,
                sha256, rowCount, loadedAt, ORIGIN_BUNDLED);
    }

    Dataset(String code, String file, String kind, String title, SourceGrade grade, String source,
            LocalDate sourceDate, String license, LocalDate coverageFrom, LocalDate conclusiveFrom,
            String notes, String sha256, int rowCount, String loadedAt, String origin) {
        this.code = code;
        this.file = file;
        this.kind = kind;
        this.title = title;
        this.grade = grade;
        this.source = source;
        this.sourceDate = sourceDate;
        this.license = license;
        this.coverageFrom = coverageFrom;
        this.conclusiveFrom = conclusiveFrom;
        this.notes = notes;
        this.sha256 = sha256;
        this.rowCount = rowCount;
        this.loadedAt = loadedAt;
        this.origin = origin;
    }

    /** Tabela embutida no sistema (JAR). */
    public static final String ORIGIN_BUNDLED = "EMBUTIDO";
    /** Arquivo oficial importado pelo usuário (ex.: alíquotas de ISS mais novas do Portal NFS-e). */
    public static final String ORIGIN_IMPORTED = "IMPORTADO";

    Dataset withLoad(String newSha256, int newRowCount, String newLoadedAt) {
        return new Dataset(code, file, kind, title, grade, source, sourceDate, license, coverageFrom,
                conclusiveFrom, notes, newSha256, newRowCount, newLoadedAt, origin);
    }

    Dataset withNotes(String newNotes) {
        return new Dataset(code, file, kind, title, grade, source, sourceDate, license, coverageFrom,
                conclusiveFrom, newNotes, sha256, rowCount, loadedAt, origin);
    }

    Dataset imported(String newFile, String newTitle, String newSource, LocalDate newSourceDate) {
        return new Dataset(code, newFile, kind, newTitle, grade, newSource, newSourceDate, license, coverageFrom,
                conclusiveFrom, notes, sha256, rowCount, loadedAt, ORIGIN_IMPORTED);
    }

    public String code() {
        return code;
    }

    public String file() {
        return file;
    }

    /** Tipo de tabela: NCM ou CST. */
    public String kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public SourceGrade grade() {
        return grade;
    }

    public String source() {
        return source;
    }

    public LocalDate sourceDate() {
        return sourceDate;
    }

    public String license() {
        return license;
    }

    /** Antes desta data a tabela não responde (SEM_COBERTURA). Nulo = sem limite. */
    public LocalDate coverageFrom() {
        return coverageFrom;
    }

    /** Código ausente só é "não encontrado" a partir desta data; antes, SEM_COBERTURA. Nulo = sempre conclusivo. */
    public LocalDate conclusiveFrom() {
        return conclusiveFrom;
    }

    public String notes() {
        return notes;
    }

    public String sha256() {
        return sha256;
    }

    public int rowCount() {
        return rowCount;
    }

    public String loadedAt() {
        return loadedAt;
    }

    /** EMBUTIDO (veio com o sistema) ou IMPORTADO (arquivo oficial trazido pelo usuário). */
    public String origin() {
        return origin;
    }

    boolean covers(LocalDate day) {
        return coverageFrom == null || !day.isBefore(coverageFrom);
    }

    boolean absenceConclusive(LocalDate day) {
        return conclusiveFrom == null || !day.isBefore(conclusiveFrom);
    }
}
