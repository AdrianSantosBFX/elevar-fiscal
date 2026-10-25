package br.com.facilitasig.oa;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.IeSituation;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.company.SampleCompanies;
import br.com.facilitasig.company.StateRegistration;
import br.com.facilitasig.company.TaxRegime;
import br.com.facilitasig.company.Declared;
import br.com.facilitasig.ref.Obligation;
import br.com.facilitasig.ref.ReferenceLoader;
import br.com.facilitasig.ref.ReferenceService;
import br.com.facilitasig.ref.SourceGrade;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObligationsTest {

    private static ObligationService service;
    private static ReferenceService refs;

    /** Carrega só o catálogo de obrigações (rápido; sem o ISS). */
    private static synchronized ObligationService service() throws IOException {
        if (service == null) {
            refs = ReferenceService.open(new File(Files.createTempDirectory("fsig-oa").toFile(), "ref.db"));
            ReferenceLoader.Source src = name -> {
                if ("manifest.tsv".equals(name)) {
                    String line = null;
                    for (String l : new String(readAll(ReferenceLoader.classpath().open(name)), StandardCharsets.UTF_8).split("\n")) {
                        if (l.startsWith("obrigacoes\t")) {
                            line = l;
                        }
                    }
                    return new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
                return ReferenceLoader.classpath().open(name);
            };
            List<ReferenceLoader.Entry> r = refs.load(src);
            assertEquals(ReferenceLoader.Action.CARREGADO, r.get(0).action(), r.toString());
            service = new ObligationService(refs.obligations());
        }
        return service;
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        try (java.io.InputStream i = in) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = i.read(b)) > 0) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static Applicability find(List<Applicability> list, String code) {
        Applicability found = null;
        for (Applicability a : list) {
            if (a.obligation().code().equals(code)) {
                assertNull(found, "obrigação repetida: " + code);
                found = a;
            }
        }
        return found;
    }

    private static Company withRegime(Company c, TaxRegime r) {
        c.regimePeriods().clear();
        c.regimePeriods().add(new RegimePeriod(r, LocalDate.of(2000, 1, 1), Declared.<LocalDate>none(), Declared.<String>none()));
        return c;
    }

    // ------------------------------------------------------------------ calendário e prazos

    @Test
    void easterAndNationalHolidays() {
        assertEquals(LocalDate.of(2026, 4, 5), BusinessCalendar.easter(2026));
        assertEquals(LocalDate.of(2027, 3, 28), BusinessCalendar.easter(2027));
        assertFalse(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 2, 16)));   // Carnaval
        assertFalse(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 4, 3)));    // Sexta-feira Santa
        assertFalse(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 6, 4)));    // Corpus Christi
        assertFalse(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 11, 20)));  // Consciência Negra
        assertTrue(BusinessCalendar.isBusinessDay(LocalDate.of(2023, 11, 20)));   // antes da Lei 14.759/2023
        assertFalse(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 10, 24)));  // sábado
    }

    @Test
    void deadlineRules() {
        assertEquals(LocalDate.of(2026, 11, 16), DeadlineRule.parse("DU:2:10").dueDate(YearMonth.of(2026, 9)));  // EFD-Contribuições
        assertEquals(LocalDate.of(2026, 10, 15), DeadlineRule.parse("DU:2:10").dueDate(YearMonth.of(2026, 8)));  // 12/10 feriado
        assertEquals(LocalDate.of(2026, 10, 30), DeadlineRule.parse("UDU:1").dueDate(YearMonth.of(2026, 9)));    // 31/10 é sábado
        assertEquals(LocalDate.of(2026, 6, 30), DeadlineRule.parse("UDU:6").dueDate(YearMonth.of(2025, 12)));    // ECD 2025
        assertEquals(LocalDate.of(2026, 7, 31), DeadlineRule.parse("UDU:7").dueDate(YearMonth.of(2025, 12)));    // ECF 2025
        assertEquals(LocalDate.of(2026, 11, 13), DeadlineRule.parse("DIA:1:15:A").dueDate(YearMonth.of(2026, 10))); // 15/11 domingo
        assertEquals(LocalDate.of(2026, 11, 16), DeadlineRule.parse("DIA:1:15:P").dueDate(YearMonth.of(2026, 10)));
        assertEquals(LocalDate.of(2026, 11, 15), DeadlineRule.parse("DIA:1:15:F").dueDate(YearMonth.of(2026, 10)));
        assertNull(DeadlineRule.parse("UF").dueDate(YearMonth.of(2026, 10)));
        assertThrows(IllegalArgumentException.class, () -> DeadlineRule.parse("DIA:1:40:A"));
        assertThrows(IllegalArgumentException.class, () -> DeadlineRule.parse("XYZ"));
    }

    // ------------------------------------------------------------------ catálogo

    @Test
    void catalogIsLoadedAsCuratedDataset() throws Exception {
        ObligationService s = service();
        assertEquals(20, s.catalog().size());
        assertEquals(SourceGrade.CURADORIA, refs.dataset("obrigacoes").grade());
        for (Obligation o : s.catalog()) {
            assertFalse(o.legalBasis().isEmpty(), o.code());
            assertTrue(o.conditionFor(Obligation.LP) != null || o.conditionFor(Obligation.LR) != null, o.code());
            DeadlineRule.parse(o.deadlineRule());
        }
    }

    // ------------------------------------------------------------------ aplicação à empresa

    @Test
    void sinteprolLucroRealInMinasGerais() throws Exception {
        List<Applicability> sep = service().evaluate(SampleCompanies.sintepro(), YearMonth.of(2026, 9));
        Applicability efdc = find(sep, "EFD_CONTRIB");
        assertEquals(Applicability.Status.OBRIGATORIA, efdc.status());
        assertEquals(LocalDate.of(2026, 11, 16), efdc.dueDate());
        assertEquals("09/2026", efdc.period());
        assertEquals(LocalDate.of(2026, 10, 30), find(sep, "DCTFWEB").dueDate());
        Applicability efd = find(sep, "EFD_ICMS_IPI");               // só a linha de MG, sem duplicar a genérica
        assertEquals("MG", efd.obligation().uf());
        assertEquals(Applicability.Status.OBRIGATORIA, efd.status());
        assertEquals(LocalDate.of(2026, 10, 15), efd.dueDate());
        Applicability ecd = find(sep, "ECD");
        assertEquals(Applicability.Status.OBRIGATORIA, ecd.status());   // Lucro Real
        assertEquals("Ano-calendário 2026", ecd.period());
        assertEquals(LocalDate.of(2027, 6, 30), ecd.dueDate());
        assertEquals(Applicability.Status.FORA_DE_VIGENCIA, find(sep, "DAPI_MG").status());
        assertEquals(Applicability.Status.FORA_DE_VIGENCIA, find(sep, "DCTF").status());
        assertTrue(find(sep, "DCTF").reason().contains("DCTFWEB"));
        assertEquals(Applicability.Status.A_CONFIRMAR, find(sep, "EFD_REINF").status());
        assertEquals(Applicability.Status.A_CONFIRMAR, find(sep, "DECL_ISS").status());
        assertTrue(find(sep, "DECL_ISS").reason().contains("201662"));
        // DAPI ainda exigida antes de 07/2026
        assertEquals(Applicability.Status.OBRIGATORIA, find(service().evaluate(SampleCompanies.sintepro(), YearMonth.of(2026, 5)), "DAPI_MG").status());
    }

    @Test
    void efdContribuicoesEndsWithPisCofinsIn2027() throws Exception {
        Company c = SampleCompanies.sintepro();
        assertEquals(Applicability.Status.OBRIGATORIA, find(service().evaluate(c, YearMonth.of(2026, 12)), "EFD_CONTRIB").status());
        assertEquals(Applicability.Status.FORA_DE_VIGENCIA, find(service().evaluate(c, YearMonth.of(2027, 1)), "EFD_CONTRIB").status());
    }

    @Test
    void lucroPresumidoEcdIsConditional() throws Exception {
        List<Applicability> l5 = service().evaluate(SampleCompanies.l5(), YearMonth.of(2026, 9));
        assertEquals(Applicability.Status.A_CONFIRMAR, find(l5, "ECD").status());
        assertEquals(Applicability.Status.OBRIGATORIA, find(l5, "ECF").status());
        assertEquals(Applicability.Status.OBRIGATORIA, find(l5, "EFD_CONTRIB").status());
    }

    @Test
    void regimeOutsideCatalogAndCompanyWithoutStateRegistration() throws Exception {
        for (Applicability a : service().evaluate(withRegime(SampleCompanies.l5(), TaxRegime.SIMPLES_NACIONAL), YearMonth.of(2026, 9))) {
            assertEquals(Applicability.Status.NAO_SE_APLICA, a.status(), a.obligation().code());
        }
        Company noIe = SampleCompanies.l5();
        noIe.stateRegistrations().clear();
        noIe.stateRegistrations().add(new StateRegistration("MG", IeSituation.NAO_CONTRIBUINTE, "", Declared.<LocalDate>none()));
        Applicability efd = find(service().evaluate(noIe, YearMonth.of(2026, 9)), "EFD_ICMS_IPI");
        assertEquals(Applicability.Status.NAO_SE_APLICA, efd.status());
    }

    @Test
    void stateWithoutOwnRowUsesGenericRule() throws Exception {
        Company sp = SampleCompanies.sintepro();
        sp.stateRegistrations().clear();
        sp.stateRegistrations().add(new StateRegistration("SP", IeSituation.INSCRITA, "123456789012", Declared.<LocalDate>none()));
        Applicability efd = find(service().evaluate(sp, YearMonth.of(2026, 9)), "EFD_ICMS_IPI");
        assertTrue(efd.obligation().isGeneric());
        assertEquals(Applicability.Status.OBRIGATORIA, efd.status());
        assertNull(efd.dueDate(), "prazo da UF não cadastrado");
    }

    @Test
    void dueDatesOfAMonth() throws Exception {
        List<Applicability> due = service().dueIn(SampleCompanies.sintepro(), YearMonth.of(2026, 10));
        java.util.Set<String> codes = new java.util.HashSet<String>();
        for (Applicability a : due) {
            codes.add(a.obligation().code() + "@" + a.period());
            assertEquals(YearMonth.of(2026, 10), YearMonth.from(a.dueDate()));
        }
        assertTrue(codes.contains("EFD_CONTRIB@08/2026"), codes.toString());
        assertTrue(codes.contains("DCTFWEB@09/2026"), codes.toString());
        assertTrue(codes.contains("EFD_ICMS_IPI@09/2026"), codes.toString());
        assertTrue(codes.contains("EFD_REINF@09/2026"), codes.toString());
        assertTrue(codes.contains("DIRBI@08/2026"), codes.toString());
        List<ObligationService.Due> generic = service().calendar(YearMonth.of(2026, 10), Obligation.LR, "MG");
        assertEquals(due.size(), generic.size());
    }
}
