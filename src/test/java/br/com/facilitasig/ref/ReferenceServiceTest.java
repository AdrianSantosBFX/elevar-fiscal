package br.com.facilitasig.ref;

import br.com.facilitasig.company.CompanyRepository;
import br.com.facilitasig.db.Database;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 23);

    private static File tempDir() throws IOException {
        return Files.createTempDirectory("fsig-ref").toFile();
    }

    private static ReferenceService shared;

    /** Banco carregado uma vez para os testes de leitura (a carga do ISS leva alguns segundos). */
    static synchronized ReferenceService bundled() throws IOException {
        if (shared == null) {
            ReferenceService refs = ReferenceService.open(new File(tempDir(), "ref.db"));
            for (ReferenceLoader.Entry e : refs.loadBundled()) {
                assertEquals(ReferenceLoader.Action.CARREGADO, e.action(), e.toString());
            }
            shared = refs;
        }
        return shared;
    }

    // ------------------------------------------------------------------ carga

    @Test
    void bundledTablesLoadWithExpectedCounts() throws Exception {
        ReferenceService refs = bundled();
        assertEquals(15157, refs.ncmCount());
        assertEquals(8, refs.datasets().size());
        assertEquals(9, refs.cstCodes(CstTable.ORIGEM, null).size());
        assertEquals(15, refs.cstCodes(CstTable.CST_ICMS, null).size());
        assertEquals(10, refs.cstCodes(CstTable.CSOSN, null).size());
        assertEquals(14, refs.cstCodes(CstTable.CST_IPI, null).size());
        assertEquals(33, refs.cstCodes(CstTable.CST_PIS_COFINS, null).size());
        Dataset ncm = refs.dataset("ncm");
        assertEquals(SourceGrade.OFICIAL, ncm.grade());
        assertEquals(64, ncm.sha256().length());
        assertEquals(SourceGrade.SECUNDARIA, refs.dataset("cst-planilha").grade());
        assertEquals(SourceGrade.CURADORIA, refs.dataset("cst-complemento").grade());
    }

    @Test
    void secondLoadIsUnchangedAndKeepsData() throws Exception {
        ReferenceService refs = bundled();
        List<ReferenceLoader.Entry> again = refs.loadBundled();
        for (ReferenceLoader.Entry e : again) {
            assertEquals(ReferenceLoader.Action.SEM_ALTERACAO, e.action(), e.toString());
        }
        assertEquals(15157, refs.ncmCount());
    }

    @Test
    void referenceDatabaseIsSeparateFromCompanyDatabase() throws Exception {
        File dir = tempDir();
        File main = new File(dir, "elevar-fiscal.db");
        File ref = new File(dir, "elevar-fiscal-ref.db");
        new CompanyRepository(new Database(main));
        ReferenceService refs = ReferenceService.open(ref);
        refs.loadBundled();
        assertTrue(main.isFile());
        assertTrue(ref.isFile());
        assertEquals(3, new Database(ref, RefSchema.migrations()).schemaVersion());
        assertEquals(4, new Database(main).schemaVersion());
        // apagar o banco de referência não afeta o das empresas, e ele é recriado na abertura
        assertTrue(ref.delete());
        ReferenceService fresh = ReferenceService.open(ref);
        assertEquals(0, fresh.ncmCount());
        fresh.loadBundled();
        assertEquals(15157, fresh.ncmCount());
        assertEquals(4, new Database(main).schemaVersion());
    }

    // ------------------------------------------------------------------ NCM

    @Test
    void ncmLookupStatuses() throws Exception {
        ReferenceService refs = bundled();
        Lookup<Ncm> ok = refs.lookupNcm("0101.21.00", HOJE);
        assertEquals(LookupStatus.ENCONTRADO, ok.status());
        assertEquals("01012100", ok.value().code());
        assertTrue(ok.value().isItemCode());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupNcm("01012100", LocalDate.of(2023, 1, 10)).status());

        // código inexistente, depois da data da tabela: não encontrado (conclusivo)
        assertEquals(LookupStatus.NAO_ENCONTRADO, refs.lookupNcm("99999999", HOJE).status());
        // mesmo código, numa nota antiga: a tabela só tem os vigentes em 21/10/2026 -> indeterminado
        assertEquals(LookupStatus.SEM_COBERTURA, refs.lookupNcm("99999999", LocalDate.of(2025, 5, 1)).status());
        // antes da NCM 2022 a tabela não responde
        assertEquals(LookupStatus.SEM_COBERTURA, refs.lookupNcm("01012100", LocalDate.of(2021, 12, 31)).status());
        assertEquals(LookupStatus.NAO_ENCONTRADO, refs.lookupNcm("", HOJE).status());
    }

    @Test
    void ncmCreatedLaterIsOutOfValidityOnOlderDates() throws Exception {
        ReferenceService refs = bundled();
        List<Ncm> recent = refs.searchNcm("", null, true, 20000);
        Ncm later = null;
        for (Ncm n : recent) {
            if (n.validity().start() != null && n.validity().start().isAfter(LocalDate.of(2023, 1, 1))) {
                later = n;
                break;
            }
        }
        assertNotNull(later, "a tabela tem códigos criados depois de 2022");
        LocalDate before = later.validity().start().minusDays(1);
        Lookup<Ncm> l = refs.lookupNcm(later.code(), before);
        assertEquals(LookupStatus.FORA_DE_VIGENCIA, l.status(), l.message());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupNcm(later.code(), later.validity().start()).status());
    }

    @Test
    void ncmSearchByCodePrefixAndWords() throws Exception {
        ReferenceService refs = bundled();
        List<Ncm> byCode = refs.searchNcm("0101", HOJE, false, 100);
        assertFalse(byCode.isEmpty());
        for (Ncm n : byCode) {
            assertTrue(n.code().startsWith("0101"));
        }
        assertEquals("0101", byCode.get(0).code());
        List<Ncm> items = refs.searchNcm("0101", HOJE, true, 100);
        for (Ncm n : items) {
            assertEquals(8, n.code().length());
        }
        // sem acento e em maiúsculas encontra "raça pura"
        List<Ncm> words = refs.searchNcm("REPRODUTORES RACA PURA cavalos", HOJE, true, 100);
        boolean has = false;
        for (Ncm n : words) {
            has |= n.code().equals("01012100");
        }
        assertTrue(has);
    }

    @Test
    void ncmHierarchyGoesFromChapterToItem() throws Exception {
        ReferenceService refs = bundled();
        List<Ncm> h = refs.ncmHierarchy("0101.21.00");
        assertEquals("01", h.get(0).code());
        assertEquals("Capítulo", h.get(0).levelName());
        assertEquals("01012100", h.get(h.size() - 1).code());
        assertTrue(h.size() >= 4);
    }

    // ------------------------------------------------------------------ CST

    @Test
    void missingCodesWereAdded() throws Exception {
        ReferenceService refs = bundled();
        for (String code : new String[]{"02", "15", "53", "61"}) {
            Lookup<CstCode> l = refs.lookupCst(CstTable.CST_ICMS, code, HOJE);
            assertEquals(LookupStatus.ENCONTRADO, l.status(), code);
            assertTrue(l.value().description().toLowerCase().contains("monofásica"));
            assertEquals("cst-complemento", l.value().dataset());
        }
        // monofásico de combustíveis não existia antes de 2023
        assertEquals(LookupStatus.FORA_DE_VIGENCIA, refs.lookupCst(CstTable.CST_ICMS, "61", LocalDate.of(2022, 6, 1)).status());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupCst(CstTable.CST_IPI, "50", HOJE).status());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupCst(CstTable.CST_PIS_COFINS, "01", HOJE).status());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupCst(CstTable.CST_PIS_COFINS, "75", HOJE).status());
        assertEquals(LookupStatus.FORA_DE_VIGENCIA, refs.lookupCst(CstTable.CST_PIS_COFINS, "01", LocalDate.of(2010, 3, 31)).status());
    }

    @Test
    void cstCodesAreNormalizedAndUnknownCodesRejected() throws Exception {
        ReferenceService refs = bundled();
        assertEquals("00", refs.lookupCst(CstTable.CST_ICMS, "0", HOJE).value().code());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupCst(CstTable.ORIGEM, "8", HOJE).status());
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupCst(CstTable.CSOSN, "101", HOJE).status());
        assertEquals(LookupStatus.NAO_ENCONTRADO, refs.lookupCst(CstTable.CST_ICMS, "45", HOJE).status());
        assertEquals(LookupStatus.NAO_ENCONTRADO, refs.lookupCst(CstTable.CSOSN, "104", HOJE).status());
        assertEquals(LookupStatus.NAO_ENCONTRADO, refs.lookupCst(CstTable.ORIGEM, "9", HOJE).status());
        // planilha sem data de início: vale em qualquer data
        assertEquals(LookupStatus.ENCONTRADO, refs.lookupCst(CstTable.CST_ICMS, "00", LocalDate.of(2005, 1, 1)).status());
    }

    @Test
    void typosOfTheSpreadsheetWereFixed() throws Exception {
        ReferenceService refs = bundled();
        for (CstTable t : CstTable.values()) {
            for (CstCode c : refs.cstCodes(t, null)) {
                String d = c.description();
                assertFalse(d.contains("Nascional") || d.contains("Decretp") || d.contains("indaicada")
                        || d.contains("Substituião") || d.contains("na mercado"), d);
                assertFalse(c.legalBasis().isEmpty());
            }
        }
    }

    // ------------------------------------------------------------------ fonte alterada / com erro

    private static final class MemorySource implements ReferenceLoader.Source {
        final Map<String, String> files = new HashMap<String, String>();

        @Override
        public java.io.InputStream open(String name) throws IOException {
            String s = files.get(name);
            if (s == null) {
                throw new IOException("sem arquivo " + name);
            }
            return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final String MANIFEST = "cst-x\tcst.tsv\tCST\tTeste\tCURADORIA\tNorma de teste\t2026-01-01\t\t\t\t\n";

    @Test
    void changedFileIsReloadedAndBrokenFileKeepsPreviousVersion() throws Exception {
        ReferenceService refs = ReferenceService.open(new File(tempDir(), "ref.db"));
        MemorySource src = new MemorySource();
        src.files.put("manifest.tsv", MANIFEST);
        src.files.put("cst.tsv", "CST_IPI\t50\tSaída tributada\t2010-04-01\t\tIN RFB 1.009/2010\t\n");
        assertEquals(ReferenceLoader.Action.CARREGADO, refs.load(src).get(0).action());
        assertEquals(1, refs.cstCodes(CstTable.CST_IPI, null).size());

        src.files.put("cst.tsv", "CST_IPI\t50\tSaída tributada\t2010-04-01\t\tIN RFB 1.009/2010\t\n"
                + "CST_IPI\t99\tOutras saídas\t2010-04-01\t\tIN RFB 1.009/2010\t\n");
        assertEquals(ReferenceLoader.Action.CARREGADO, refs.load(src).get(0).action());
        assertEquals(2, refs.cstCodes(CstTable.CST_IPI, null).size());

        src.files.put("cst.tsv", "CST_IPI\t50\tlinha quebrada\n");
        ReferenceLoader.Entry broken = refs.load(src).get(0);
        assertEquals(ReferenceLoader.Action.ERRO, broken.action());
        assertEquals(2, refs.cstCodes(CstTable.CST_IPI, null).size(), "a versão anterior continua valendo");

        src.files.put("manifest.tsv", "# vazio\n");
        List<ReferenceLoader.Entry> removed = refs.load(src);
        assertEquals(ReferenceLoader.Action.REMOVIDO, removed.get(0).action());
        assertEquals(0, refs.cstCodes(CstTable.CST_IPI, null).size());
        assertEquals(LookupStatus.SEM_COBERTURA, refs.lookupCst(CstTable.CST_IPI, "50", HOJE).status());
    }

    @Test
    void emptyDatabaseAnswersSemCobertura() throws Exception {
        ReferenceService refs = ReferenceService.open(new File(tempDir(), "ref.db"));
        assertEquals(LookupStatus.SEM_COBERTURA, refs.lookupNcm("01012100", HOJE).status());
        assertEquals(LookupStatus.SEM_COBERTURA, refs.lookupCst(CstTable.CST_ICMS, "00", HOJE).status());
    }

    @Test
    void searchKeyIgnoresAccentsAndCase() {
        assertEquals("raca pura 0101 21 00", ReferenceService.searchKey("Raça  Pura — 0101.21.00"));
    }
}
