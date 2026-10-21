package br.com.facilitasig.company;

import br.com.facilitasig.db.Database;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CompanyRepositoryTest {

    private CompanyRepository newRepo() throws Exception {
        File dir = Files.createTempDirectory("fsig").toFile();
        return new CompanyRepository(new Database(new File(dir, "t.db")));
    }

    @Test
    void cnpjAndCpfValidation() {
        assertTrue(BrDocs.isValidCnpj("12.345.678/0001-95"));
        assertFalse(BrDocs.isValidCnpj("12.345.678/0001-96"));
        assertTrue(BrDocs.isValidCnpj("12ABC34501DE35"));
        assertTrue(BrDocs.isValidCpf("529.982.247-25"));
        assertFalse(BrDocs.isValidCpf("111.111.111-11"));
    }

    @Test
    void declaredTextTreatsEmptyAndMarkerAsNone() {
        assertFalse(Declared.ofText("").isPresent());
        assertFalse(Declared.ofText("não possui").isPresent());
        assertFalse(Declared.ofText(" NAO POSSUI ").isPresent());
        assertEquals("X", Declared.ofText(" X ").get());
        assertEquals("NÃO POSSUI", Declared.<String>none().display());
    }

    @Test
    void demoSampleIsValidAndRoundTrips() throws Exception {
        CompanyRepository repo = newRepo();
        Company demo = SampleCompanies.demo();
        assertTrue(CompanyValidator.validate(demo).isEmpty());
        repo.save(demo);
        Company r = repo.findById(demo.id());
        assertEquals("12345678000195", r.document());
        assertEquals("12345678", r.cnpjRoot());
        assertFalse(r.tradeName().isPresent());
        assertFalse(r.efr().isPresent());
        assertEquals("BAIRRO DEMONSTRACAO", r.district());
        assertEquals(13, r.secondaryCnaes().size());
        assertEquals(IeSituation.INSCRITA, r.stateRegistrations().get(0).situation());
        assertEquals(TaxRegime.LUCRO_PRESUMIDO, r.regimeOn(LocalDate.of(2026, 10, 21)).regime());
    }

    @Test
    void noneIsPersistedExplicitlyNeverEmpty() throws Exception {
        CompanyRepository repo = newRepo();
        Company c = SampleCompanies.demo();
        c.secondaryCnaes().clear();
        repo.save(c);
        Company r = repo.findById(c.id());
        assertTrue(r.secondaryCnaes().isEmpty());
        assertEquals("NÃO POSSUI", r.website().display());
    }

    @Test
    void duplicateDocumentIsRejected() throws Exception {
        CompanyRepository repo = newRepo();
        repo.save(SampleCompanies.demo());
        assertThrows(IllegalArgumentException.class, () -> repo.save(SampleCompanies.demo()));
    }

    @Test
    void invalidCompanyIsNotSaved() throws Exception {
        CompanyRepository repo = newRepo();
        Company c = SampleCompanies.demo();
        c.setDocument("12345678000196");
        assertThrows(IllegalArgumentException.class, () -> repo.save(c));
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void onlyOneOpenRegimeAllowed() {
        Company c = SampleCompanies.demo();
        c.regimePeriods().add(new RegimePeriod(TaxRegime.LUCRO_REAL, LocalDate.of(2027, 1, 1),
                Declared.<LocalDate>none(), Declared.<String>none()));
        assertFalse(CompanyValidator.validate(c).isEmpty());
    }

    @Test
    void settingsRoundTrip() throws Exception {
        CompanyRepository repo = newRepo();
        assertNull(repo.setting("k"));
        repo.putSetting("k", "v");
        assertEquals("v", repo.setting("k"));
    }
}
