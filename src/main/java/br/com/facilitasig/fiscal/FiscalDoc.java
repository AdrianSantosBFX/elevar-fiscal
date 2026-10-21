package br.com.facilitasig.fiscal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nota fiscal importada. Os valores extraídos do XML nunca são alterados; as correções do
 * usuário ficam numa camada separada (overrides). {@link #get} devolve o valor efetivo.
 */
public final class FiscalDoc {

    private Long id;
    private long companyId;
    private Direction direction;
    private DocKind kind = DocKind.DESCONHECIDO;
    private String accessKey = "";
    private DocStatus status = DocStatus.ALERTA;
    private long duplicateOf;
    private String xmlHash = "";
    private String sourcePath = "";
    private String xml = "";
    private String importedAt = "";
    private final Map<String, String> fields = new LinkedHashMap<String, String>();
    private final List<Map<String, String>> items = new ArrayList<Map<String, String>>();
    private final Map<String, String> overrides = new LinkedHashMap<String, String>();
    private final List<Finding> findings = new ArrayList<Finding>();

    public FiscalDoc() {
        for (String k : Fields.DOC) {
            fields.put(k, Fields.NA);
        }
    }

    public static Map<String, String> newItem() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String k : Fields.ITEM) {
            m.put(k, Fields.NA);
        }
        return m;
    }

    public Long id() { return id; }
    public void setId(Long id) { this.id = id; }
    public long companyId() { return companyId; }
    public void setCompanyId(long v) { this.companyId = v; }
    public Direction direction() { return direction; }
    public void setDirection(Direction v) { this.direction = v; }
    public DocKind kind() { return kind; }
    public void setKind(DocKind v) { this.kind = v; }
    public String accessKey() { return accessKey; }
    public void setAccessKey(String v) { this.accessKey = v == null ? "" : v; }
    public DocStatus status() { return status; }
    public void setStatus(DocStatus v) { this.status = v; }
    public long duplicateOf() { return duplicateOf; }
    public void setDuplicateOf(long v) { this.duplicateOf = v; }
    public String xmlHash() { return xmlHash; }
    public void setXmlHash(String v) { this.xmlHash = v; }
    public String sourcePath() { return sourcePath; }
    public void setSourcePath(String v) { this.sourcePath = v; }
    public String xml() { return xml; }
    public void setXml(String v) { this.xml = v; }
    public String importedAt() { return importedAt; }
    public void setImportedAt(String v) { this.importedAt = v; }
    public List<Map<String, String>> items() { return items; }
    public Map<String, String> overrides() { return overrides; }
    public List<Finding> findings() { return findings; }

    /** Grava o valor extraído do XML (vazio vira NÃO POSSUI). */
    public void put(String key, String value) {
        fields.put(key, value == null || value.trim().isEmpty() ? Fields.NA : value.trim());
    }

    public String original(String key) {
        String v = fields.get(key);
        return v == null ? Fields.NA : v;
    }

    /** Valor efetivo: correção do usuário, se houver; senão o do XML. */
    public String get(String key) {
        String o = overrides.get(key);
        return o != null ? o : original(key);
    }

    public boolean isEdited(String key) {
        return overrides.containsKey(key);
    }

    public int itemCount() {
        return items.size();
    }

    public static String itemKey(int index, String key) {
        return "item." + (index + 1) + "." + key;
    }

    public String itemOriginal(int index, String key) {
        String v = items.get(index).get(key);
        return v == null ? Fields.NA : v;
    }

    public String itemGet(int index, String key) {
        String o = overrides.get(itemKey(index, key));
        return o != null ? o : itemOriginal(index, key);
    }

    /** Participante da operação para a empresa: o emitente na entrada, o destinatário/tomador na saída. */
    public String partyPrefix() {
        return direction == Direction.ENTRADA ? "issuer_" : "recipient_";
    }

    public String partyGet(String suffix) {
        return get(partyPrefix() + suffix);
    }
}
