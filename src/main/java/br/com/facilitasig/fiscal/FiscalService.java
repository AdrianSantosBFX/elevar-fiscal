package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Importação em lote de XMLs (NF-e e NFS-e nacional) e revalidação. Sem dependência de tela. */
public final class FiscalService {

    /** Resumo da importação. */
    public static final class ImportResult {
        public int read;
        public int conforme;
        public int alerta;
        public int erro;
        public int duplicada;
        public int repetida;
        public int ignorada;
        /** Eventos de NF-e gravados (cancelamento, carta de correção). */
        public int evento;
        /** Notas e eventos de outra empresa: descartados, sem gravar nada. */
        public int outraEmpresa;
        public final List<String> messages = new ArrayList<String>();
    }

    public interface Progress {
        /** @return false para cancelar */
        boolean step(int done, int total, String file);
    }

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final FiscalDocRepository repository;

    public FiscalService(FiscalDocRepository repository) {
        this.repository = repository;
    }

    public FiscalDocRepository repository() {
        return repository;
    }

    /** Lista os arquivos .xml da pasta (opcionalmente com subpastas), em ordem estável. */
    public static List<File> collect(File dir, boolean recursive) {
        List<File> out = new ArrayList<File>();
        walk(dir, recursive, out);
        Collections.sort(out);
        return out;
    }

    private static void walk(File dir, boolean recursive, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                if (recursive) {
                    walk(f, true, out);
                }
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                out.add(f);
            }
        }
    }

    public ImportResult importFiles(List<File> files, Direction direction, Company company, Progress progress) {
        ImportResult r = new ImportResult();
        int i = 0;
        for (File f : files) {
            if (progress != null && !progress.step(i++, files.size(), f.getName())) {
                r.messages.add("Importação cancelada pelo usuário.");
                break;
            }
            try {
                importOne(f, direction, company, r);
            } catch (RuntimeException e) {
                r.erro++;
                r.messages.add(f.getName() + ": falha inesperada — " + e.getMessage());
            }
        }
        return r;
    }

    private void importOne(File f, Direction direction, Company company, ImportResult r) {
        r.read++;
        byte[] data;
        try {
            if (f.length() > MAX_BYTES) {
                saveFailure(f, new byte[0], "", direction, company, r, "IMP-001", "Arquivo maior que 20 MB.");
                return;
            }
            data = Files.readAllBytes(f.toPath());
        } catch (java.io.IOException e) {
            r.erro++;
            r.messages.add(f.getName() + ": não foi possível ler o arquivo.");
            return;
        }
        String hash = sha256(data);
        Element root;
        try {
            root = parseXml(data);
        } catch (Exception e) {
            saveFailure(f, data, hash, direction, company, r, "IMP-001", "XML ilegível: " + e.getMessage());
            return;
        }
        String name = X.name(root);
        if (name.equals("procEventoNFe")) {
            importEvent(f, root, data, hash, direction, company, r);
            return;
        }
        if (name.startsWith("procEvento") || name.startsWith("retEnvEvento") || name.equals("evento")
                || name.equals("resNFe") || name.equals("resEvento")) {
            r.ignorada++;
            r.messages.add(f.getName() + ": evento/resumo ignorado (" + name + ").");
            return;
        }
        if (isMunicipalNfse(root)) {
            saveFailure(f, data, hash, direction, company, r, "ERR-1.12",
                    "NFS-e em leiaute municipal (<" + name + ">), não suportado nesta versão: a nota fica fora da apuração.");
            return;
        }
        FiscalDoc d;
        try {
            if (name.equals("nfeProc") || name.equals("NFe")) {
                d = NfeParser.parse(root);
            } else if (name.equals("NFSe")) {
                d = NfseNacionalParser.parse(root);
            } else {
                saveFailure(f, data, hash, direction, company, r, "IMP-001", "Tipo de XML não suportado: <" + name + ">.");
                return;
            }
        } catch (RuntimeException e) {
            saveFailure(f, data, hash, direction, company, r, "IMP-001", "Estrutura inválida: " + e.getMessage());
            return;
        }
        if (!DocValidator.belongsTo(d, direction, company)) {
            r.outraEmpresa++;
            boolean nfe = d.kind() == DocKind.NFE;
            String who = direction == Direction.SAIDA
                    ? (nfe ? "emitente " : "prestador ") + d.original("issuer_doc")
                    : (nfe ? "destinatário " : "tomador ") + d.original("recipient_doc");
            r.messages.add(f.getName() + ": " + (nfe ? "NF-e " : "NFS-e ") + d.original("number") + " de outra empresa ("
                    + who + ") — descartada, nada foi gravado.");
            return;
        }
        d.setCompanyId(company.id());
        d.setDirection(direction);
        d.setXml(new String(data, StandardCharsets.UTF_8));
        d.setXmlHash(hash);
        d.setSourcePath(f.getAbsolutePath());
        if (Fields.NA.equals(d.original("entry_exit_date")) && !Fields.NA.equals(d.original("issued_at"))) {
            d.put("entry_exit_date", d.original("issued_at").substring(0, Math.min(10, d.original("issued_at").length())));
        }
        long original = repository.findOriginal(company.id(), direction, d.accessKey());
        if (original != 0) {
            if (repository.duplicateRecorded(company.id(), direction, d.accessKey(), hash) && original != 0
                    && sameFileAlreadyDuplicated(company, direction, d)) {
                r.repetida++;
                return;
            }
            d.setStatus(DocStatus.DUPLICADA);
            d.setDuplicateOf(original);
            d.findings().add(new Finding("IMP-020", Severity.ALERTA, "access_key",
                    "Nota já importada (registro " + original + ").", "Excluir esta duplicata."));
            repository.insert(d);
            r.duplicada++;
            return;
        }
        d.findings().addAll(DocValidator.validate(d, company, LocalDate.now(), repository.events(company.id(), d.accessKey())));
        d.setStatus(DocValidator.statusOf(d.findings()));
        repository.insert(d);
        count(r, d.status());
    }

    // ------------------------------------------------------------------ eventos (v0.1.1)

    /**
     * Grava o evento de NF-e (cancelamento 110111 ou carta de correção 110110) registrado na
     * SEFAZ e revalida as notas já importadas com a mesma chave. Os demais eventos continuam
     * ignorados. O evento vale para os dois sentidos (entrada e saída) da empresa ativa.
     */
    private void importEvent(File f, Element root, byte[] data, String hash, Direction direction, Company company, ImportResult r) {
        Element inf = X.path(root, "evento", "infEvento");
        String type = X.text(inf, "tpEvento");
        if (inf == null || !(FiscalEvent.CANCELAMENTO.equals(type) || FiscalEvent.CARTA_CORRECAO.equals(type))) {
            r.ignorada++;
            r.messages.add(f.getName() + ": evento ignorado (tipo " + type + ").");
            return;
        }
        String cStat = X.text(root, "retEvento", "infEvento", "cStat");
        if (!("135".equals(cStat) || "136".equals(cStat) || "155".equals(cStat))) {
            r.ignorada++;
            r.messages.add(f.getName() + ": evento sem registro na SEFAZ (cStat " + cStat + "), ignorado.");
            return;
        }
        FiscalEvent ev = new FiscalEvent();
        ev.setCompanyId(company.id());
        ev.setAccessKey(X.text(inf, "chNFe"));
        ev.setType(type);
        String seq = X.text(inf, "nSeqEvento");
        ev.setSeq(seq.matches("\\d{1,3}") ? Integer.parseInt(seq) : 1);
        ev.setEventAt(X.text(inf, "dhEvento"));
        ev.setProtocolStatus(cStat);
        ev.setDescription(ev.isCancellation() ? X.text(inf, "detEvento", "xJust") : X.text(inf, "detEvento", "xCorrecao"));
        ev.setXmlHash(hash);
        ev.setSourcePath(f.getAbsolutePath());
        ev.setXml(new String(data, StandardCharsets.UTF_8));
        if (!ev.accessKey().matches("\\d{44}")) {
            r.ignorada++;
            r.messages.add(f.getName() + ": evento sem chave de acesso válida, ignorado.");
            return;
        }
        // Na saída, a chave traz o CNPJ do emitente: evento de nota emitida por outro CNPJ é alheio.
        // Na entrada o evento não informa o destinatário; ele só é aplicado a notas desta empresa.
        String keyIssuer = ev.accessKey().substring(6, 20);
        if (direction == Direction.SAIDA && !keyIssuer.equals(BrDocs.alphanumeric(company.document()))) {
            r.outraEmpresa++;
            r.messages.add(f.getName() + ": " + ev.typeLabel().toLowerCase(Locale.ROOT) + " de nota emitida por outro CNPJ ("
                    + keyIssuer + ") — descartado.");
            return;
        }
        if (!repository.insertEvent(ev)) {
            r.repetida++;
            return;
        }
        r.evento++;
        List<Long> ids = repository.docIdsByKey(company.id(), ev.accessKey());
        for (Long id : ids) {
            review(id, null, company);
        }
        r.messages.add(f.getName() + ": " + ev.typeLabel().toLowerCase(Locale.ROOT) + " da chave " + ev.accessKey()
                + (ids.isEmpty() ? " gravado (a nota ainda não foi importada)." : " aplicado a " + ids.size() + " nota(s)."));
    }

    /** XML de NFS-e no padrão municipal (ABRASF e variações), que esta versão não lê. */
    static boolean isMunicipalNfse(Element root) {
        String n = X.name(root);
        if (n.equals("CompNfse") || n.equals("ConsultarNfseResposta") || n.equals("ConsultarNfseRpsResposta")
                || n.equals("ConsultarNfseServicoPrestadoResposta") || n.equals("ConsultarNfseServicoTomadoResposta")
                || n.equals("ConsultarNfseFaixaResposta") || n.equals("ConsultarLoteRpsResposta") || n.equals("GerarNfseResposta")
                || n.equals("EnviarLoteRpsSincronoResposta") || n.equals("ListaNfse")) {
            return true;
        }
        return n.equalsIgnoreCase("Nfse") && !n.equals("NFSe")
                || root.getElementsByTagNameNS("*", "InfNfse").getLength() > 0;
    }

    /** Evita acumular várias duplicatas idênticas do mesmo arquivo em reimportações. */
    private boolean sameFileAlreadyDuplicated(Company company, Direction dir, FiscalDoc d) {
        for (FiscalDoc x : repository.list(company.id(), dir)) {
            if (x.status() == DocStatus.DUPLICADA && x.accessKey().equals(d.accessKey()) && x.xmlHash().equals(d.xmlHash())) {
                return true;
            }
        }
        return false;
    }

    private void saveFailure(File f, byte[] data, String hash, Direction direction, Company company, ImportResult r,
                             String ruleId, String msg) {
        FiscalDoc d = new FiscalDoc();
        d.setKind(DocKind.DESCONHECIDO);
        d.setCompanyId(company.id());
        d.setDirection(direction);
        d.setAccessKey("ARQ-" + (hash.isEmpty() ? Integer.toHexString(f.getAbsolutePath().hashCode()) : hash));
        d.setXmlHash(hash);
        d.setSourcePath(f.getAbsolutePath());
        d.setXml(new String(data, StandardCharsets.UTF_8));
        d.put("number", f.getName());
        d.findings().add(new Finding(ruleId, Severity.ERRO, "arquivo", msg, "ERR-1.12".equals(ruleId)
                ? "Obter a nota no padrão nacional (Emissor Nacional/ADN) ou registrar manualmente quando houver suporte."
                : "Obter novamente o XML ou excluir o registro."));
        d.setStatus(DocStatus.ERRO);
        if (repository.findOriginal(company.id(), direction, d.accessKey()) != 0) {
            r.repetida++;
            return;
        }
        repository.insert(d);
        r.erro++;
        r.messages.add(f.getName() + ": " + msg);
    }

    private static void count(ImportResult r, DocStatus s) {
        switch (s) {
            case CONFORME: r.conforme++; break;
            case ALERTA: r.alerta++; break;
            default: r.erro++;
        }
    }

    static Element parseXml(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }
        });
        Document doc = b.parse(new ByteArrayInputStream(data));
        return doc.getDocumentElement();
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte x : h) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ revisão

    /** Aplica o novo conjunto de correções, revalida e grava. Devolve a nota atualizada. */
    public FiscalDoc review(long docId, java.util.Map<String, String> newOverrides, Company company) {
        FiscalDoc d = repository.load(company.id(), docId);
        if (d == null) {
            throw new IllegalArgumentException("A nota " + docId + " não pertence à empresa selecionada.");
        }
        if (d.status() == DocStatus.DUPLICADA) {
            throw new IllegalStateException("Duplicatas não são revalidadas.");
        }
        if (newOverrides != null) {
            d.overrides().clear();
            d.overrides().putAll(newOverrides);
        }
        revalidate(d, company);
        repository.saveReview(d);
        return d;
    }

    private void revalidate(FiscalDoc d, Company company) {
        d.findings().clear();
        if (d.kind() == DocKind.DESCONHECIDO) {
            Finding f = new Finding("IMP-001", Severity.ERRO, "arquivo",
                    "Arquivo não reconhecido como NF-e/NFS-e.", "Excluir o registro e importar o XML correto.");
            Element root = null;
            try {
                root = parseXml(d.xml().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // continua como IMP-001
            }
            if (root != null && isMunicipalNfse(root)) {
                f = new Finding("ERR-1.12", Severity.ERRO, "arquivo", "NFS-e em leiaute municipal (<" + X.name(root)
                        + ">), não suportado nesta versão: a nota fica fora da apuração.",
                        "Obter a nota no padrão nacional (Emissor Nacional/ADN) ou registrar manualmente quando houver suporte.");
            }
            d.findings().add(f);
        } else {
            d.findings().addAll(DocValidator.validate(d, company, LocalDate.now(), repository.events(company.id(), d.accessKey())));
        }
        d.setStatus(DocValidator.statusOf(d.findings()));
    }

    /** Revalida todas as notas (exceto duplicadas) do sentido. Devolve quantas mudaram de situação. */
    public int revalidateAll(Direction dir, Company company) {
        int changed = 0;
        for (FiscalDoc summary : repository.list(company.id(), dir)) {
            if (summary.status() == DocStatus.DUPLICADA) {
                continue;
            }
            DocStatus before = summary.status();
            FiscalDoc d = review(summary.id(), null, company);
            if (d.status() != before) {
                changed++;
            }
        }
        return changed;
    }
}
