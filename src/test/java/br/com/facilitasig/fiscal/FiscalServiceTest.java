package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.CompanyRepository;
import br.com.facilitasig.company.SampleCompanies;
import br.com.facilitasig.db.Database;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FiscalServiceTest {

    private static final String L5 = "41843649000151";
    private static final String OTHER = other();

    private static String other() {
        String base = "112223330001";
        return base + BrDocs.cnpjCheckDigits(base);
    }

    private File dir;
    private FiscalService service;
    private Company company;

    private void setup() throws Exception {
        dir = Files.createTempDirectory("fsig-docs").toFile();
        Database db = new Database(new File(dir, "t.db"));
        company = new CompanyRepository(db).save(SampleCompanies.l5());
        service = new FiscalService(new FiscalDocRepository(db));
    }

    private File write(String name, String xml) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    static String key(String cnpj, int number) {
        String k = "3126" + "08" + cnpj + "55" + "001" + String.format("%09d", number) + "1" + "12345678";
        return k + DocValidator.keyCheckDigit(k);
    }

    static String nfe(String issuer, String recipient, int number, String vProd, String vNF, String ncm, String cfop,
                      String issuerUf, String recipUf) {
        String k = key(issuer, number);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<NFe><infNFe Id=\"NFe" + k + "\" versao=\"4.00\"><ide><cUF>31</cUF><natOp>Venda de mercadoria</natOp><mod>55</mod>"
                + "<serie>1</serie><nNF>" + number + "</nNF><dhEmi>2026-08-06T10:00:00-03:00</dhEmi><tpNF>1</tpNF><idDest>1</idDest>"
                + "<tpAmb>1</tpAmb></ide>"
                + "<emit><CNPJ>" + issuer + "</CNPJ><xNome>EMITENTE LTDA</xNome><enderEmit><cMun>3170701</cMun><UF>" + issuerUf + "</UF></enderEmit><IE>0044543260066</IE></emit>"
                + "<dest><CNPJ>" + recipient + "</CNPJ><xNome>DESTINATARIO SA</xNome><enderDest><cMun>3170701</cMun><UF>" + recipUf + "</UF></enderDest><IE>123</IE></dest>"
                + "<det nItem=\"1\"><prod><cProd>1</cProd><xProd>Produto</xProd><NCM>" + ncm + "</NCM><CFOP>" + cfop + "</CFOP><uCom>UN</uCom>"
                + "<qCom>1</qCom><vUnCom>" + vProd + "</vUnCom><vProd>" + vProd + "</vProd></prod>"
                + "<imposto><ICMS><ICMS00><orig>0</orig><CST>00</CST><modBC>3</modBC><vBC>" + vProd + "</vBC><pICMS>18.00</pICMS><vICMS>" + icms(vProd)
                + "</vICMS></ICMS00></ICMS><PIS><PISNT><CST>07</CST></PISNT></PIS></imposto></det>"
                + "<total><ICMSTot><vBC>" + vProd + "</vBC><vICMS>" + icms(vProd) + "</vICMS><vProd>" + vProd + "</vProd><vNF>" + vNF
                + "</vNF></ICMSTot></total></infNFe></NFe>"
                + "<protNFe><infProt><cStat>100</cStat><nProt>131000000000001</nProt></infProt></protNFe></nfeProc>";
        return TestSigner.sign(xml);
    }

    static String icms(String vProd) {
        return new java.math.BigDecimal(vProd).multiply(new java.math.BigDecimal("0.18"))
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    static String nfse(String provider, String taker, String number) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.00\">"
                + "<infNFSe Id=\"NFS35503082" + provider + number + "\"><xTribNac>Serviços de contabilidade</xTribNac><nNFSe>" + number + "</nNFSe><cStat>100</cStat>"
                + "<emit><CNPJ>" + provider + "</CNPJ><xNome>PRESTADOR LTDA</xNome><enderNac><cMun>3550308</cMun><UF>SP</UF></enderNac></emit>"
                + "<valores><vISSQN>20.00</vISSQN></valores>"
                + "<DPS versao=\"1.00\"><infDPS><tpAmb>1</tpAmb><dhEmi>2026-09-03T09:00:00-03:00</dhEmi><serie>1</serie><nDPS>" + number + "</nDPS><dCompet>2026-09-03</dCompet>"
                + "<toma><CNPJ>" + taker + "</CNPJ><xNome>TOMADOR</xNome><end><endNac><cMun>3170701</cMun></endNac></end></toma>"
                + "<serv><cServ><cTribNac>171901</cTribNac><xDescServ>Serviço contábil</xDescServ></cServ></serv>"
                + "<valores><vServPrest><vServ>1000.00</vServ></vServPrest><trib><tribMun><tpRetISSQN>2</tpRetISSQN></tribMun></trib></valores>"
                + "</infDPS></DPS></infNFSe></NFSe>";
        return TestSigner.sign(xml);
    }

    private FiscalDoc only(Direction d, String key) {
        for (FiscalDoc x : service.repository().list(company.id(), d)) {
            if (x.accessKey().equals(key)) {
                return x;
            }
        }
        return null;
    }

    @Test
    void conformingOutgoingNfeIsConforme() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 10, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        assertEquals(1, r.conforme);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 10));
        assertEquals(DocStatus.CONFORME, d.status());
        assertEquals("2026-08-06", d.get("entry_exit_date"));
    }

    @Test
    void duplicateIsRecordedOnceAndOriginalKept() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 11, "10.00", "10.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalService.ImportResult r2 = service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        assertEquals(1, r2.duplicada);
        FiscalService.ImportResult r3 = service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        assertEquals(1, r3.repetida);
        assertEquals(0, r3.duplicada);
        assertEquals(1, (int) service.repository().counts(company.id(), Direction.SAIDA).get(DocStatus.CONFORME));
        assertEquals(1, (int) service.repository().counts(company.id(), Direction.SAIDA).get(DocStatus.DUPLICADA));
    }

    @Test
    void wrongTotalIsAlert() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 12, "100.00", "90.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 12));
        assertEquals(DocStatus.ALERTA, d.status());
        assertEquals("IMP-008", d.findings().get(0).ruleId());
    }

    @Test
    void noteOfAnotherCompanyIsDiscardedWithoutSaving() throws Exception {
        setup();
        File f = write("a.xml", nfe(OTHER, OTHER, 13, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        assertEquals(1, r.outraEmpresa);
        assertEquals(0, r.erro);
        assertTrue(service.repository().list(company.id(), Direction.SAIDA).isEmpty());
        assertTrue(r.messages.get(0).contains("descartada"));
        // na entrada: nota de terceiro destinada a outro CNPJ
        FiscalService.ImportResult r2 = service.importFiles(java.util.Arrays.asList(f), Direction.ENTRADA, company, null);
        assertEquals(1, r2.outraEmpresa);
        assertTrue(service.repository().list(company.id(), Direction.ENTRADA).isEmpty());
    }

    @Test
    void branchIsAnotherCompany() throws Exception {
        setup();
        String branch = "418436490002" + BrDocs.cnpjCheckDigits("418436490002"); // filial da L5
        File f = write("a.xml", nfe(OTHER, branch, 30, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(f), Direction.ENTRADA, company, null);
        assertEquals(1, r.outraEmpresa, "matriz e filial são CNPJs separados");
    }

    @Test
    void notesOfOneCompanyAreInvisibleToAnother() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 31, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        long id = only(Direction.SAIDA, key(L5, 31)).id();
        Company other = SampleCompanies.l5();
        other.setDocument(OTHER);
        other = new CompanyRepository(new Database(new File(dir, "t.db"))).save(other);
        assertTrue(service.repository().list(other.id(), Direction.SAIDA).isEmpty());
        assertNull(service.repository().load(other.id(), id));
        assertNotNull(service.repository().load(company.id(), id));
        final Company o = other;
        assertThrows(IllegalArgumentException.class, () -> service.review(id, null, o));
        assertFalse(service.repository().delete(other.id(), id));
        assertNotNull(service.repository().load(company.id(), id));
        assertTrue(service.repository().delete(company.id(), id));
    }

    @Test
    void outgoingEventOfAnotherIssuerIsDiscarded() throws Exception {
        setup();
        File ev = write("ev.xml", event(key(OTHER, 32), FiscalEvent.CANCELAMENTO, "135", "x"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(ev), Direction.SAIDA, company, null);
        assertEquals(1, r.outraEmpresa);
        assertEquals(0, r.evento);
    }

    @Test
    void garbageAndEventsAreHandled() throws Exception {
        setup();
        File bad = write("bad.xml", "isto nao e xml");
        File ev = write("ev.xml", "<procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><evento/></procEventoNFe>");
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(bad, ev), Direction.ENTRADA, company, null);
        assertEquals(1, r.erro);
        assertEquals(1, r.ignorada);
    }

    @Test
    void incomingNfseNacional() throws Exception {
        setup();
        File f = write("s.xml", nfse(OTHER, L5, "77"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(f), Direction.ENTRADA, company, null);
        FiscalDoc d = service.repository().list(company.id(), Direction.ENTRADA).get(0);
        assertEquals(DocKind.NFSE, d.kind());
        assertEquals("SP", d.get("issuer_uf"));
        assertEquals("SIM", d.get("iss_retained"));
        assertEquals(DocStatus.CONFORME, d.status());
        assertEquals(1, r.conforme);
    }

    @Test
    void editingAndRevalidatingFixesAnItem() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 14, "100.00", "100.00", "ABC", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 14));
        assertEquals(DocStatus.ALERTA, d.status());
        Map<String, String> ov = new LinkedHashMap<String, String>();
        ov.put(FiscalDoc.itemKey(0, "ncm"), "12345678");
        FiscalDoc fixed = service.review(d.id(), ov, company);
        assertEquals(DocStatus.CONFORME, fixed.status());
        FiscalDoc reloaded = service.repository().load(d.id());
        assertEquals("ABC", reloaded.itemOriginal(0, "ncm"));
        assertEquals("12345678", reloaded.itemGet(0, "ncm"));
        FiscalDoc restored = service.review(d.id(), new LinkedHashMap<String, String>(), company);
        assertEquals(DocStatus.ALERTA, restored.status());
    }

    @Test
    void cfopUfMismatchIsAlert() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 15, "100.00", "100.00", "12345678", "6102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 15));
        assertEquals("IMP-016", d.findings().get(0).ruleId());
    }

    @Test
    void folderCollectionIsRecursiveWhenAsked() throws Exception {
        setup();
        File sub = new File(dir, "sub");
        sub.mkdirs();
        Files.write(new File(sub, "x.XML").toPath(), "<a/>".getBytes(StandardCharsets.UTF_8));
        write("y.xml", "<a/>");
        assertEquals(1, FiscalService.collect(dir, false).size());
        assertEquals(2, FiscalService.collect(dir, true).size());
    }

    // ------------------------------------------------------------------ v0.1.1

    static String event(String key, String type, String cStat, String text) {
        String det = FiscalEvent.CANCELAMENTO.equals(type)
                ? "<descEvento>Cancelamento</descEvento><nProt>131000000000001</nProt><xJust>" + text + "</xJust>"
                : "<descEvento>Carta de Correcao</descEvento><xCorrecao>" + text + "</xCorrecao>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><procEventoNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">"
                + "<evento versao=\"1.00\"><infEvento Id=\"ID" + type + key + "01\"><cOrgao>31</cOrgao><tpAmb>1</tpAmb><CNPJ>" + L5 + "</CNPJ>"
                + "<chNFe>" + key + "</chNFe><dhEvento>2026-08-06T12:00:00-03:00</dhEvento><tpEvento>" + type + "</tpEvento>"
                + "<nSeqEvento>1</nSeqEvento><verEvento>1.00</verEvento><detEvento versao=\"1.00\">" + det + "</detEvento></infEvento></evento>"
                + "<retEvento versao=\"1.00\"><infEvento><tpAmb>1</tpAmb><cStat>" + cStat + "</cStat><chNFe>" + key + "</chNFe></infEvento></retEvento>"
                + "</procEventoNFe>";
    }

    private static boolean has(FiscalDoc d, String rule) {
        for (Finding f : d.findings()) {
            if (f.ruleId().equals(rule)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void cancellationEventImportedAfterTheNoteMakesItAnError() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 20, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        assertEquals(DocStatus.CONFORME, only(Direction.SAIDA, key(L5, 20)).status());
        File ev = write("ev.xml", event(key(L5, 20), FiscalEvent.CANCELAMENTO, "135", "Erro na emissao"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(ev), Direction.SAIDA, company, null);
        assertEquals(1, r.evento);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 20));
        assertEquals(DocStatus.ERRO, d.status());
        assertTrue(has(d, "ERR-1.1"));
        FiscalService.ImportResult again = service.importFiles(java.util.Arrays.asList(ev), Direction.SAIDA, company, null);
        assertEquals(1, again.repetida);
        assertEquals(0, again.evento);
    }

    @Test
    void eventImportedBeforeTheNoteIsAppliedWhenTheNoteArrives() throws Exception {
        setup();
        File ev = write("ev.xml", event(key(L5, 21), FiscalEvent.CARTA_CORRECAO, "135", "Corrige o valor do frete"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(ev), Direction.SAIDA, company, null);
        assertEquals(1, r.evento);
        File f = write("a.xml", nfe(L5, OTHER, 21, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 21));
        assertTrue(has(d, "ERR-1.10"));
        assertEquals(DocStatus.ALERTA, d.status());
    }

    @Test
    void unregisteredEventIsIgnored() throws Exception {
        setup();
        File ev = write("ev.xml", event(key(L5, 22), FiscalEvent.CANCELAMENTO, "573", "x"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(ev), Direction.SAIDA, company, null);
        assertEquals(1, r.ignorada);
        assertEquals(0, r.evento);
    }

    @Test
    void municipalNfseIsRecognisedAndKeptAsError() throws Exception {
        setup();
        File f = write("m.xml", "<?xml version=\"1.0\"?><CompNfse xmlns=\"http://www.abrasf.org.br/nfse.xsd\"><Nfse><InfNfse>"
                + "<Numero>1</Numero></InfNfse></Nfse></CompNfse>");
        service.importFiles(java.util.Arrays.asList(f), Direction.ENTRADA, company, null);
        FiscalDoc d = service.repository().list(company.id(), Direction.ENTRADA).get(0);
        assertEquals(DocStatus.ERRO, d.status());
        assertEquals("ERR-1.12", d.findings().get(0).ruleId());
        service.revalidateAll(Direction.ENTRADA, company);
        assertEquals("ERR-1.12", service.repository().load(d.id()).findings().get(0).ruleId());
    }

    @Test
    void cstOverrideIsCheckedAgainstTheXmlGroupOnReview() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 23, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(L5, 23));
        Map<String, String> ov = new LinkedHashMap<String, String>();
        ov.put(FiscalDoc.itemKey(0, "cst"), "40");
        FiscalDoc reviewed = service.review(d.id(), ov, company);
        assertTrue(has(reviewed, "ERR-7.7"));
    }

    @Test
    void databaseIsMigratedToSchema4() throws Exception {
        setup();
        assertEquals(4, new Database(new File(dir, "t.db")).schemaVersion());
    }

    @Test
    void databaseFromVersion001IsMigratedKeepingOwnNotesAndDroppingAlienOnes() throws Exception {
        setup();
        File f = write("a.xml", nfe(L5, OTHER, 24, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        // nota alheia como a v0.0.1 gravava: com IMP-003, e uma duplicata dela
        FiscalDoc alien = new FiscalDoc();
        alien.setKind(DocKind.NFE);
        alien.setCompanyId(company.id());
        alien.setDirection(Direction.SAIDA);
        alien.setAccessKey(key(OTHER, 25));
        alien.setStatus(DocStatus.ERRO);
        alien.setXmlHash("h1");
        alien.setSourcePath("x.xml");
        alien.findings().add(new Finding("IMP-003", Severity.ERRO, "issuer_doc", "outra empresa", null));
        long alienId = service.repository().insert(alien);
        FiscalDoc dup = new FiscalDoc();
        dup.setKind(DocKind.NFE);
        dup.setCompanyId(company.id());
        dup.setDirection(Direction.SAIDA);
        dup.setAccessKey(key(OTHER, 25));
        dup.setStatus(DocStatus.DUPLICADA);
        dup.setDuplicateOf(alienId);
        dup.setXmlHash("h2");
        dup.setSourcePath("y.xml");
        service.repository().insert(dup);
        File dbFile = new File(dir, "t.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             java.sql.Statement st = c.createStatement()) {
            st.execute("DROP TABLE fiscal_event");
            st.execute("PRAGMA user_version = 2"); // banco como a v0.0.1 deixava
        }
        Database reopened = new Database(dbFile);
        assertEquals(4, reopened.schemaVersion());
        FiscalService again = new FiscalService(new FiscalDocRepository(reopened));
        List<FiscalDoc> left = again.repository().list(company.id(), Direction.SAIDA);
        assertEquals(1, left.size());
        assertEquals(key(L5, 24), left.get(0).accessKey());
        assertEquals("2", new CompanyRepository(reopened).setting("migration_v4_removed"));
        again.revalidateAll(Direction.SAIDA, company);
        assertEquals(DocStatus.CONFORME, again.repository().list(company.id(), Direction.SAIDA).get(0).status());
    }
}
