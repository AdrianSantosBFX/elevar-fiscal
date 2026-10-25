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
        assertTrue(BrDocs.isValidCnpj("41.843.649/0001-51"));
        assertFalse(BrDocs.isValidCnpj("41.843.649/0001-52"));
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
    void l5SampleIsValidAndRoundTrips() throws Exception {
        CompanyRepository repo = newRepo();
        Company l5 = SampleCompanies.l5();
        assertTrue(CompanyValidator.validate(l5).isEmpty());
        repo.save(l5);
        Company r = repo.findById(l5.id());
        assertEquals("41843649000151", r.document());
        assertEquals("41843649", r.cnpjRoot());
        assertFalse(r.tradeName().isPresent());
        assertFalse(r.efr().isPresent());
        assertEquals("VILA PINTO", r.district());
        assertEquals(13, r.secondaryCnaes().size());
        assertEquals(IeSituation.INSCRITA, r.stateRegistrations().get(0).situation());
        assertEquals(TaxRegime.LUCRO_PRESUMIDO, r.regimeOn(LocalDate.of(2026, 10, 21)).regime());
    }

    @Test
    void sinteproSampleIsValidAndRoundTrips() throws Exception {
        CompanyRepository repo = newRepo();
        Company s = SampleCompanies.sintepro();
        assertTrue(CompanyValidator.validate(s).isEmpty(), CompanyValidator.validate(s).toString());
        repo.save(s);
        Company r = repo.findById(s.id());
        assertEquals("25832809000100", r.document());
        assertEquals("25832809", r.cnpjRoot());
        assertEquals(EstablishmentType.MATRIZ, r.establishment());
        assertEquals("SINTEPROL", r.tradeName().get());
        assertEquals("151", r.number());
        assertEquals("194 E 206", r.complement().get());
        assertEquals("3101904", r.ibgeCode());
        assertEquals("4679699", r.mainCnae().code());
        assertEquals(10, r.secondaryCnaes().size());
        assertEquals("0194655530055", r.stateRegistrations().get(0).number());
        assertEquals("201662", r.municipalRegistration().get());
        assertEquals("31203075043", r.nire().get());
        assertEquals(TaxRegime.LUCRO_REAL, r.regimeOn(LocalDate.of(2026, 10, 21)).regime());
    }

    @Test
    void allSamplesAreDistinctAndCanBeRegisteredTogether() throws Exception {
        CompanyRepository repo = newRepo();
        for (Company c : SampleCompanies.all()) {
            repo.save(c);
        }
        assertEquals(2, repo.findAll().size());
        assertNotNull(repo.findByDocument(IdType.CNPJ, "25832809000100"));
        assertNotNull(repo.findByDocument(IdType.CNPJ, "41843649000151"));
    }

    @Test
    void sinteproIsSeededOnceAndStaysDeletedIfUserRemovesIt() throws Exception {
        CompanyRepository repo = newRepo();
        assertEquals(1, CompanySeeds.apply(repo).size());
        Company s = repo.findByDocument(IdType.CNPJ, "25832809000100");
        assertNotNull(s);
        assertEquals(0, CompanySeeds.apply(repo).size(), "não cadastra de novo");
        assertEquals(1, repo.findAll().size());
        repo.delete(s.id());
        assertEquals(0, CompanySeeds.apply(repo).size(), "excluída pelo usuário: não volta");
        assertNull(repo.findByDocument(IdType.CNPJ, "25832809000100"));
    }

    @Test
    void seedDoesNotDuplicateACompanyAlreadyRegistered() throws Exception {
        CompanyRepository repo = newRepo();
        repo.save(SampleCompanies.sintepro());
        assertEquals(0, CompanySeeds.apply(repo).size());
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void noneIsPersistedExplicitlyNeverEmpty() throws Exception {
        CompanyRepository repo = newRepo();
        Company c = SampleCompanies.l5();
        c.secondaryCnaes().clear();
        repo.save(c);
        Company r = repo.findById(c.id());
        assertTrue(r.secondaryCnaes().isEmpty());
        assertEquals("NÃO POSSUI", r.website().display());
    }

    @Test
    void duplicateDocumentIsRejected() throws Exception {
        CompanyRepository repo = newRepo();
        repo.save(SampleCompanies.l5());
        assertThrows(IllegalArgumentException.class, () -> repo.save(SampleCompanies.l5()));
    }

    @Test
    void invalidCompanyIsNotSaved() throws Exception {
        CompanyRepository repo = newRepo();
        Company c = SampleCompanies.l5();
        c.setDocument("41843649000152");
        assertThrows(IllegalArgumentException.class, () -> repo.save(c));
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void onlyOneOpenRegimeAllowed() {
        Company c = SampleCompanies.l5();
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
