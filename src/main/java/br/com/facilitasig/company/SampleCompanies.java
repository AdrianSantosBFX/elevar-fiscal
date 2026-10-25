package br.com.facilitasig.company;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Empresas reais usadas como base de testes, cadastradas a partir do Cartão CNPJ e do e-Contínuo:
 * <ul>
 *   <li>L5 Participações Societárias Ltda (holding, Lucro Presumido);</li>
 *   <li>Sintepro Material de Construção Ltda (atacado e varejo de materiais de construção, com
 *       indústria, transporte e serviços; Lucro Real): empresa grande, boa para testes de volume e de
 *       operações variadas (ICMS, ST, IPI, transporte, ISS).</li>
 * </ul>
 * Quando o Cartão CNPJ e o e-Contínuo divergem, vale o Cartão CNPJ (fonte oficial).
 */
public final class SampleCompanies {

    private SampleCompanies() {
    }

    /** Todas as empresas de exemplo, na ordem do botão "Cadastrar exemplo". */
    public static List<Company> all() {
        return Arrays.asList(l5(), sintepro());
    }

    public static Company l5() {
        LocalDate opening = LocalDate.of(2021, 5, 6);
        Company c = new Company();
        c.setIdType(IdType.CNPJ);
        c.setDocument("41.843.649/0001-51");
        c.setEstablishment(EstablishmentType.MATRIZ);
        c.setLegalName("L5 PARTICIPACOES SOCIETARIAS LTDA");
        c.setTradeName(Declared.<String>none());            // Cartão CNPJ: ********
        c.setOpeningDate(opening);
        c.setSize(CompanySize.DEMAIS);
        c.setLegalNature("206-2", "Sociedade Empresária Limitada");
        c.setSituation(CadastralSituation.ATIVA);
        c.setSituationDate(opening);
        c.setSituationReason(Declared.<String>none());
        c.setSpecialSituation(Declared.<String>none());      // a confirmar no Cartão CNPJ
        c.setSpecialSituationDate(Declared.<LocalDate>none());
        c.setStreet("R IRMAO MARIO ESDRAS");
        c.setNumber("161");
        c.setComplement(Declared.of("APT 101"));
        c.setDistrict("VILA PINTO");
        c.setZip("37010660");
        c.setCity("VARGINHA");
        c.setIbgeCode("3170701");                             // a confirmar
        c.setUf("MG");
        c.setEmail(Declared.of("CONTATO@CONTABILITASIG.COM.BR"));
        c.setPhone(Declared.of("(35) 3221-5704"));
        c.setWebsite(Declared.<String>none());
        c.setEfr(Declared.<String>none());                    // Cartão CNPJ: *****
        c.setCompanyGroup(Declared.of("SERVIÇO"));
        c.setNire(Declared.of("31212255580"));
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
        c.stateRegistrations().add(new StateRegistration("MG", IeSituation.INSCRITA, "0044543260066",
                Declared.<LocalDate>none()));
        c.setMunicipalRegistration(Declared.of("37808"));
        c.setMunicipalRegistrationDate(Declared.<LocalDate>none());
        c.regimePeriods().add(new RegimePeriod(TaxRegime.LUCRO_PRESUMIDO, opening,
                Declared.<LocalDate>none(), Declared.<String>none())); // início = abertura (a confirmar)
        return c;
    }

    /**
     * Sintepro Material de Construção Ltda (Alpinópolis/MG), conforme Cartão CNPJ e e-Contínuo (23/10/2026).
     * Divergências resolvidas pelo Cartão CNPJ: nome fantasia "SINTEPROL" (e-Contínuo: "SINTEPROL MATERIAL DE
     * CONSTRUCAO LTDA-Matriz"); número 151 e complemento "194 E 206" (e-Contínuo junta tudo no número).
     */
    public static Company sintepro() {
        LocalDate opening = LocalDate.of(1989, 5, 29);
        Company c = new Company();
        c.setIdType(IdType.CNPJ);
        c.setDocument("25.832.809/0001-00");
        c.setEstablishment(EstablishmentType.MATRIZ);
        c.setLegalName("SINTEPROL MATERIAL DE CONSTRUCAO LTDA");
        c.setTradeName(Declared.of("SINTEPROL"));
        c.setOpeningDate(opening);
        c.setSize(CompanySize.DEMAIS);
        c.setLegalNature("206-2", "Sociedade Empresária Limitada");
        c.setSituation(CadastralSituation.ATIVA);
        c.setSituationDate(LocalDate.of(2004, 12, 18));
        c.setSituationReason(Declared.<String>none());
        c.setSpecialSituation(Declared.<String>none());      // Cartão CNPJ: ********
        c.setSpecialSituationDate(Declared.<LocalDate>none());
        c.setStreet("R RIO DE JANEIRO");
        c.setNumber("151");
        c.setComplement(Declared.of("194 E 206"));
        c.setDistrict("MUNDO NOVO");
        c.setZip("37940000");
        c.setCity("ALPINOPOLIS");
        c.setIbgeCode("3101904");                             // Alpinópolis/MG (tabela de municípios do Portal NFS-e)
        c.setUf("MG");
        c.setEmail(Declared.of("CONTATO@CONTABILITASIG.COM.BR"));
        c.setPhone(Declared.of("(35) 3221-5704"));
        c.setWebsite(Declared.<String>none());
        c.setEfr(Declared.<String>none());                    // Cartão CNPJ: *****
        c.setCompanyGroup(Declared.of("INDÚSTRIA, COMÉRCIO E SERVIÇO"));
        c.setNire(Declared.of("31203075043"));
        c.setOtherIdentifiers(Declared.<String>none());
        c.setMainCnae(new Cnae("4679699", "Comércio atacadista de materiais de construção em geral"));
        String[][] secondary = {
                {"2330301", "Fabricação de estruturas pré-moldadas de concreto armado, em série e sob encomenda"},
                {"4741500", "Comércio varejista de tintas e materiais para pintura"},
                {"4742300", "Comércio varejista de material elétrico"},
                {"4744003", "Comércio varejista de materiais hidráulicos"},
                {"4744005", "Comércio varejista de materiais de construção não especificados anteriormente"},
                {"4754701", "Comércio varejista de móveis"},
                {"4754703", "Comércio varejista de artigos de iluminação"},
                {"4930202", "Transporte rodoviário de carga, exceto produtos perigosos e mudanças, intermunicipal, interestadual e internacional"},
                {"5611203", "Lanchonetes, casas de chá, de sucos e similares"},
                {"7490104", "Atividades de intermediação e agenciamento de serviços e negócios em geral, exceto imobiliários"}};
        for (String[] s : secondary) {
            c.secondaryCnaes().add(new Cnae(s[0], s[1]));
        }
        c.stateRegistrations().add(new StateRegistration("MG", IeSituation.INSCRITA, "0194655530055",
                Declared.<LocalDate>none()));                 // 019465553.00-55 (dígitos conferidos)
        c.setMunicipalRegistration(Declared.of("201662"));
        c.setMunicipalRegistrationDate(Declared.<LocalDate>none());
        c.regimePeriods().add(new RegimePeriod(TaxRegime.LUCRO_REAL, opening,
                Declared.<LocalDate>none(), Declared.<String>none())); // início = abertura (a confirmar)
        return c;
    }
}
