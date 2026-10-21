package br.com.facilitasig.company;

import java.time.LocalDate;

/**
 * Empresa de exemplo para o início do trabalho: Empresa Fictícia de Demonstração Ltda,
 * Dados sintéticos para demonstração e testes; não representam uma empresa real.
 */
public final class SampleCompanies {

    private SampleCompanies() {
    }

    public static Company demo() {
        LocalDate opening = LocalDate.of(2021, 5, 6);
        Company c = new Company();
        c.setIdType(IdType.CNPJ);
        c.setDocument("12.345.678/0001-95");
        c.setEstablishment(EstablishmentType.MATRIZ);
        c.setLegalName("EMPRESA FICTICIA DE DEMONSTRACAO LTDA");
        c.setTradeName(Declared.<String>none());            // Exemplo sem nome fantasia
        c.setOpeningDate(opening);
        c.setSize(CompanySize.DEMAIS);
        c.setLegalNature("206-2", "Sociedade Empresária Limitada");
        c.setSituation(CadastralSituation.ATIVA);
        c.setSituationDate(opening);
        c.setSituationReason(Declared.<String>none());
        c.setSpecialSituation(Declared.<String>none());      // Sem situação especial no exemplo
        c.setSpecialSituationDate(Declared.<LocalDate>none());
        c.setStreet("RUA DE EXEMPLO");
        c.setNumber("100");
        c.setComplement(Declared.<String>none());
        c.setDistrict("BAIRRO DEMONSTRACAO");
        c.setZip("37000000");
        c.setCity("VARGINHA");
        c.setIbgeCode("3170701");                             // Código municipal usado apenas na demonstração
        c.setUf("MG");
        c.setEmail(Declared.of("contato@example.com"));
        c.setPhone(Declared.of("(00) 0000-0000"));
        c.setWebsite(Declared.<String>none());
        c.setEfr(Declared.<String>none());                    // Exemplo sem ente federativo responsável
        c.setCompanyGroup(Declared.of("SERVIÇO"));
        c.setNire(Declared.of("00000000000"));
        c.setOtherIdentifiers(Declared.<String>none());
        c.setMainCnae(new Cnae("6462000", "Holdings de instituições não-financeiras"));
        String[][] secondary = {
                {"4120400", "Construção de edifícios"},
                {"4321500", "Instalação e manutenção elétrica"},
                {"4322301", "Instalações hidráulicas, sanitárias e de gás"},
                {"4329199", "Outras obras de instalações em construções não especificadas anteriormente"},
                {"4330402", "Instalação de portas, janelas, tetos, divisórias e armários embutidos de qualquer material"},
                {"4330404", "Serviços de pintura de edifícios em geral"},
                {"4330405", "Aplicação de revestimentos e de resinas em interiores e exteriores"},
                {"4330499", "Outras obras de acabamento da construção"},
                {"4391600", "Obras de fundações"},
                {"4399101", "Administração de obras"},
                {"4399103", "Obras de alvenaria"},
                {"4399199", "Serviços especializados para construção não especificados anteriormente"},
                {"6463800", "Outras sociedades de participação, exceto holdings"}};
        for (String[] s : secondary) {
            c.secondaryCnaes().add(new Cnae(s[0], s[1]));
        }
        c.stateRegistrations().add(new StateRegistration("MG", IeSituation.INSCRITA, "0000000000000",
                Declared.<LocalDate>none()));
        c.setMunicipalRegistration(Declared.of("00000"));
        c.setMunicipalRegistrationDate(Declared.<LocalDate>none());
        c.regimePeriods().add(new RegimePeriod(TaxRegime.LUCRO_PRESUMIDO, opening,
                Declared.<LocalDate>none(), Declared.<String>none())); // início do regime na demonstração
        return c;
    }
}
