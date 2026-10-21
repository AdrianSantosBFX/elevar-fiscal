package br.com.facilitasig.company;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cadastro de uma empresa (um estabelecimento = um CNPJ). Reúne os dados do Cartão CNPJ
 * e do cadastro de empresa do e-Contínuo. Todo dado opcional é {@link Declared}: ausente
 * significa "NÃO POSSUI", nunca vazio.
 */
public final class Company {

    private Long id;
    private IdType idType = IdType.CNPJ;
    private String document = "";
    private EstablishmentType establishment = EstablishmentType.MATRIZ;
    private String legalName = "";
    private Declared<String> tradeName = Declared.none();
    private LocalDate openingDate;
    private CompanySize size = CompanySize.DEMAIS;
    private String legalNatureCode = "";
    private String legalNatureDescription = "";
    private CadastralSituation situation = CadastralSituation.ATIVA;
    private LocalDate situationDate;
    private Declared<String> situationReason = Declared.none();
    private Declared<String> specialSituation = Declared.none();
    private Declared<LocalDate> specialSituationDate = Declared.none();
    private String street = "";
    private String number = "";
    private Declared<String> complement = Declared.none();
    private String district = "";
    private String zip = "";
    private String city = "";
    private String ibgeCode = "";
    private String uf = "";
    private Declared<String> email = Declared.none();
    private Declared<String> phone = Declared.none();
    private Declared<String> efr = Declared.none();
    private Declared<String> website = Declared.none();
    private Declared<String> companyGroup = Declared.none();
    private Declared<String> nire = Declared.none();
    private Declared<String> otherIdentifiers = Declared.none();
    private Cnae mainCnae;
    private final List<Cnae> secondaryCnaes = new ArrayList<Cnae>();
    private final List<StateRegistration> stateRegistrations = new ArrayList<StateRegistration>();
    private Declared<String> municipalRegistration = Declared.none();
    private Declared<LocalDate> municipalRegistrationDate = Declared.none();
    private final List<RegimePeriod> regimePeriods = new ArrayList<RegimePeriod>();

    public Long id() { return id; }
    public void setId(Long id) { this.id = id; }
    public IdType idType() { return idType; }
    public void setIdType(IdType v) { this.idType = v; }
    public String document() { return document; }
    public void setDocument(String v) { this.document = v == null ? "" : BrDocs.alphanumeric(v); }
    public EstablishmentType establishment() { return establishment; }
    public void setEstablishment(EstablishmentType v) { this.establishment = v; }
    public String legalName() { return legalName; }
    public void setLegalName(String v) { this.legalName = v == null ? "" : v.trim().toUpperCase(); }
    public Declared<String> tradeName() { return tradeName; }
    public void setTradeName(Declared<String> v) { this.tradeName = v; }
    public LocalDate openingDate() { return openingDate; }
    public void setOpeningDate(LocalDate v) { this.openingDate = v; }
    public CompanySize size() { return size; }
    public void setSize(CompanySize v) { this.size = v; }
    public String legalNatureCode() { return legalNatureCode; }
    public String legalNatureDescription() { return legalNatureDescription; }
    public void setLegalNature(String code, String description) {
        this.legalNatureCode = code == null ? "" : code.trim();
        this.legalNatureDescription = description == null ? "" : description.trim();
    }
    public CadastralSituation situation() { return situation; }
    public void setSituation(CadastralSituation v) { this.situation = v; }
    public LocalDate situationDate() { return situationDate; }
    public void setSituationDate(LocalDate v) { this.situationDate = v; }
    public Declared<String> situationReason() { return situationReason; }
    public void setSituationReason(Declared<String> v) { this.situationReason = v; }
    public Declared<String> specialSituation() { return specialSituation; }
    public void setSpecialSituation(Declared<String> v) { this.specialSituation = v; }
    public Declared<LocalDate> specialSituationDate() { return specialSituationDate; }
    public void setSpecialSituationDate(Declared<LocalDate> v) { this.specialSituationDate = v; }
    public String street() { return street; }
    public void setStreet(String v) { this.street = v == null ? "" : v.trim().toUpperCase(); }
    public String number() { return number; }
    public void setNumber(String v) { this.number = v == null ? "" : v.trim().toUpperCase(); }
    public Declared<String> complement() { return complement; }
    public void setComplement(Declared<String> v) { this.complement = v; }
    public String district() { return district; }
    public void setDistrict(String v) { this.district = v == null ? "" : v.trim().toUpperCase(); }
    public String zip() { return zip; }
    public void setZip(String v) { this.zip = BrDocs.digits(v); }
    public String city() { return city; }
    public void setCity(String v) { this.city = v == null ? "" : v.trim().toUpperCase(); }
    public String ibgeCode() { return ibgeCode; }
    public void setIbgeCode(String v) { this.ibgeCode = BrDocs.digits(v); }
    public String uf() { return uf; }
    public void setUf(String v) { this.uf = v == null ? "" : v.trim().toUpperCase(); }
    public Declared<String> email() { return email; }
    public void setEmail(Declared<String> v) { this.email = v; }
    public Declared<String> phone() { return phone; }
    public void setPhone(Declared<String> v) { this.phone = v; }
    public Declared<String> efr() { return efr; }
    public void setEfr(Declared<String> v) { this.efr = v; }
    public Declared<String> website() { return website; }
    public void setWebsite(Declared<String> v) { this.website = v; }
    /** Grupo de empresas (classificação interna, ex.: Serviço). */
    public Declared<String> companyGroup() { return companyGroup; }
    public void setCompanyGroup(Declared<String> v) { this.companyGroup = v; }
    /** Número de Identificação do Registro de Empresas (Junta Comercial). */
    public Declared<String> nire() { return nire; }
    public void setNire(Declared<String> v) { this.nire = v; }
    /** Outros identificadores (ex.: CPF/CEI). */
    public Declared<String> otherIdentifiers() { return otherIdentifiers; }
    public void setOtherIdentifiers(Declared<String> v) { this.otherIdentifiers = v; }
    public Cnae mainCnae() { return mainCnae; }
    public void setMainCnae(Cnae v) { this.mainCnae = v; }
    /** CNAEs secundários; lista vazia = "NÃO POSSUI". */
    public List<Cnae> secondaryCnaes() { return secondaryCnaes; }
    public List<StateRegistration> stateRegistrations() { return stateRegistrations; }
    public Declared<String> municipalRegistration() { return municipalRegistration; }
    public void setMunicipalRegistration(Declared<String> v) { this.municipalRegistration = v; }
    public Declared<LocalDate> municipalRegistrationDate() { return municipalRegistrationDate; }
    public void setMunicipalRegistrationDate(Declared<LocalDate> v) { this.municipalRegistrationDate = v; }
    public List<RegimePeriod> regimePeriods() { return regimePeriods; }

    /** Raiz do CNPJ (8 primeiros caracteres) ou "" quando o identificador não é CNPJ. */
    public String cnpjRoot() {
        return idType == IdType.CNPJ ? BrDocs.cnpjRoot(document) : "";
    }

    public String documentFormatted() {
        return BrDocs.formatDocument(idType, document);
    }

    /** Regime vigente na data, se houver. */
    public RegimePeriod regimeOn(LocalDate date) {
        for (RegimePeriod p : regimePeriods) {
            if (p.covers(date)) {
                return p;
            }
        }
        return null;
    }

    public String displayName() {
        return documentFormatted() + " - " + legalName;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
