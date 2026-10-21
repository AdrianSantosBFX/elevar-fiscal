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

    private static final String DEMO = "12345678000195";
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
        company = new CompanyRepository(db).save(SampleCompanies.demo());
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
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<NFe><infNFe Id=\"NFe" + k + "\" versao=\"4.00\"><ide><cUF>31</cUF><natOp>Venda de mercadoria</natOp><mod>55</mod>"
                + "<serie>1</serie><nNF>" + number + "</nNF><dhEmi>2026-08-06T10:00:00-03:00</dhEmi><tpNF>1</tpNF><idDest>1</idDest>"
                + "<tpAmb>1</tpAmb></ide>"
                + "<emit><CNPJ>" + issuer + "</CNPJ><xNome>EMITENTE LTDA</xNome><enderEmit><cMun>3170701</cMun><UF>" + issuerUf + "</UF></enderEmit><IE>0000000000000</IE></emit>"
                + "<dest><CNPJ>" + recipient + "</CNPJ><xNome>DESTINATARIO SA</xNome><enderDest><cMun>3170701</cMun><UF>" + recipUf + "</UF></enderDest><IE>123</IE></dest>"
                + "<det nItem=\"1\"><prod><cProd>1</cProd><xProd>Produto</xProd><NCM>" + ncm + "</NCM><CFOP>" + cfop + "</CFOP><uCom>UN</uCom>"
                + "<qCom>1</qCom><vUnCom>" + vProd + "</vUnCom><vProd>" + vProd + "</vProd></prod>"
                + "<imposto><ICMS><ICMS00><CST>00</CST><vICMS>0.00</vICMS></ICMS00></ICMS><PIS><PISAliq><CST>01</CST><vPIS>0.00</vPIS></PISAliq></PIS></imposto></det>"
                + "<total><ICMSTot><vICMS>0.00</vICMS><vProd>" + vProd + "</vProd><vNF>" + vNF + "</vNF></ICMSTot></total></infNFe></NFe>"
                + "<protNFe><infProt><cStat>100</cStat><nProt>131000000000001</nProt></infProt></protNFe></nfeProc>";
    }

    static String nfse(String provider, String taker, String number) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NFSe xmlns=\"http://www.sped.fazenda.gov.br/nfse\" versao=\"1.00\">"
                + "<infNFSe Id=\"NFS35503082" + provider + number + "\"><xTribNac>Serviços de contabilidade</xTribNac><nNFSe>" + number + "</nNFSe><cStat>100</cStat>"
                + "<emit><CNPJ>" + provider + "</CNPJ><xNome>PRESTADOR LTDA</xNome><enderNac><cMun>3550308</cMun><UF>SP</UF></enderNac></emit>"
                + "<valores><vISSQN>20.00</vISSQN></valores>"
                + "<DPS versao=\"1.00\"><infDPS><tpAmb>1</tpAmb><dhEmi>2026-09-03T09:00:00-03:00</dhEmi><serie>1</serie><nDPS>" + number + "</nDPS><dCompet>2026-09-03</dCompet>"
                + "<toma><CNPJ>" + taker + "</CNPJ><xNome>TOMADOR</xNome><end><endNac><cMun>3170701</cMun></endNac></end></toma>"
                + "<serv><cServ><cTribNac>171901</cTribNac><xDescServ>Serviço contábil</xDescServ></cServ></serv>"
                + "<valores><vServPrest><vServ>1000.00</vServ></vServPrest><trib><tribMun><tpRetISSQN>2</tpRetISSQN></tribMun></trib></valores>"
                + "</infDPS></DPS></infNFSe></NFSe>";
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
        File f = write("a.xml", nfe(DEMO, OTHER, 10, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        FiscalService.ImportResult r = service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        assertEquals(1, r.conforme);
        FiscalDoc d = only(Direction.SAIDA, key(DEMO, 10));
        assertEquals(DocStatus.CONFORME, d.status());
        assertEquals("2026-08-06", d.get("entry_exit_date"));
    }

    @Test
    void duplicateIsRecordedOnceAndOriginalKept() throws Exception {
        setup();
        File f = write("a.xml", nfe(DEMO, OTHER, 11, "10.00", "10.00", "12345678", "5102", "MG", "MG"));
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
        File f = write("a.xml", nfe(DEMO, OTHER, 12, "100.00", "90.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(DEMO, 12));
        assertEquals(DocStatus.ALERTA, d.status());
        assertEquals("IMP-008", d.findings().get(0).ruleId());
    }

    @Test
    void noteOfAnotherCompanyIsError() throws Exception {
        setup();
        File f = write("a.xml", nfe(OTHER, DEMO.replace("12345678000195", OTHER), 13, "100.00", "100.00", "12345678", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(OTHER, 13));
        assertEquals(DocStatus.ERRO, d.status());
        assertEquals("IMP-003", d.findings().get(0).ruleId());
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
        File f = write("s.xml", nfse(OTHER, DEMO, "77"));
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
        File f = write("a.xml", nfe(DEMO, OTHER, 14, "100.00", "100.00", "ABC", "5102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(DEMO, 14));
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
        File f = write("a.xml", nfe(DEMO, OTHER, 15, "100.00", "100.00", "12345678", "6102", "MG", "MG"));
        service.importFiles(java.util.Arrays.asList(f), Direction.SAIDA, company, null);
        FiscalDoc d = only(Direction.SAIDA, key(DEMO, 15));
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
}
