package br.com.facilitasig.fiscal;

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
                saveFailure(f, new byte[0], "", direction, company, r, "Arquivo maior que 20 MB.");
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
            saveFailure(f, data, hash, direction, company, r, "XML ilegível: " + e.getMessage());
            return;
        }
        String name = X.name(root);
        if (name.startsWith("procEvento") || name.startsWith("retEnvEvento") || name.equals("evento")
                || name.equals("resNFe") || name.equals("resEvento") || name.equals("procEventoNFe")) {
            r.ignorada++;
            r.messages.add(f.getName() + ": evento/resumo ignorado (" + name + ").");
            return;
        }
        FiscalDoc d;
        try {
            if (name.equals("nfeProc") || name.equals("NFe")) {
                d = NfeParser.parse(root);
            } else if (name.equals("NFSe")) {
                d = NfseNacionalParser.parse(root);
            } else {
                saveFailure(f, data, hash, direction, company, r, "Tipo de XML não suportado: <" + name + ">.");
                return;
            }
        } catch (RuntimeException e) {
            saveFailure(f, data, hash, direction, company, r, "Estrutura inválida: " + e.getMessage());
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
        d.findings().addAll(DocValidator.validate(d, company, LocalDate.now()));
        d.setStatus(DocValidator.statusOf(d.findings()));
        repository.insert(d);
        count(r, d.status());
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

    private void saveFailure(File f, byte[] data, String hash, Direction direction, Company company, ImportResult r, String msg) {
        FiscalDoc d = new FiscalDoc();
        d.setKind(DocKind.DESCONHECIDO);
        d.setCompanyId(company.id());
        d.setDirection(direction);
        d.setAccessKey("ARQ-" + (hash.isEmpty() ? Integer.toHexString(f.getAbsolutePath().hashCode()) : hash));
        d.setXmlHash(hash);
        d.setSourcePath(f.getAbsolutePath());
        d.setXml(new String(data, StandardCharsets.UTF_8));
        d.put("number", f.getName());
        d.findings().add(new Finding("IMP-001", Severity.ERRO, "arquivo", msg, "Obter novamente o XML ou excluir o registro."));
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
        FiscalDoc d = repository.load(docId);
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
            d.findings().add(new Finding("IMP-001", Severity.ERRO, "arquivo",
                    "Arquivo não reconhecido como NF-e/NFS-e.", "Excluir o registro e importar o XML correto."));
        } else {
            d.findings().addAll(DocValidator.validate(d, company, LocalDate.now()));
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
