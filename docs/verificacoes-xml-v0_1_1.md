# Elevar Fiscal (anteriormente Facilita.sig) — Verificações do XML (v0.1.1)

Versão de 22/10/2026. Implementa as verificações do catálogo `erros-nfe-nfse-v0.0.2` que dependem **apenas do XML da nota e dos seus eventos** (classe SO_XML). Não usa tabelas de referência, parâmetros além do regime da empresa nem cruzamento entre notas.

## Cobertura
- 65 itens SO_XML no catálogo; **58 cobertos** (por regras IMP já existentes, IMP corrigidas ou regras ERR novas).
- **48 regras novas (ERR-g.n)**: o número é o item do catálogo. As IMP-nnn mantêm os nomes da v0.0.1.
- Fora desta versão: 7.1 a 7.5 (créditos de ICMS, dependem da apuração), 12.10 e 13.8 (dependem da guia). O item 5.6 está coberto só na parte do ICMS (ERR-6.2).
- Extra: ERR-1.1 (cancelamento por evento), antes classificado como X+E, passou a ser só XML porque o evento é importado.

## Gravidade
Todas as ERR geram **ALERTA**, exceto:
- ERR-1.1 (nota cancelada por evento): ERRO.
- ERR-1.6 (assinatura): ERRO quando a assinatura é inválida ou o XML foi alterado; ALERTA quando não há assinatura ou ela não pôde ser verificada.
- ERR-1.12 (NFS-e em leiaute municipal): ERRO.

Notas com ERRO bloqueiam a conclusão da etapa 3 (regra da v0.0.1).

## Regras novas
| Regra | O que verifica | Afeta | Base |
|---|---|---|---|
| ERR-1.1 | Nota cancelada por evento | Todos os tributos da nota; EFD ICMS/IPI (COD_SIT 02/03) | Ajuste SINIEF 07/2005, cl. 12?; Guia Pr?tico EFD ICMS/IPI (tabela COD_SIT) |
| ERR-1.6 | Assinatura digital ausente, inv?lida ou XML alterado | Todos os tributos da nota | Ajuste SINIEF 07/2005, cl. 4? (assinatura ICP-Brasil); MP 2.200-2/2001 |
| ERR-1.10 | CC-e tentando corrigir campo vedado | Todos os tributos da nota | Ajuste SINIEF 07/2005, cl. 14?-A; Conv?nio SINIEF s/n? 1970, art. 7?, ?1?-A [validar] |
| ERR-1.12 | NFS-e em leiaute municipal n?o suportado | ISS; IRPJ/CSLL; PIS/Cofins | Leiautes municipais (ABRASF) fora do escopo desta vers?o |
| ERR-2.6 | Opera??o com exterior incoerente (idDest, CFOP 7.xxx/3.xxx) | ICMS; PIS/Cofins | MOC NF-e 7.0 (idDest = 3); Conv?nio SINIEF s/n? 1970 (CFOP 3.xxx e 7.xxx) |
| ERR-3.3 | Emiss?o e sa?da em meses diferentes | ICMS; PIS/Cofins; IRPJ/CSLL | LC 87/1996, art. 12, I (fato gerador na sa?da); Guia Pr?tico EFD (DT_E_ES) |
| ERR-3.4 | NFS-e com compet?ncia diferente do m?s de emiss?o | ISS; IRPJ/CSLL; PIS/Cofins | LC 116/2003; Leiaute NFS-e nacional v1.00 [validar com XML real] (dCompet) |
| ERR-4.2 | idDest incoerente com as UFs | ICMS; DIFAL | MOC NF-e 7.0 (idDest, indPres, indFinal) |
| ERR-4.5 | Devolu??o sem refer?ncia, sem finNFe = 4 ou com CFOP de n?o devolu??o | IRPJ/CSLL; PIS/Cofins; ICMS | MOC NF-e 7.0 (finNFe, NFref); Conv?nio SINIEF s/n? 1970 (CFOP de devolu??o) |
| ERR-4.7 | Nota complementar ou de ajuste sem refer?ncia | ICMS; IPI | MOC NF-e 7.0 (finNFe 2 e 3, NFref) |
| ERR-4.12 | Servi?o na NF-e (ISSQN) incoerente com o CFOP | ISS; ICMS; IRPJ/CSLL | LC 116/2003; Conv?nio SINIEF s/n? 1970 (CFOP x.933); MOC NF-e 7.0 (grupo ISSQN) |
| ERR-5.1 | vProd diferente de quantidade ? valor unit?rio | Todos os tributos da nota | MOC NF-e 7.0, grupo I (prod) |
| ERR-5.2 | Total da nota diferente da soma dos itens | Todos os tributos da nota; EFD ICMS/IPI (C100/C190) | MOC NF-e 7.0, grupo W (ICMSTot) |
| ERR-5.4 | Item fora do total (indTot = 0) somado ao vProd | Todos os tributos da nota | MOC NF-e 7.0 (indTot) |
| ERR-5.5 | Desconto maior que o valor do item (detec??o parcial) | ICMS; IRPJ/CSLL; PIS/Cofins | LC 87/1996, art. 13, ?1?, II, "a" (desconto condicional integra a base) |
| ERR-5.7 | Modalidade de frete incoerente com o valor do frete | ICMS; IRPJ/CSLL | MOC NF-e 7.0 (modFrete); LC 87/1996, art. 13, ?1?, II, "b" |
| ERR-5.8 | Pagamentos ou duplicatas diferentes do total | IRPJ/CSLL (regime de caixa); EFD ICMS/IPI (C140/C141) | MOC NF-e 7.0 (grupos pag e cobr) |
| ERR-6.1 | ICMS diferente de base ? al?quota | ICMS | LC 87/1996, art. 13 |
| ERR-6.2 | Base do ICMS incoerente com os valores do item | ICMS | LC 87/1996, art. 13, ?1? (acess?rias) e ?2? (IPI na base quando destinado a consumo) |
| ERR-6.3 | Al?quota interestadual de ICMS incoerente com a origem e as UFs | ICMS | Resolu??o do Senado 22/1989 (7% e 12%) e 13/2012 (4% para importados) |
| ERR-6.6 | CST de ICMS incoerente com o CFOP | ICMS; ICMS-ST | Conv?nio SINIEF s/n? 1970 (CFOP x.401 a x.405 e tabela CST) |
| ERR-6.7 | CST tributado sem imposto | ICMS | Conv?nio SINIEF s/n? 1970, Anexo (Tabela B do CST) |
| ERR-6.8 | CST/CSOSN incoerente com o regime do emitente (CRT) | ICMS | MOC NF-e 7.0 (CRT ? CST/CSOSN); LC 123/2006 |
| ERR-6.10 | Diferimento (CST 51) com valores incoerentes | ICMS | Regulamento do ICMS da UF (diferimento); MOC NF-e 7.0 (grupo ICMS51) |
| ERR-6.12 | Exporta??o com ICMS destacado | ICMS | CF/88, art. 155, ?2?, X, "a"; LC 87/1996, art. 3?, II |
| ERR-7.7 | CST corrigido pelo usu?rio incompat?vel com o grupo do XML | ICMS; EFD ICMS/IPI | Regra interna (corre??o ? XML original) |
| ERR-8.4 | ICMS-ST diferente de base ST ? al?quota ? ICMS pr?prio | ICMS-ST | LC 87/1996, arts. 6? a 10; conv?nios e protocolos de ST |
| ERR-8.7 | CST 60 sem os valores do ST retido | ICMS-ST; EFD ICMS/IPI (C176) | MOC NF-e 7.0 (vBCSTRet, pST, vICMSSTRet); Guia Pr?tico EFD (C176) |
| ERR-9.1 | Venda interestadual a n?o contribuinte sem DIFAL | DIFAL | EC 87/2015; LC 190/2022; Conv?nio ICMS 236/2021 |
| ERR-9.2 | Indicadores do destinat?rio incoerentes (indIEDest, indFinal, IE) | DIFAL; ICMS | MOC NF-e 7.0 (indIEDest, indFinal) |
| ERR-9.3 | Partilha ou valor do DIFAL incoerente | DIFAL | EC 87/2015 (100% ao destino desde 2019); Conv?nio ICMS 236/2021 |
| ERR-10.2 | CST de IPI incoerente com o tipo de opera??o | IPI | MOC NF-e 7.0 (tabela CST do IPI: 00?49 entradas, 50?99 sa?das) |
| ERR-10.3 | IPI diferente de base ? al?quota, ou base incoerente | IPI | RIPI (Decreto 7.212/2010), arts. 189 e 190 |
| ERR-11.1 | Al?quotas n?o cumulativas de PIS/Cofins em nota do Lucro Presumido | PIS/Cofins; EFD-Contribui??es | Lei 9.718/1998 (0,65% e 3%); Lei 10.637/2002, art. 8?, II; Lei 10.833/2003, art. 10, II |
| ERR-11.5 | Base ou valor de PIS/Cofins incoerentes | PIS/Cofins | Lei 9.718/1998, art. 3?, ?2?, I (exclus?o de IPI e ICMS-ST); RE 574.706 (Tema 69) |
| ERR-11.6 | Exporta??o com PIS/Cofins | PIS/Cofins | MP 2.158-35/2001, art. 14; Lei 10.637/2002, art. 5?; Lei 10.833/2003, art. 6? |
| ERR-12.1 | ISS diferente de base ? al?quota | ISS | LC 116/2003, arts. 7? e 8?-A |
| ERR-12.2 | Base do ISS diferente do servi?o menos descontos incondicionais e dedu??es | ISS | LC 116/2003, art. 7?; Leiaute NFS-e nacional v1.00 [validar com XML real] |
| ERR-12.5 | Tributa??o do ISSQN (tribISSQN) incoerente | ISS; PIS/Cofins | LC 116/2003, art. 2?; CF/88, art. 150, VI; Leiaute NFS-e nacional v1.00 [validar com XML real] |
| ERR-13.2 | Reten??o de CSRF parcial ou fora de 4,65% | Reten??es; PIS/Cofins; CSLL | Lei 10.833/2003, arts. 30 e 31; IN SRF 459/2004 |
| ERR-13.5 | Reten??o feita com valor de at? R$ 10,00 | Reten??es | Lei 10.833/2003, art. 31, ?3? (Lei 13.137/2015); Lei 9.430/1996, art. 67 |
| ERR-13.6 | Prestador do Simples Nacional com reten??o federal | Reten??es | LC 123/2006; IN SRF 459/2004; IN SRF 765/2007 [validar] |
| ERR-13.7 | Valor l?quido da NFS-e incoerente | Reten??es; ISS | Leiaute NFS-e nacional v1.00 [validar com XML real] (vLiq, vTotalRet) |
| ERR-13.10 | Compra de produtor rural pessoa f?sica: verificar contribui??o retida | Reten??es; EFD-Reinf (R-2055) | Lei 8.212/1991, arts. 25 e 30; Lei 8.315/1991 (Senar) [validar al?quotas] |
| ERR-16.2 | Unidades comercial e tribut?vel diferentes com convers?o 1:1 | EFD ICMS/IPI (0220) | Guia Pr?tico EFD (registro 0220); MOC NF-e 7.0 (uCom/uTrib) |
| ERR-16.5 | Processo ou benef?cio citado sem estrutura para a EFD | EFD ICMS/IPI (C110/C111/C113/C197) | Guia Pr?tico EFD; MOC NF-e 7.0 (infAdic/procRef) |
| ERR-17.1 | Grupo IBS/CBS ausente ap?s a obrigatoriedade | IBS/CBS | LC 214/2025, art. 348; NT 2025.002-RTC (cronograma) [validar data] |
| ERR-17.3 | Valores de IBS/CBS incoerentes | IBS/CBS | LC 214/2025, arts. 343 e 344 (2026: CBS 0,9%, IBS 0,1%) [validar]; NT 2025.002-RTC |

## Ajustes nas regras da v0.0.1 (falsos alertas)
| Regra | Ajuste |
|---|---|
| IMP-007 | Itens com `indTot = 0` e serviços (grupo ISSQN) não entram na soma de `vProd`. Se o total só fecha somando itens fora do total, gera ERR-5.4 |
| IMP-008 | Aceita o `vServ` da NF-e conjugada (`ISSQNtot`); notas complementares e de ajuste (`finNFe` 2 e 3) não são cobradas pela fórmula |
| IMP-009 | CFOP conferido pelo `tpNF` (0 → 1/2/3; 1 → 5/6/7), também em notas de terceiros |
| IMP-015 | Destinatário no exterior (UF EX, NIF) não gera alerta de UF/município |
| IMP-016 | Venda presencial a consumidor final de outra UF (`indPres = 1` e `indFinal = 1`) é operação interna |
| IMP-001 | NFS-e no padrão municipal (ABRASF) passa a ser identificada como ERR-1.12 |

## Eventos de NF-e
- Importados pelas mesmas telas de Entrada e Saída (arquivos `procEventoNFe`).
- Gravados: cancelamento (110111) e carta de correção (110110), só com registro na SEFAZ (`cStat` 135, 136 ou 155). Os demais eventos continuam ignorados.
- Ligados à nota pela chave, nos dois sentidos da empresa ativa. Se a nota já foi importada, é revalidada na hora; se chegar depois, o evento é aplicado na importação.
- CC-e: vale a de maior sequência. Gera ERR-1.10 quando o texto cita campo vedado (valor, base de cálculo, alíquota, quantidade, preço, desconto, imposto, ICMS, IPI, CNPJ, CPF, razão social, remetente, destinatário, datas). É uma busca por termos: confirme o texto.
- Eventos de NFS-e: ainda não.

## Banco de dados
- Esquema v3: tabela `fiscal_event`. A migração roda sozinha ao abrir o sistema e mantém as notas já importadas (coberto por teste).
- Impacto e base legal **não** ficam no banco: vêm do código (`RuleCatalog`), para acompanharem a versão do sistema.
- Atenção para quem mexer no código: as colunas de `fiscal_doc` vêm de `Fields.DOC`. Acrescentar chaves ali exige migração nova.

## Código
- `fiscal/RuleCatalog`, `fiscal/Rule`: catálogo das regras (título, impacto, base legal, item do catálogo).
- `fiscal/NfeXmlChecks`, `fiscal/NfseXmlChecks`: verificações; releem o XML original guardado no banco.
- `fiscal/SignatureCheck`: integridade da assinatura XML com a chave do próprio XML. **Não** valida a cadeia ICP-Brasil nem revogação.
- `fiscal/FiscalEvent` + `FiscalDocRepository` (eventos) + `FiscalService.importEvent`.
- `ui/RulesCatalogFrame`: Conferência > Regras Aplicadas e Base Legal.
- Telas de notas: cada achado mostra mensagem, sugestão, "Afeta" e "Base".
- CFOP e CST usam o valor efetivo (com a correção do usuário); valores monetários vêm sempre do XML original.

## Pontos a validar
1. Leiaute da NFS-e nacional (nomes de campos, `tpRetPisCofins`, fórmula de `vLiq`/`vTotalRet`) com XMLs reais. Por isso as regras de NFS-e só geram ALERTA.
2. Datas de obrigatoriedade do grupo IBS/CBS: 03/08/2026 (CRT 3) e 04/01/2027 (CRT 1, 2 e 4), tiradas de `nfe-02`.
3. Alíquotas de 2026 (CBS 0,9%; IBS-UF 0,1%; IBS-Mun 0%), conferidas só para `cClassTrib` 000001 sem redução.
4. ERR-13.10 (produtor rural) e ERR-13.6 (exceções de retenção do Simples).
5. ERR-8.7 (CST 60 sem ST retido) pode gerar muitos alertas em notas reais; se gerar ruído, restringir às notas emitidas pela empresa.
6. ERR-6.2: base do ICMS pelo valor da operação (`modBC = 3`), aceitando com ou sem IPI conforme `indFinal`; importação (CFOP 3.xxx) fica de fora.

## Estado de validação
- **83 testes aprovados** (29 da v0.0.1 + 54 novos). Cada teste novo parte de uma nota coerente, sem achados, e provoca um único erro. XMLs de teste assinados de verdade (RSA-SHA1, como a NF-e).
- Bytecode Java 8 (versão 52), compilado com `-source 8 -target 8` no JDK 21. Este ambiente não tinha os arquivos para `--release 8`; o uso de APIs posteriores ao Java 8 foi conferido por busca manual.
- Testes executados com um substituto mínimo do JUnit 5. **`mvn package` não executado** (Maven indisponível).
- **Não executado em Java 8 real nem no Windows.** A interface não foi aberta em tela real; só a tela do catálogo foi renderizada fora de tela.
