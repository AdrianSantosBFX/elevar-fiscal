package br.com.facilitasig.fiscal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo das verificações aplicadas na importação.
 *
 * <p>IMP-nnn: regras da v0.0.1 (estrutura, identidade, coerência). ERR-g.n: verificações da
 * v0.1.1 que olham apenas o XML da nota e os eventos dela; o número é o item do catálogo
 * erros-nfe-nfse-v0.0.2. Base legal marcada [validar] ainda depende de conferência.
 */
public final class RuleCatalog {

    private static final String ALL = "Todos os tributos da nota";
    private static final String MOC = "MOC NF-e 7.0";
    private static final String NFSE = "Leiaute NFS-e nacional v1.00 [validar com XML real]";

    private static final Map<String, Rule> RULES = new LinkedHashMap<String, Rule>();

    static {
        // ---------------------------------------------------------------- v0.0.1 (importação)
        r("IMP-001", "Arquivo ilegível ou tipo não suportado", ALL, "Leiaute NF-e (" + MOC + ") e NFS-e nacional", "—");
        r("IMP-002", "Chave de acesso incoerente com a nota", "EFD ICMS/IPI (C100)", MOC + ", Anexo I (chave de acesso)", "1.5");
        r("IMP-003", "Nota não pertence à empresa ativa", ALL, "Regra interna de pertencimento", "2.1");
        r("IMP-004", "CNPJ/CPF com dígito verificador inválido", "EFD ICMS/IPI (0150); EFD-Contribuições", "Algoritmo módulo 11 do CNPJ/CPF", "2.3");
        r("IMP-005", "NF-e denegada ou sem autorização vigente", ALL, "Ajuste SINIEF 07/2005", "1.2");
        r("IMP-006", "XML sem protocolo de autorização", ALL, "Ajuste SINIEF 07/2005, cl. 7ª (autorização de uso)", "1.2");
        r("IMP-007", "Itens ausentes ou soma dos itens diferente de vProd", ALL, MOC + ", grupo W (totais)", "5.2");
        r("IMP-008", "vNF diferente da fórmula do MOC", ALL, MOC + ", regra de validação de vNF", "5.3");
        r("IMP-009", "NCM ou CFOP inválido, ou CFOP incompatível com tpNF", ALL, "Convênio SINIEF s/nº 1970 (tabela CFOP); " + MOC, "4.1");
        r("IMP-010", "Data de emissão ausente, inválida ou futura", ALL, MOC + " (dhEmi)", "3.1");
        r("IMP-011", "Data de entrada/saída ausente ou anterior à emissão", ALL, MOC + " (dhSaiEnt); Guia Prático EFD (DT_E_ES)", "3.2");
        r("IMP-012", "NFS-e sem cTribNac", "ISS; IRPJ/CSLL", NFSE, "4.13");
        r("IMP-013", "ISS retido sem valor de ISS", "ISS", NFSE, "12.9");
        r("IMP-014", "Ambiente de homologação", ALL, MOC + " (tpAmb)", "1.3");
        r("IMP-015", "UF ou município do participante ausente ou incoerente", "ICMS; ISS", "Tabela IBGE de municípios", "2.5");
        r("IMP-016", "CFOP interno/interestadual incoerente com as UFs", "ICMS; DIFAL", "Convênio SINIEF s/nº 1970 (CFOP)", "4.2");
        r("IMP-020", "Nota já importada (duplicata)", ALL, "Regra interna", "1.7");

        // ---------------------------------------------------------------- v0.1.1 (só XML)
        r("ERR-1.1", "Nota cancelada por evento", ALL + "; EFD ICMS/IPI (COD_SIT 02/03)",
                "Ajuste SINIEF 07/2005, cl. 12ª; Guia Prático EFD ICMS/IPI (tabela COD_SIT)", "1.1");
        r("ERR-1.6", "Assinatura digital ausente, inválida ou XML alterado", ALL,
                "Ajuste SINIEF 07/2005, cl. 4ª (assinatura ICP-Brasil); MP 2.200-2/2001", "1.6");
        r("ERR-1.10", "CC-e tentando corrigir campo vedado", ALL,
                "Ajuste SINIEF 07/2005, cl. 14ª-A; Convênio SINIEF s/nº 1970, art. 7º, §1º-A [validar]", "1.10");
        r("ERR-1.12", "NFS-e em leiaute municipal não suportado", "ISS; IRPJ/CSLL; PIS/Cofins",
                "Leiautes municipais (ABRASF) fora do escopo desta versão", "1.12");
        r("ERR-2.6", "Operação com exterior incoerente (idDest, CFOP 7.xxx/3.xxx)", "ICMS; PIS/Cofins",
                MOC + " (idDest = 3); Convênio SINIEF s/nº 1970 (CFOP 3.xxx e 7.xxx)", "2.6");
        r("ERR-3.3", "Emissão e saída em meses diferentes", "ICMS; PIS/Cofins; IRPJ/CSLL",
                "LC 87/1996, art. 12, I (fato gerador na saída); Guia Prático EFD (DT_E_ES)", "3.3");
        r("ERR-3.4", "NFS-e com competência diferente do mês de emissão", "ISS; IRPJ/CSLL; PIS/Cofins",
                "LC 116/2003; " + NFSE + " (dCompet)", "3.4");
        r("ERR-4.2", "idDest incoerente com as UFs", "ICMS; DIFAL", MOC + " (idDest, indPres, indFinal)", "4.2");
        r("ERR-4.5", "Devolução sem referência, sem finNFe = 4 ou com CFOP de não devolução", "IRPJ/CSLL; PIS/Cofins; ICMS",
                MOC + " (finNFe, NFref); Convênio SINIEF s/nº 1970 (CFOP de devolução)", "4.5");
        r("ERR-4.7", "Nota complementar ou de ajuste sem referência", "ICMS; IPI", MOC + " (finNFe 2 e 3, NFref)", "4.7");
        r("ERR-4.12", "Serviço na NF-e (ISSQN) incoerente com o CFOP", "ISS; ICMS; IRPJ/CSLL",
                "LC 116/2003; Convênio SINIEF s/nº 1970 (CFOP x.933); " + MOC + " (grupo ISSQN)", "4.12");
        r("ERR-5.1", "vProd diferente de quantidade × valor unitário", ALL, MOC + ", grupo I (prod)", "5.1");
        r("ERR-5.2", "Total da nota diferente da soma dos itens", ALL + "; EFD ICMS/IPI (C100/C190)", MOC + ", grupo W (ICMSTot)", "5.2");
        r("ERR-5.4", "Item fora do total (indTot = 0) somado ao vProd", ALL, MOC + " (indTot)", "5.4");
        r("ERR-5.5", "Desconto maior que o valor do item (detecção parcial)", "ICMS; IRPJ/CSLL; PIS/Cofins",
                "LC 87/1996, art. 13, §1º, II, \"a\" (desconto condicional integra a base)", "5.5");
        r("ERR-5.7", "Modalidade de frete incoerente com o valor do frete", "ICMS; IRPJ/CSLL",
                MOC + " (modFrete); LC 87/1996, art. 13, §1º, II, \"b\"", "5.7");
        r("ERR-5.8", "Pagamentos ou duplicatas diferentes do total", "IRPJ/CSLL (regime de caixa); EFD ICMS/IPI (C140/C141)",
                MOC + " (grupos pag e cobr)", "5.8");
        r("ERR-6.1", "ICMS diferente de base × alíquota", "ICMS", "LC 87/1996, art. 13", "6.1");
        r("ERR-6.2", "Base do ICMS incoerente com os valores do item", "ICMS",
                "LC 87/1996, art. 13, §1º (acessórias) e §2º (IPI na base quando destinado a consumo)", "6.2");
        r("ERR-6.3", "Alíquota interestadual de ICMS incoerente com a origem e as UFs", "ICMS",
                "Resolução do Senado 22/1989 (7% e 12%) e 13/2012 (4% para importados)", "6.3");
        r("ERR-6.6", "CST de ICMS incoerente com o CFOP", "ICMS; ICMS-ST",
                "Convênio SINIEF s/nº 1970 (CFOP x.401 a x.405 e tabela CST)", "6.6");
        r("ERR-6.7", "CST tributado sem imposto", "ICMS", "Convênio SINIEF s/nº 1970, Anexo (Tabela B do CST)", "6.7");
        r("ERR-6.8", "CST/CSOSN incoerente com o regime do emitente (CRT)", "ICMS",
                MOC + " (CRT × CST/CSOSN); LC 123/2006", "6.8");
        r("ERR-6.10", "Diferimento (CST 51) com valores incoerentes", "ICMS",
                "Regulamento do ICMS da UF (diferimento); " + MOC + " (grupo ICMS51)", "6.10");
        r("ERR-6.12", "Exportação com ICMS destacado", "ICMS",
                "CF/88, art. 155, §2º, X, \"a\"; LC 87/1996, art. 3º, II", "6.12");
        r("ERR-7.7", "CST corrigido pelo usuário incompatível com o grupo do XML", "ICMS; EFD ICMS/IPI",
                "Regra interna (correção × XML original)", "7.7");
        r("ERR-8.4", "ICMS-ST diferente de base ST × alíquota − ICMS próprio", "ICMS-ST",
                "LC 87/1996, arts. 6º a 10; convênios e protocolos de ST", "8.4");
        r("ERR-8.7", "CST 60 sem os valores do ST retido", "ICMS-ST; EFD ICMS/IPI (C176)",
                MOC + " (vBCSTRet, pST, vICMSSTRet); Guia Prático EFD (C176)", "8.7");
        r("ERR-9.1", "Venda interestadual a não contribuinte sem DIFAL", "DIFAL",
                "EC 87/2015; LC 190/2022; Convênio ICMS 236/2021", "9.1");
        r("ERR-9.2", "Indicadores do destinatário incoerentes (indIEDest, indFinal, IE)", "DIFAL; ICMS",
                MOC + " (indIEDest, indFinal)", "9.2");
        r("ERR-9.3", "Partilha ou valor do DIFAL incoerente", "DIFAL",
                "EC 87/2015 (100% ao destino desde 2019); Convênio ICMS 236/2021", "9.3");
        r("ERR-10.2", "CST de IPI incoerente com o tipo de operação", "IPI",
                MOC + " (tabela CST do IPI: 00–49 entradas, 50–99 saídas)", "10.2");
        r("ERR-10.3", "IPI diferente de base × alíquota, ou base incoerente", "IPI",
                "RIPI (Decreto 7.212/2010), arts. 189 e 190", "10.3");
        r("ERR-11.1", "Alíquotas não cumulativas de PIS/Cofins em nota do Lucro Presumido", "PIS/Cofins; EFD-Contribuições",
                "Lei 9.718/1998 (0,65% e 3%); Lei 10.637/2002, art. 8º, II; Lei 10.833/2003, art. 10, II", "11.1");
        r("ERR-11.5", "Base ou valor de PIS/Cofins incoerentes", "PIS/Cofins",
                "Lei 9.718/1998, art. 3º, §2º, I (exclusão de IPI e ICMS-ST); RE 574.706 (Tema 69)", "11.5");
        r("ERR-11.6", "Exportação com PIS/Cofins", "PIS/Cofins",
                "MP 2.158-35/2001, art. 14; Lei 10.637/2002, art. 5º; Lei 10.833/2003, art. 6º", "11.6");
        r("ERR-12.1", "ISS diferente de base × alíquota", "ISS", "LC 116/2003, arts. 7º e 8º-A", "12.1");
        r("ERR-12.2", "Base do ISS diferente do serviço menos descontos incondicionais e deduções", "ISS",
                "LC 116/2003, art. 7º; " + NFSE, "12.2");
        r("ERR-12.5", "Tributação do ISSQN (tribISSQN) incoerente", "ISS; PIS/Cofins",
                "LC 116/2003, art. 2º; CF/88, art. 150, VI; " + NFSE, "12.5");
        r("ERR-13.2", "Retenção de CSRF parcial ou fora de 4,65%", "Retenções; PIS/Cofins; CSLL",
                "Lei 10.833/2003, arts. 30 e 31; IN SRF 459/2004", "13.2");
        r("ERR-13.5", "Retenção feita com valor de até R$ 10,00", "Retenções",
                "Lei 10.833/2003, art. 31, §3º (Lei 13.137/2015); Lei 9.430/1996, art. 67", "13.5");
        r("ERR-13.6", "Prestador do Simples Nacional com retenção federal", "Retenções",
                "LC 123/2006; IN SRF 459/2004; IN SRF 765/2007 [validar]", "13.6");
        r("ERR-13.7", "Valor líquido da NFS-e incoerente", "Retenções; ISS", NFSE + " (vLiq, vTotalRet)", "13.7");
        r("ERR-13.10", "Compra de produtor rural pessoa física: verificar contribuição retida", "Retenções; EFD-Reinf (R-2055)",
                "Lei 8.212/1991, arts. 25 e 30; Lei 8.315/1991 (Senar) [validar alíquotas]", "13.10");
        r("ERR-16.2", "Unidades comercial e tributável diferentes com conversão 1:1", "EFD ICMS/IPI (0220)",
                "Guia Prático EFD (registro 0220); " + MOC + " (uCom/uTrib)", "16.2");
        r("ERR-16.5", "Processo ou benefício citado sem estrutura para a EFD", "EFD ICMS/IPI (C110/C111/C113/C197)",
                "Guia Prático EFD; " + MOC + " (infAdic/procRef)", "16.5");
        r("ERR-17.1", "Grupo IBS/CBS ausente após a obrigatoriedade", "IBS/CBS",
                "LC 214/2025, art. 348; NT 2025.002-RTC (cronograma) [validar data]", "17.1");
        r("ERR-17.3", "Valores de IBS/CBS incoerentes", "IBS/CBS",
                "LC 214/2025, arts. 343 e 344 (2026: CBS 0,9%, IBS 0,1%) [validar]; NT 2025.002-RTC", "17.3");
    }

    private RuleCatalog() {
    }

    private static void r(String id, String title, String impacts, String basis, String ref) {
        RULES.put(id, new Rule(id, title, impacts, basis, ref));
    }

    /** Regra pelo id; nunca nulo (regra desconhecida vira um registro genérico). */
    public static Rule get(String id) {
        Rule r = RULES.get(id);
        return r != null ? r : new Rule(id, id, Fields.NA, Fields.NA, Fields.NA);
    }

    public static boolean contains(String id) {
        return RULES.containsKey(id);
    }

    public static List<Rule> all() {
        return Collections.unmodifiableList(new ArrayList<Rule>(RULES.values()));
    }
}
