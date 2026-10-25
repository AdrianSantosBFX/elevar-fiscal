package br.com.facilitasig.fiscal;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.SampleCompanies;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static br.com.facilitasig.fiscal.XmlFixtures.Item;
import static br.com.facilitasig.fiscal.XmlFixtures.L5;
import static br.com.facilitasig.fiscal.XmlFixtures.Nfe;
import static br.com.facilitasig.fiscal.XmlFixtures.Nfse;
import static br.com.facilitasig.fiscal.XmlFixtures.OTHER;
import static br.com.facilitasig.fiscal.XmlFixtures.icms00;
import static org.junit.jupiter.api.Assertions.*;

/** Cada teste parte de uma nota coerente (sem achados) e provoca um erro do catálogo. */
class XmlChecksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private final Company l5 = SampleCompanies.l5();

    // ------------------------------------------------------------------ apoio

    private List<Finding> run(String signedXml, Direction dir, List<FiscalEvent> events, String cstOverride) {
        try {
            Element root = FiscalService.parseXml(signedXml.getBytes(StandardCharsets.UTF_8));
            FiscalDoc d = "NFSe".equals(X.name(root)) ? NfseNacionalParser.parse(root) : NfeParser.parse(root);
            d.setDirection(dir);
            d.setXml(signedXml);
            if (Fields.NA.equals(d.original("entry_exit_date"))) {
                d.put("entry_exit_date", d.original("issued_at").substring(0, 10));
            }
            if (cstOverride != null) {
                d.overrides().put(FiscalDoc.itemKey(0, "cst"), cstOverride);
            }
            List<Finding> out = DocValidator.validate(d, l5, TODAY, events);
            for (Finding f : out) {
                assertTrue(RuleCatalog.contains(f.ruleId()), "regra fora do catálogo: " + f.ruleId());
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Finding> saida(Nfe n) {
        return run(TestSigner.sign(n.xml()), Direction.SAIDA, Collections.<FiscalEvent>emptyList(), null);
    }

    private List<Finding> entrada(Nfse n) {
        return run(TestSigner.sign(n.xml()), Direction.ENTRADA, Collections.<FiscalEvent>emptyList(), null);
    }

    private static boolean has(List<Finding> fs, String rule) {
        for (Finding f : fs) {
            if (f.ruleId().equals(rule)) {
                return true;
            }
        }
        return false;
    }

    private static Finding get(List<Finding> fs, String rule) {
        for (Finding f : fs) {
            if (f.ruleId().equals(rule)) {
                return f;
            }
        }
        fail("esperava " + rule + " em " + fs);
        return null;
    }

    private static void assertOnly(List<Finding> fs, String... rules) {
        List<String> got = new ArrayList<String>();
        for (Finding f : fs) {
            if (!got.contains(f.ruleId())) {
                got.add(f.ruleId());
            }
        }
        List<String> want = new ArrayList<String>(java.util.Arrays.asList(rules));
        Collections.sort(got);
        Collections.sort(want);
        assertEquals(want, got, "achados: " + fs);
    }

    private static FiscalEvent event(String key, String type, int seq, String text) {
        FiscalEvent ev = new FiscalEvent();
        ev.setAccessKey(key);
        ev.setType(type);
        ev.setSeq(seq);
        ev.setEventAt("2026-07-11T09:00:00-03:00");
        ev.setProtocolStatus("135");
        ev.setDescription(text);
        return ev;
    }

    // ------------------------------------------------------------------ notas de referência

    @Test
    void coherentNfeHasNoFindings() {
        assertOnly(saida(new Nfe()));
    }

    @Test
    void coherentNfseHasNoFindings() {
        assertOnly(entrada(new Nfse()));
    }

    // ------------------------------------------------------------------ grupo 1

    @Test
    void unsignedXmlIsAlertAndTamperedIsError() {
        List<Finding> unsigned = run(new Nfe().xml(), Direction.SAIDA, Collections.<FiscalEvent>emptyList(), null);
        assertEquals(Severity.ALERTA, get(unsigned, "ERR-1.6").severity());
        String tampered = TestSigner.sign(new Nfe().xml()).replace("Produto 1", "Produto X");
        Finding f = get(run(tampered, Direction.SAIDA, Collections.<FiscalEvent>emptyList(), null), "ERR-1.6");
        assertEquals(Severity.ERRO, f.severity());
        assertTrue(f.message().contains("alterado"));
    }

    @Test
    void cancellationEventIsError() {
        Nfe n = new Nfe();
        List<FiscalEvent> evs = Collections.singletonList(event(n.key(), FiscalEvent.CANCELAMENTO, 1, "Erro de digitação"));
        List<Finding> fs = run(TestSigner.sign(n.xml()), Direction.SAIDA, evs, null);
        assertEquals(Severity.ERRO, get(fs, "ERR-1.1").severity());
        assertEquals(DocStatus.ERRO, DocValidator.statusOf(fs));
    }

    @Test
    void correctionLetterOnValueIsAlertButOnAddressIsNot() {
        Nfe n = new Nfe();
        List<FiscalEvent> bad = Collections.singletonList(event(n.key(), FiscalEvent.CARTA_CORRECAO, 1, "Corrige o VALOR unitário e a alíquota do item 1"));
        assertTrue(has(run(TestSigner.sign(n.xml()), Direction.SAIDA, bad, null), "ERR-1.10"));
        List<FiscalEvent> ok = new ArrayList<FiscalEvent>();
        ok.add(event(n.key(), FiscalEvent.CARTA_CORRECAO, 1, "Corrige o valor do item"));
        ok.add(event(n.key(), FiscalEvent.CARTA_CORRECAO, 2, "Corrige o endereço de entrega")); // a última substitui as anteriores
        assertFalse(has(run(TestSigner.sign(n.xml()), Direction.SAIDA, ok, null), "ERR-1.10"));
    }

    // ------------------------------------------------------------------ grupos 2 a 4

    @Test
    void foreignRecipientNeedsIdDest3AndCfop7() {
        Nfe n = new Nfe();
        n.dest = "<idEstrangeiro>A123</idEstrangeiro>";
        n.destUf = "EX";
        n.destMun = "9999999";
        n.destCPais = "1600";
        n.indIEDest = "9";
        n.destIE = null;
        List<Finding> fs = saida(n);
        assertTrue(has(fs, "ERR-2.6"));
        assertFalse(has(fs, "IMP-015"), "exterior não tem UF/município brasileiros");
        n.idDest = "3";
        n.item().cfop = "7101";
        n.item().icms = "<ICMS40><orig>0</orig><CST>41</CST></ICMS40>";
        n.item().tBC = "0.00";
        n.item().tICMS = "0.00";
        assertOnly(saida(n));
    }

    @Test
    void idDestMustMatchUfsExceptPresentialSale() {
        Nfe n = new Nfe();
        n.destUf = "SP";
        n.destMun = "3550308";
        List<Finding> fs = saida(n);
        assertTrue(has(fs, "ERR-4.2"));
        assertTrue(has(fs, "IMP-016"));
        n.indPres = "1";
        n.indFinal = "1";
        n.indIEDest = "9";
        n.destIE = null;
        fs = saida(n);
        assertFalse(has(fs, "ERR-4.2"));
        assertFalse(has(fs, "IMP-016"));
    }

    @Test
    void devolutionNeedsReferenceAndPurpose() {
        Nfe n = new Nfe();
        n.finNFe = "4";
        n.item().cfop = "5202";
        assertTrue(get(saida(n), "ERR-4.5").message().contains("NFref"));
        n.nfRef = "<NFref><refNFe>" + new Nfe().key() + "</refNFe></NFref>";
        assertOnly(saida(n));
        n.finNFe = "1";
        assertTrue(has(saida(n), "ERR-4.5"));
    }

    @Test
    void complementaryNoteNeedsReferenceAndIsNotAFalseTotalError() {
        Nfe n = new Nfe();
        n.finNFe = "2";
        n.totalOverride.put("vNF", "0.00");
        List<Finding> fs = saida(n);
        assertTrue(has(fs, "ERR-4.7"));
        assertFalse(has(fs, "IMP-008"), "complementar não segue a fórmula do vNF");
    }

    @Test
    void serviceItemInNfeNeedsCfop933AndCorrectIss() {
        Nfe n = new Nfe();
        n.item().extraImposto = "<ISSQN><vBC>100.00</vBC><vAliq>5.00</vAliq><vISSQN>4.00</vISSQN><cMunFG>3170701</cMunFG>"
                + "<cListServ>17.01</cListServ></ISSQN>";
        List<Finding> fs = saida(n);
        assertTrue(has(fs, "ERR-4.12"));
        assertTrue(has(fs, "ERR-12.1"));
    }

    @Test
    void cfopFollowsTpNfEvenForThirdPartyEntryNote() {
        Nfe n = new Nfe();
        n.emitDoc = OTHER;
        n.dest = "<CNPJ>" + L5 + "</CNPJ>";
        n.tpNF = "0";
        n.item().cfop = "1202";
        n.finNFe = "4";
        n.nfRef = "<NFref><refNFe>" + new Nfe().key() + "</refNFe></NFref>";
        List<Finding> fs = run(TestSigner.sign(n.xml()), Direction.ENTRADA, Collections.<FiscalEvent>emptyList(), null);
        assertFalse(has(fs, "IMP-009"), "tpNF = 0 usa CFOP 1/2/3: " + fs);
    }

    // ------------------------------------------------------------------ grupo 5

    @Test
    void itemValueMustBeQuantityTimesUnitPrice() {
        Nfe n = new Nfe();
        n.item().vProd = "101.00";
        n.item().tBC = "101.00";
        n.item().tICMS = "18.18";
        n.item().icms = icms00("101.00", "18.00", "18.18");
        assertTrue(has(saida(n), "ERR-5.1"));
    }

    @Test
    void totalsMustMatchItems() {
        Nfe n = new Nfe();
        n.totalOverride.put("vICMS", "10.00");
        assertTrue(get(saida(n), "ERR-5.2").message().contains("vICMS"));
    }

    @Test
    void itemOutOfTotalMustNotBeSummed() {
        Nfe n = new Nfe();
        Item extra = new Item();
        extra.indTot = "0";
        n.items.add(extra);
        assertOnly(saida(n));
        n.totalOverride.put("vProd", "200.00");
        n.totalOverride.put("vNF", "200.00");
        List<Finding> fs = saida(n);
        assertTrue(has(fs, "ERR-5.4"));
        assertFalse(has(fs, "IMP-007"));
    }

    @Test
    void discountLargerThanItemIsAlert() {
        Nfe n = new Nfe();
        n.item().vDesc = "150.00";
        assertTrue(has(saida(n), "ERR-5.5"));
    }

    @Test
    void freightWithNoFreightModeIsAlert() {
        Nfe n = new Nfe();
        n.item().vFrete = "10.00";
        n.item().icms = icms00("110.00", "18.00", "19.80");
        n.item().tBC = "110.00";
        n.item().tICMS = "19.80";
        assertOnly(saida(n), "ERR-5.7");
        n.transp = "<transp><modFrete>0</modFrete></transp>";
        assertOnly(saida(n));
    }

    @Test
    void paymentsAndInstallmentsMustMatch() {
        Nfe n = new Nfe();
        n.pag = "<pag><detPag><tPag>01</tPag><vPag>50.00</vPag></detPag></pag>";
        assertTrue(has(saida(n), "ERR-5.8"));
        n.pag = "<pag><detPag><tPag>15</tPag><vPag>100.00</vPag></detPag></pag>";
        n.cobr = "<cobr><fat><nFat>1</nFat><vOrig>100.00</vOrig><vLiq>100.00</vLiq></fat><dup><nDup>001</nDup><vDup>60.00</vDup></dup></cobr>";
        assertTrue(get(saida(n), "ERR-5.8").message().contains("duplicatas"));
    }

    // ------------------------------------------------------------------ grupos 6 e 7

    @Test
    void icmsMustBeBaseTimesRate() {
        Nfe n = new Nfe();
        n.item().icms = icms00("100.00", "18.00", "17.00");
        n.item().tICMS = "17.00";
        assertOnly(saida(n), "ERR-6.1");
    }

    @Test
    void icmsBaseMustIncludeFreight() {
        Nfe n = new Nfe();
        n.transp = "<transp><modFrete>0</modFrete></transp>";
        n.item().vFrete = "10.00";
        assertOnly(saida(n), "ERR-6.2");
    }

    @Test
    void ipiOutOfIcmsBaseInSaleToFinalConsumer() {
        Nfe n = new Nfe();
        n.indFinal = "1";
        n.item().ipi = "<IPI><cEnq>999</cEnq><IPITrib><CST>50</CST><vBC>100.00</vBC><pIPI>10.00</pIPI><vIPI>10.00</vIPI></IPITrib></IPI>";
        n.item().tIPI = "10.00";
        assertTrue(get(saida(n), "ERR-6.2").message().contains("consumidor final"));
        n.item().icms = icms00("110.00", "18.00", "19.80");
        n.item().tBC = "110.00";
        n.item().tICMS = "19.80";
        assertOnly(saida(n));
    }

    @Test
    void interstateRateDependsOnOriginAndUfs() {
        Nfe n = new Nfe();
        n.idDest = "2";
        n.destUf = "BA";
        n.destMun = "2927408";
        n.item().cfop = "6102";
        n.item().icms = icms00("100.00", "12.00", "12.00");
        n.item().tICMS = "12.00";
        assertTrue(get(saida(n), "ERR-6.3").message().contains("esperada 7%"));
        n.item().icms = icms00("100.00", "7.00", "7.00");
        n.item().tICMS = "7.00";
        assertOnly(saida(n));
        n.item().icms = "<ICMS00><orig>1</orig><CST>00</CST><modBC>3</modBC><vBC>100.00</vBC><pICMS>7.00</pICMS><vICMS>7.00</vICMS></ICMS00>";
        assertTrue(get(saida(n), "ERR-6.3").message().contains("esperada 4%"));
    }

    @Test
    void cstMustMatchStCfop() {
        Nfe n = new Nfe();
        n.item().cfop = "5405";
        assertTrue(has(saida(n), "ERR-6.6"));
        Nfe m = new Nfe();
        m.item().icms = "<ICMS60><orig>0</orig><CST>60</CST><vBCSTRet>100.00</vBCSTRet><pST>18.00</pST><vICMSSTRet>18.00</vICMSSTRet></ICMS60>";
        m.item().tBC = "0.00";
        m.item().tICMS = "0.00";
        assertOnly(saida(m), "ERR-6.6");
        m.item().cfop = "5405";
        assertOnly(saida(m));
    }

    @Test
    void taxedCstWithoutTaxIsAlert() {
        Nfe n = new Nfe();
        n.item().icms = icms00("100.00", "0.00", "0.00");
        n.item().tICMS = "0.00";
        assertOnly(saida(n), "ERR-6.7");
    }

    @Test
    void crtMustMatchCstOrCsosn() {
        Nfe n = new Nfe();
        n.crt = "1";
        assertTrue(has(saida(n), "ERR-6.8"));
    }

    @Test
    void deferralValuesMustAddUp() {
        Nfe n = new Nfe();
        n.item().icms = "<ICMS51><orig>0</orig><CST>51</CST><modBC>3</modBC><vBC>100.00</vBC><pICMS>18.00</pICMS><vICMSOp>18.00</vICMSOp>"
                + "<pDif>100.00</pDif><vICMSDif>18.00</vICMSDif><vICMS>5.00</vICMS></ICMS51>";
        n.item().tICMS = "5.00";
        assertOnly(saida(n), "ERR-6.10");
    }

    @Test
    void exportWithIcmsIsAlert() {
        Nfe n = new Nfe();
        n.dest = "<idEstrangeiro>A1</idEstrangeiro>";
        n.destUf = "EX";
        n.destMun = "9999999";
        n.destCPais = "1600";
        n.indIEDest = "9";
        n.destIE = null;
        n.idDest = "3";
        n.item().cfop = "7101";
        assertOnly(saida(n), "ERR-6.12");
    }

    @Test
    void cstOverrideIncompatibleWithXmlGroup() {
        List<Finding> fs = run(TestSigner.sign(new Nfe().xml()), Direction.SAIDA, Collections.<FiscalEvent>emptyList(), "40");
        assertTrue(has(fs, "ERR-7.7"));
        fs = run(TestSigner.sign(new Nfe().xml()), Direction.SAIDA, Collections.<FiscalEvent>emptyList(), "00");
        assertFalse(has(fs, "ERR-7.7"));
    }

    // ------------------------------------------------------------------ grupos 8 e 9

    @Test
    void stMustBeStBaseTimesRateMinusOwnIcms() {
        Nfe n = new Nfe();
        n.item().cfop = "5401";
        n.item().icms = "<ICMS10><orig>0</orig><CST>10</CST><modBC>3</modBC><vBC>100.00</vBC><pICMS>18.00</pICMS><vICMS>18.00</vICMS>"
                + "<modBCST>4</modBCST><vBCST>140.00</vBCST><pICMSST>18.00</pICMSST><vICMSST>7.20</vICMSST></ICMS10>";
        n.item().tBCST = "140.00";
        n.item().tST = "7.20";
        assertOnly(saida(n));
        n.item().icms = n.item().icms.replace("<vICMSST>7.20<", "<vICMSST>25.20<");
        n.item().tST = "25.20";
        assertOnly(saida(n), "ERR-8.4");
    }

    @Test
    void cst60WithoutRetainedStDataIsAlert() {
        Nfe n = new Nfe();
        n.item().cfop = "5405";
        n.item().icms = "<ICMS60><orig>0</orig><CST>60</CST></ICMS60>";
        n.item().tBC = "0.00";
        n.item().tICMS = "0.00";
        assertOnly(saida(n), "ERR-8.7");
    }

    private Nfe interstateToConsumer() {
        Nfe n = new Nfe();
        n.idDest = "2";
        n.destUf = "BA";
        n.destMun = "2927408";
        n.indFinal = "1";
        n.indIEDest = "9";
        n.destIE = null;
        n.dest = "<CPF>52998224725</CPF>";
        n.item().cfop = "6108";
        n.item().icms = icms00("100.00", "7.00", "7.00");
        n.item().tICMS = "7.00";
        return n;
    }

    @Test
    void interstateSaleToNonContributorNeedsDifal() {
        Nfe n = interstateToConsumer();
        assertOnly(saida(n), "ERR-9.1");
        n.item().extraImposto = "<ICMSUFDest><vBCUFDest>100.00</vBCUFDest><vBCFCPUFDest>100.00</vBCFCPUFDest><pFCPUFDest>2.00</pFCPUFDest>"
                + "<pICMSUFDest>20.50</pICMSUFDest><pICMSInter>7.00</pICMSInter><pICMSInterPart>100.00</pICMSInterPart>"
                + "<vFCPUFDest>2.00</vFCPUFDest><vICMSUFDest>13.50</vICMSUFDest><vICMSUFRemet>0.00</vICMSUFRemet></ICMSUFDest>";
        assertOnly(saida(n));
        n.item().extraImposto = n.item().extraImposto.replace("<pICMSInterPart>100.00", "<pICMSInterPart>80.00");
        assertTrue(get(saida(n), "ERR-9.3").message().contains("partilha"));
    }

    @Test
    void recipientIndicatorsMustBeCoherent() {
        Nfe n = new Nfe();
        n.destIE = null;
        assertOnly(saida(n), "ERR-9.2");
        Nfe m = new Nfe();
        m.indIEDest = "9";
        m.destIE = null;
        assertTrue(get(saida(m), "ERR-9.2").message().contains("consumidor final"));
    }

    // ------------------------------------------------------------------ grupos 10 e 11

    @Test
    void ipiCstAndArithmetic() {
        Nfe n = new Nfe();
        n.item().ipi = "<IPI><cEnq>999</cEnq><IPITrib><CST>00</CST><vBC>100.00</vBC><pIPI>10.00</pIPI><vIPI>9.00</vIPI></IPITrib></IPI>";
        n.item().tIPI = "9.00";
        List<Finding> fs = saida(n);
        assertTrue(has(fs, "ERR-10.2"));
        assertTrue(has(fs, "ERR-10.3"));
    }

    @Test
    void presumidoCompanyMustNotUseNonCumulativeRates() {
        Nfe n = new Nfe();
        n.item().pis = "<PIS><PISAliq><CST>01</CST><vBC>100.00</vBC><pPIS>1.65</pPIS><vPIS>1.65</vPIS></PISAliq></PIS>";
        n.item().tPIS = "1.65";
        assertOnly(saida(n), "ERR-11.1");
        n.item().pis = "<PIS><PISAliq><CST>01</CST><vBC>100.00</vBC><pPIS>0.65</pPIS><vPIS>0.60</vPIS></PISAliq></PIS>";
        n.item().tPIS = "0.60";
        assertOnly(saida(n), "ERR-11.5");
        n.item().pis = "<PIS><PISAliq><CST>01</CST><vBC>110.00</vBC><pPIS>0.65</pPIS><vPIS>0.72</vPIS></PISAliq></PIS>";
        n.item().tPIS = "0.72";
        assertTrue(get(saida(n), "ERR-11.5").message().contains("maior que o valor da operação"));
    }

    @Test
    void exportWithPisIsAlert() {
        Nfe n = new Nfe();
        n.dest = "<idEstrangeiro>A1</idEstrangeiro>";
        n.destUf = "EX";
        n.destMun = "9999999";
        n.destCPais = "1600";
        n.indIEDest = "9";
        n.destIE = null;
        n.idDest = "3";
        n.item().cfop = "7101";
        n.item().icms = "<ICMS40><orig>0</orig><CST>41</CST></ICMS40>";
        n.item().tBC = "0.00";
        n.item().tICMS = "0.00";
        n.item().pis = "<PIS><PISAliq><CST>01</CST><vBC>100.00</vBC><pPIS>0.65</pPIS><vPIS>0.65</vPIS></PISAliq></PIS>";
        n.item().tPIS = "0.65";
        assertOnly(saida(n), "ERR-11.6");
    }

    // ------------------------------------------------------------------ grupos 3, 13, 16 e 17

    @Test
    void outgoingNoteWithExitInAnotherMonth() {
        Nfe n = new Nfe();
        n.dhSaiEnt = "2026-08-01T08:00:00-03:00";
        assertOnly(saida(n), "ERR-3.3");
    }

    @Test
    void ownEntryFromIndividualSuggestsRuralProducer() {
        Nfe n = new Nfe();
        n.tpNF = "0";
        n.dest = "<CPF>52998224725</CPF>";
        n.indIEDest = "9";
        n.destIE = null;
        n.item().cfop = "1102";
        List<Finding> fs = run(TestSigner.sign(n.xml()), Direction.ENTRADA, Collections.<FiscalEvent>emptyList(), null);
        assertTrue(has(fs, "ERR-13.10"));
    }

    @Test
    void unitsDifferentWithOneToOneConversion() {
        Nfe n = new Nfe();
        n.item().uTrib = "CX";
        assertOnly(saida(n), "ERR-16.2");
        n.item().uTrib = "UND"; // mesma unidade, outra grafia
        assertOnly(saida(n));
    }

    @Test
    void processCitedInAdditionalInfo() {
        Nfe n = new Nfe();
        n.infAdic = "<infAdic><infCpl>ICMS suspenso por liminar em mandado de segurança</infCpl></infAdic>";
        assertOnly(saida(n), "ERR-16.5");
    }

    @Test
    void reformGroupMandatoryAndArithmetic() {
        Nfe n = new Nfe();
        n.dhEmi = "2026-08-10T10:00:00-03:00";
        assertOnly(saida(n), "ERR-17.1");
        n.item().extraImposto = "<IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib><gIBSCBS><vBC>100.00</vBC>"
                + "<gIBSUF><pIBSUF>0.10</pIBSUF><vIBSUF>0.10</vIBSUF></gIBSUF><gIBSMun><pIBSMun>0.00</pIBSMun><vIBSMun>0.00</vIBSMun></gIBSMun>"
                + "<vIBS>0.10</vIBS><gCBS><pCBS>0.90</pCBS><vCBS>0.90</vCBS></gCBS></gIBSCBS></IBSCBS>";
        assertOnly(saida(n));
        n.item().extraImposto = n.item().extraImposto.replace("<vCBS>0.90<", "<vCBS>9.00<").replace("<pCBS>0.90<", "<pCBS>9.00<");
        List<Finding> fs = saida(n);
        assertOnly(fs, "ERR-17.3");
        assertTrue(get(fs, "ERR-17.3").message().contains("2026"));
    }

    // ------------------------------------------------------------------ NFS-e

    @Test
    void nfseCompetenceInAnotherMonth() {
        Nfse n = new Nfse();
        n.dCompet = "2026-08-31";
        assertOnly(entrada(n), "ERR-3.4");
    }

    @Test
    void nfseIssMustBeBaseTimesRate() {
        Nfse n = new Nfse();
        n.vISSQN = "25.00";
        assertOnly(entrada(n), "ERR-12.1");
    }

    @Test
    void nfseConditionalDiscountMustNotReduceBase() {
        Nfse n = new Nfse();
        n.descontos = "<vDescCondIncond><vDescCond>100.00</vDescCond></vDescCondIncond>";
        n.vBC = "900.00";
        n.vISSQN = "18.00";
        n.vLiq = "900.00";
        assertTrue(get(entrada(n), "ERR-12.2").message().contains("condicional"));
        n.descontos = "<vDescCondIncond><vDescIncond>100.00</vDescIncond></vDescCondIncond>";
        assertOnly(entrada(n));
    }

    @Test
    void nfseTaxationCode() {
        Nfse n = new Nfse();
        n.tribISSQN = "3";
        List<Finding> fs = entrada(n);
        assertTrue(get(fs, "ERR-12.5").message().contains("tomador no Brasil"));
        Nfse m = new Nfse();
        m.tribISSQN = "2";
        m.vISSQN = null;
        m.vBC = null;
        m.pAliqAplic = null;
        m.pAliq = null;
        assertOnly(entrada(m), "ERR-12.5");
    }

    @Test
    void nfseFederalWithholdings() {
        Nfse n = new Nfse();
        n.tribFed = "<tribFed><piscofins><CST>01</CST><vBCPisCofins>1000.00</vBCPisCofins><pAliqPis>0.65</pAliqPis><pAliqCofins>3.00</pAliqCofins>"
                + "<vPis>6.50</vPis><vCofins>30.00</vCofins><tpRetPisCofins>1</tpRetPisCofins></piscofins><vRetIRRF>15.00</vRetIRRF>"
                + "<vRetCSLL>10.00</vRetCSLL></tribFed>";
        n.vLiq = "938.50";
        assertOnly(entrada(n));
        n.tribFed = n.tribFed.replace("<vRetCSLL>10.00</vRetCSLL>", "");
        n.vLiq = "948.50";
        assertTrue(get(entrada(n), "ERR-13.2").message().contains("sem CSLL"));
    }

    @Test
    void nfseSmallWithholdingSimplesAndNetValue() {
        Nfse n = new Nfse();
        n.tribFed = "<tribFed><vRetIRRF>5.00</vRetIRRF></tribFed>";
        n.vLiq = "995.00";
        assertOnly(entrada(n), "ERR-13.5");
        n.opSimpNac = "3";
        n.tribFed = "<tribFed><vRetIRRF>15.00</vRetIRRF></tribFed>";
        n.vLiq = "985.00";
        assertOnly(entrada(n), "ERR-13.6");
        n.opSimpNac = "1";
        n.vLiq = "1000.00";
        assertOnly(entrada(n), "ERR-13.7");
    }

    @Test
    void ownNfseOfPresumidoCompanyWithNonCumulativeRates() {
        Nfse n = new Nfse();
        n.provider = L5;
        n.taker = "<CNPJ>" + OTHER + "</CNPJ>";
        n.tribFed = "<tribFed><piscofins><CST>01</CST><vBCPisCofins>1000.00</vBCPisCofins><pAliqPis>1.65</pAliqPis><pAliqCofins>7.60</pAliqCofins>"
                + "<vPis>16.50</vPis><vCofins>76.00</vCofins><tpRetPisCofins>2</tpRetPisCofins></piscofins></tribFed>";
        List<Finding> fs = run(TestSigner.sign(n.xml()), Direction.SAIDA, Collections.<FiscalEvent>emptyList(), null);
        assertOnly(fs, "ERR-11.1");
    }

    @Test
    void findingDetailShowsImpactAndBasis() {
        Nfe n = new Nfe();
        n.item().icms = icms00("100.00", "18.00", "17.00");
        n.item().tICMS = "17.00";
        Finding f = get(saida(n), "ERR-6.1");
        assertTrue(f.detail().contains("Afeta: ICMS"));
        assertTrue(f.detail().contains("LC 87/1996"));
    }
}
