package br.com.facilitasig.ref;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class IcmsIssTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 23);

    private static ReferenceService refs() throws IOException {
        return ReferenceServiceTest.bundled();
    }

    private static BigDecimal pct(String s) {
        return new BigDecimal(s);
    }

    private static void assertRate(String expected, Lookup<?> l) {
        assertEquals(LookupStatus.ENCONTRADO, l.status(), l.message());
        Object v = l.value();
        BigDecimal rate = v instanceof IcmsRate ? ((IcmsRate) v).rate() : ((IssRate) v).rate();
        assertEquals(0, pct(expected).compareTo(rate), l.message());
    }

    // ------------------------------------------------------------------ ICMS

    @Test
    void interstateRatesFollowSenateResolutions() throws Exception {
        ReferenceService r = refs();
        assertRate("7", r.icmsInterstate("MG", "BA", "0", HOJE));
        assertRate("12", r.icmsInterstate("BA", "MG", "0", HOJE));
        assertRate("7", r.icmsInterstate("SP", "ES", null, HOJE));     // ES recebe 7% do Sul/Sudeste
        assertRate("12", r.icmsInterstate("ES", "SP", null, HOJE));
        assertRate("12", r.icmsInterstate("SP", "RJ", "0", HOJE));
        assertRate("4", r.icmsInterstate("MG", "SP", "1", HOJE));      // importado
        assertRate("4", r.icmsInterstate("MG", "BA", "8", HOJE));
        assertRate("7", r.icmsInterstate("MG", "BA", "6", HOJE));      // sem similar (CAMEX): regra geral
        assertRate("12", r.icmsInterstate("MG", "SP", "1", LocalDate.of(2012, 12, 31))); // antes da Res. 13/2012
        assertEquals(LookupStatus.SEM_COBERTURA, r.icmsInterstate("MG", "BA", "0", LocalDate.of(1989, 6, 1)).status());
        assertEquals(LookupStatus.NAO_ENCONTRADO, r.icmsInterstate("MG", "XX", "0", HOJE).status());
        assertTrue(r.icmsInterstate("MG", "BA", null, HOJE).message().contains("considerada nacional"));
    }

    @Test
    void internalRatesHaveValidityAndFcpApart() throws Exception {
        ReferenceService r = refs();
        assertRate("18", r.icmsInternal("MG", HOJE));
        assertRate("18", r.icmsInterstate("MG", "MG", "0", HOJE));     // mesma UF = interna
        assertRate("22", r.icmsInternal("MA", LocalDate.of(2025, 2, 22)));
        assertRate("23", r.icmsInternal("MA", LocalDate.of(2025, 2, 23)));
        assertRate("21", r.icmsInternal("PI", LocalDate.of(2025, 3, 31)));
        assertRate("22.5", r.icmsInternal("PI", LocalDate.of(2025, 4, 1)));
        assertRate("18", r.icmsInternal("RN", LocalDate.of(2025, 3, 19)));
        assertRate("20", r.icmsInternal("RN", LocalDate.of(2025, 3, 20)));
        assertRate("19", r.icmsInternal("AL", LocalDate.of(2026, 3, 31)));
        assertRate("20.5", r.icmsInternal("AL", LocalDate.of(2026, 4, 1)));
        Lookup<IcmsRate> rj = r.icmsInternal("RJ", HOJE);
        assertRate("20", rj);
        assertEquals(0, pct("2").compareTo(rj.value().fcp()));
        assertEquals("20% + 2% FCP", rj.value().display());
        assertNull(r.icmsInternal("SP", HOJE).value().fcp());
        assertEquals(LookupStatus.SEM_COBERTURA, r.icmsInternal("MG", LocalDate.of(2024, 12, 31)).status());
        assertEquals(LookupStatus.NAO_ENCONTRADO, r.icmsInternal("ZZ", HOJE).status());
    }

    @Test
    void matrixHasEveryPairOnADate() throws Exception {
        ReferenceService r = refs();
        assertEquals(27 + 27 * 26, r.icmsMatrix(HOJE, false).size());
        for (IcmsRate x : r.icmsMatrix(HOJE, true)) {
            if (!x.isInternal()) {
                assertEquals(0, pct("4").compareTo(x.rate()));
            }
        }
        // antes de 2025 a diagonal (interna) fica de fora; a interestadual continua
        assertEquals(27 * 26, r.icmsMatrix(LocalDate.of(2020, 1, 1), false).size());
    }

    // ------------------------------------------------------------------ ISS (tabela oficial embutida)

    @Test
    void issOfficialTableIsComplete() throws Exception {
        ReferenceService r = refs();
        assertEquals(1918512, r.issCount());
        Dataset iss = r.dataset("iss");
        assertEquals(SourceGrade.OFICIAL, iss.grade());
        assertEquals(LocalDate.of(2026, 9, 3), iss.sourceDate());
        assertTrue(iss.notes().contains("5341 municípios"), iss.notes());
        assertTrue(iss.notes().contains("856 linhas com fim de vigência anterior ao início"), iss.notes());
        assertEquals(200, r.lc116().size());
    }

    @Test
    void issRateOfAlpinopolis() throws Exception {
        ReferenceService r = refs();
        Lookup<IssRate> l = r.issRate(3101904, "010101", HOJE);
        assertRate("2", l);
        assertEquals("01.01.01.000", l.value().serviceFormatted());
        assertEquals("Análise e desenvolvimento de sistemas", l.value().subitemDescription());
        assertTrue(l.value().withinLegalRange());
        assertEquals(l.status(), r.issRate(3101904, "01.01.01.000", HOJE).status());
        assertEquals(333, r.issRatesOf(3101904, HOJE).size());
        assertEquals("Alpinópolis", r.searchMunicipalities("alpinopolis", "MG", 10).get(0).name());
        assertEquals(3101904, r.searchMunicipalities("3101904", null, 10).get(0).ibge());
    }

    @Test
    void issUnknownSituationsAreIndeterminate() throws Exception {
        ReferenceService r = refs();
        // capital com emissor próprio: fora da tabela
        Lookup<IssRate> sp = r.issRate(3550308, "010101", HOJE);
        assertEquals(LookupStatus.SEM_COBERTURA, sp.status());
        assertTrue(sp.message().contains("não está na tabela"));
        // antes da parametrização
        assertEquals(LookupStatus.SEM_COBERTURA, r.issRate(3101904, "010101", LocalDate.of(2024, 1, 1)).status());
        // vigência publicada sem alíquota (campo vazio)
        Lookup<IssRate> empty = r.issRate(1200013, "150307", LocalDate.of(2026, 2, 1));
        assertEquals(LookupStatus.SEM_COBERTURA, empty.status());
        assertTrue(empty.message().contains("sem alíquota"), empty.message());
        assertRate("5", r.issRate(1200013, "150307", LocalDate.of(2026, 1, 19)));
        // duas alíquotas abertas ao mesmo tempo (Pouso Alto/MG, 04.01.01: 5% desde 29/12/2025 e 2% desde 05/01/2026)
        Lookup<IssRate> conflict = r.issRate(3152600, "040101", HOJE);
        assertEquals(LookupStatus.SEM_COBERTURA, conflict.status());
        assertTrue(conflict.message().contains("mais de uma alíquota"), conflict.message());
        assertRate("5", r.issRate(3152600, "040101", LocalDate.of(2026, 1, 2)));
        // código mal formado
        assertEquals(LookupStatus.NAO_ENCONTRADO, r.issRate(3101904, "0101", HOJE).status());
    }

    // ------------------------------------------------------------------ importação de arquivo novo do Portal

    private static final String HEADER = IssImporter.HEADER;

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static final class MemorySource implements ReferenceLoader.Source {
        final Map<String, byte[]> files = new HashMap<String, byte[]>();

        @Override
        public InputStream open(String name) throws IOException {
            byte[] b = files.get(name);
            if (b == null) {
                throw new IOException("sem arquivo " + name);
            }
            return new ByteArrayInputStream(b);
        }
    }

    @Test
    void importedNewerFileReplacesAndSurvivesRestart() throws Exception {
        File dir = Files.createTempDirectory("fsig-iss").toFile();
        ReferenceService r = ReferenceService.open(new File(dir, "ref.db"));
        MemorySource src = new MemorySource();
        src.files.put("manifest.tsv", ("iss\tiss.txt.gz\tISS\tAlíquotas de ISS\tOFICIAL\tPortal NFS-e\t2026-09-03\t\t\t\t\n")
                .getBytes(StandardCharsets.UTF_8));
        src.files.put("iss.txt.gz", gzip("﻿" + HEADER + "\r\n"
                + "3101904;MG;Alpinópolis;01.01.01.000;01.01.01.000;2;2025-12-23T00:00:00;\r\n"
                + "3101904;MG;Alpinópolis;01.02.01.000;01.02.01.000;5;2025-12-23T00:00:00;2025-12-01T00:00:00\r\n"));
        List<ReferenceLoader.Entry> first = r.load(src);
        assertEquals(ReferenceLoader.Action.CARREGADO, first.get(0).action(), first.toString());
        assertEquals(1, r.issCount());
        assertTrue(first.get(0).message().contains("1 linhas com fim de vigência anterior ao início"));

        File newer = new File(dir, "aliquotas-municipios-20261201-extr1.txt");
        Files.write(newer.toPath(), (HEADER + "\n"
                + "3101904;MG;Alpinópolis;01.01.01.000;01.01.01.000;2;2025-12-23T00:00:00;2026-11-30T00:00:00\n"
                + "3101904;MG;Alpinópolis;01.01.01.000;01.01.01.000;3;2026-12-01T00:00:00;\n"
                + "3152600;MG;Pouso Alto;04.01.01.000;04.01.01.000;;2026-01-05T00:00:00;\n").getBytes(StandardCharsets.UTF_8));
        ReferenceLoader.Entry imp = r.importIss(newer, src, null);
        assertEquals(ReferenceLoader.Action.CARREGADO, imp.action(), imp.toString());
        assertEquals(3, r.issCount());
        assertEquals(Dataset.ORIGIN_IMPORTED, r.dataset("iss").origin());
        assertEquals(LocalDate.of(2026, 12, 1), r.dataset("iss").sourceDate());
        assertRate("3", r.issRate(3101904, "010101", LocalDate.of(2026, 12, 15)));
        assertRate("2", r.issRate(3101904, "010101", LocalDate.of(2026, 11, 30)));
        assertNotNull(r.municipality(3152600));

        // nova abertura do sistema: o arquivo embutido (mais antigo) não sobrescreve o importado
        ReferenceLoader.Entry again = r.load(src).get(0);
        assertEquals(ReferenceLoader.Action.SEM_ALTERACAO, again.action());
        assertTrue(again.message().contains("importado"), again.message());
        assertEquals(3, r.issCount());

        // arquivo errado: a versão importada continua valendo
        File wrong = new File(dir, "planilha-qualquer.csv");
        Files.write(wrong.toPath(), "a;b;c\n1;2;3\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(ReferenceLoader.Action.ERRO, r.importIss(wrong, src, null).action());
        assertEquals(3, r.issCount());
    }

    @Test
    void zipWithStateCsvFilesIsAccepted() throws Exception {
        File dir = Files.createTempDirectory("fsig-iss").toFile();
        ReferenceService r = ReferenceService.open(new File(dir, "ref.db"));
        MemorySource src = new MemorySource();
        src.files.put("manifest.tsv", ("iss\tiss.txt.gz\tISS\tAlíquotas de ISS\tOFICIAL\tPortal NFS-e\t2026-09-03\t\t\t\t\n")
                .getBytes(StandardCharsets.UTF_8));
        File zip = new File(dir, "aliquotas-municipios-20270105-extr1.zip");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
            z.putNextEntry(new java.util.zip.ZipEntry("MG.csv"));
            z.write((HEADER + "\n3101904;MG;Alpinópolis;01.01.01.000;01.01.01.000;2,5;2026-01-01T00:00:00;\n").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("AC.csv"));
            z.write((HEADER + "\n1200013;AC;Acrelândia;15.03.07.000;15.03.07.000;5;2026-01-19T00:00:00;\n").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        ReferenceLoader.Entry e = r.importIss(zip, src, null);
        assertEquals(ReferenceLoader.Action.CARREGADO, e.action(), e.toString());
        assertEquals(2, r.issCount());
        assertRate("2.5", r.issRate(3101904, "010101", HOJE));
        assertEquals(LocalDate.of(2027, 1, 5), r.dataset("iss").sourceDate());
    }

    @Test
    void referenceDatabaseFromV013IsUpgradedKeepingData() throws Exception {
        File file = new File(Files.createTempDirectory("fsig-up").toFile(), "ref.db");
        br.com.facilitasig.db.Database v1 = new br.com.facilitasig.db.Database(file, new String[][]{RefSchema.v1()});
        ReferenceLoader.Source onlyNcm = name -> {
            if ("manifest.tsv".equals(name)) {
                return new ByteArrayInputStream(("ncm\tncm.tsv.gz\tNCM\tNCM\tOFICIAL\tGecex\t2026-10-21\t\t2022-04-01\t2026-10-21\t\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return ReferenceLoader.classpath().open(name);
        };
        // carga no esquema v1 não conhece a coluna origin: grava direto, como a v0.1.3 fazia
        try (java.sql.Connection c = v1.connect(); java.sql.Statement st = c.createStatement()) {
            st.execute("INSERT INTO ref_dataset (code, kind, title, grade, source, file, sha256, row_count, loaded_at) VALUES "
                    + "('ncm','NCM','NCM','OFICIAL','Gecex','ncm.tsv.gz','" + new String(new char[64]).replace('\0', 'a') + "',1,'x')");
            st.execute("INSERT INTO ref_ncm (code, formatted, description, search_text, dataset) VALUES "
                    + "('01012100','0101.21.00','Reprodutores','reprodutores','ncm')");
        }
        assertEquals(1, v1.schemaVersion());
        ReferenceService r = ReferenceService.open(file);
        assertEquals(1, r.ncmCount(), "dados da v0.1.3 mantidos na migração");
        assertEquals(Dataset.ORIGIN_BUNDLED, r.dataset("ncm").origin());
        r.load(onlyNcm);
        assertEquals(15157, r.ncmCount());
    }

    @Test
    void rateParsing() {
        assertEquals(250, IssImporter.rateX100("2,5"));
        assertEquals(499, IssImporter.rateX100("4,99"));
        assertEquals(500, IssImporter.rateX100("5"));
        assertEquals(20260101, IssImporter.date("2026-01-01T00:00:00"));
        assertEquals("01.01.01.000", IssImporter.formatService(IssImporter.serviceCode("01.01.01.000")));
    }
}
