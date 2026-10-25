package br.com.facilitasig.fiscal;

/**
 * Evento de NF-e registrado na SEFAZ e importado junto com as notas: cancelamento (110111) ou
 * carta de correção (110110). Fica ligado à nota pela chave de acesso, mesmo que a nota seja
 * importada depois do evento.
 */
public final class FiscalEvent {

    public static final String CANCELAMENTO = "110111";
    public static final String CARTA_CORRECAO = "110110";

    private Long id;
    private long companyId;
    private String accessKey = "";
    private String type = "";
    private int seq = 1;
    private String eventAt = Fields.NA;
    private String protocolStatus = Fields.NA;
    private String description = Fields.NA;
    private String xmlHash = "";
    private String sourcePath = "";
    private String xml = "";

    public Long id() { return id; }
    public void setId(Long v) { this.id = v; }
    public long companyId() { return companyId; }
    public void setCompanyId(long v) { this.companyId = v; }
    public String accessKey() { return accessKey; }
    public void setAccessKey(String v) { this.accessKey = v == null ? "" : v.trim(); }
    /** Código do evento (110111 = cancelamento, 110110 = carta de correção). */
    public String type() { return type; }
    public void setType(String v) { this.type = v == null ? "" : v.trim(); }
    public int seq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String eventAt() { return eventAt; }
    public void setEventAt(String v) { this.eventAt = na(v); }
    /** cStat do registro do evento (135/136 = registrado; 155 = cancelamento fora do prazo homologado). */
    public String protocolStatus() { return protocolStatus; }
    public void setProtocolStatus(String v) { this.protocolStatus = na(v); }
    /** Justificativa (cancelamento) ou texto da correção (CC-e). */
    public String description() { return description; }
    public void setDescription(String v) { this.description = na(v); }
    public String xmlHash() { return xmlHash; }
    public void setXmlHash(String v) { this.xmlHash = v == null ? "" : v; }
    public String sourcePath() { return sourcePath; }
    public void setSourcePath(String v) { this.sourcePath = v == null ? "" : v; }
    public String xml() { return xml; }
    public void setXml(String v) { this.xml = v == null ? "" : v; }

    public boolean isCancellation() { return CANCELAMENTO.equals(type); }
    public boolean isCorrection() { return CARTA_CORRECAO.equals(type); }

    public String typeLabel() {
        return isCancellation() ? "Cancelamento" : isCorrection() ? "Carta de correção" : "Evento " + type;
    }

    private static String na(String v) {
        return v == null || v.trim().isEmpty() ? Fields.NA : v.trim();
    }
}
