# Elevar Fiscal (anteriormente Facilita.sig) — Alíquotas de ICMS e ISS (v0.1.4)

Versão de 23/10/2026. Fase 2 (ICMS) e Fase 3 (ISS) do plano `base-de-dados-plano-de-implantacao`, sobre a infraestrutura da v0.1.3 (banco `elevar-fiscal-ref.db`). Como na v0.1.3, as tabelas **só são consultadas**; nenhuma regra de conferência passou a usá-las ainda.

## Conjuntos novos
| Conjunto | Grau | Linhas | Conteúdo e fonte |
|---|---|---|---|
| `icms-interestadual` | Curadoria | 1.404 | 702 pares de UF × 2 tipos. GERAL: Res. Senado 22/1989 (Sul/Sudeste exceto ES → N/NE/CO/ES = 7%; demais 12%), desde 01/01/1990. IMPORTADO: 4% (Res. Senado 13/2012), desde 01/01/2013. Gerado pela regra e conferido com a matriz da planilha Fiscalia: 100% igual |
| `icms-interna` | Secundária | 31 | Alíquota interna geral (modal) das 27 UFs, sem fundo de pobreza. Cobertura desde 01/01/2025. Vigências registradas: MA 22% → 23% em 23/02/2025 (Lei 12.426/2024); PI 21% → 22,5% em 01/04/2025 (Lei 8.558/2024); RN 18% → 20% em 20/03/2025 (Lei 11.999/2024); AL 19% → 20,5% em 01/04/2026 (Lei 9.776/2025). RJ: 20% + 2% FECP |
| `lc116` | Secundária | 200 | Subitens da lista da LC 116/2003 (texto da lei, da planilha RETENSERV), só para descrição |
| `iss` | **Oficial** | 1.918.512 | Alíquotas de ISSQN do Portal NFS-e (gov.br/nfse/pt-br/biblioteca/aliquotas), arquivo `aliquotas-municipios-20260903-extr1.zip` (SHA-256 1519370b…4a56), TXT consolidado sem alteração. Licença CC BY-ND 3.0 |

## Achados na carga do ISS
- 1.919.368 linhas no arquivo; **5.341 municípios** de 26 UFs; 333 códigos de serviço (199 subitens da LC 116).
- **Faltam**: DF, São Paulo capital, Rio de Janeiro capital e cerca de 226 outros municípios (o Portal diz cobrir 5.571).
- **856 linhas com fim de vigência anterior ao início**: ignoradas.
- **18.480 vigências sem alíquota** (campo vazio no Portal, 262 municípios): gravadas sem alíquota; a consulta responde SEM_COBERTURA.
- Todas as alíquotas informadas estão entre 2% e 5%.
- **32 pares município × serviço com duas alíquotas diferentes vigentes em 23/10/2026** (ex.: Pouso Alto/MG, 04.01.01: 5% desde 29/12/2025 e 2% desde 05/01/2026, ambas sem fim): a consulta responde SEM_COBERTURA (não escolhe uma). **Decisão a confirmar**: tratar a vigência mais recente como a válida?
- A parametrização dos municípios começa, em geral, entre set/2025 e mar/2026: notas anteriores à parametrização do município ficam SEM_COBERTURA.

## Consulta
- **ICMS interestadual** (`icmsInterstate(origem, destino, origemMercadoria, data)`): origem da mercadoria 1, 2, 3 ou 8 → 4% (a partir de 2013); outras ou não informada → regra geral (a mensagem avisa quando não foi informada). Mesma UF → alíquota interna. Antes de 1990: SEM_COBERTURA.
- **ICMS interno** (`icmsInternal(uf, data)`): alíquota geral (modal) e FCP geral à parte. Antes de 01/01/2025: SEM_COBERTURA. A mensagem lembra que produtos com alíquota própria, redução de base ou ST não estão na tabela.
- **ISS** (`issRate(ibge, serviço, data)`): serviço como cTribNac da NFS-e nacional (6 dígitos, `010101`) ou código do Portal (9 dígitos, `01.01.01.000`). SEM_COBERTURA quando: município fora da tabela, serviço não parametrizado, data anterior à parametrização, vigência sem alíquota ou alíquotas conflitantes. `IssRate.withinLegalRange()` indica a faixa de 2% a 5% da LC 116.
- Mapeamento cTribNac → código de 9 dígitos: acrescenta `000` (todos os códigos do arquivo terminam em `000`) [validar com NFS-e real].

## Atualização do ISS pelo usuário
- O Portal publica arquivos novos de tempos em tempos. Na aba ISS: **Abrir página do Portal** → baixar o ZIP → **Importar arquivo do Portal NFS-e...** (aceita o ZIP, o TXT consolidado ou um CSV de UF).
- A importação roda em segundo plano, numa transação: se o arquivo estiver errado, a versão anterior continua valendo.
- A data da fonte vem do nome do arquivo (`aliquotas-municipios-AAAAMMDD...`). O arquivo importado fica marcado como IMPORTADO e **não é sobrescrito** pela tabela embutida em versões futuras do sistema, a menos que a embutida seja mais nova.
- O ambiente de desenvolvimento não acessa gov.br; por isso não há download automático.

## Tela (Consultas > Tabelas de Referência)
- **ICMS**: matriz 27 × 27 na data escolhida (linha = origem, coluna = destino, diagonal = interna, "20+2" = alíquota + FCP), com seletor nacional/importada. Clique na célula: fundamentação e, na diagonal, o histórico da UF.
- **ISS**: filtro por UF, nome ou código IBGE; lista de municípios; tabela com os 333 códigos do município (código, cTribNac, subitem da LC 116, alíquota, vigência, situação); filtro por serviço; detalhe com a mensagem da consulta.
- **Fontes**: os 7 conjuntos, com origem (embutido/importado).

## Banco e desempenho
- Esquema de referência v2 (migração automática da v0.1.3; dados mantidos). ISS em tabela `WITHOUT ROWID` com chaves inteiras: banco de referência com cerca de 58 MB.
- Primeira abertura: carga em segundo plano (~11 s neste ambiente), com andamento na barra de status; a tela de tabelas espera a carga terminar. Aberturas seguintes: < 0,2 s (hash igual).
- JAR com cerca de 9 MB a mais (TXT oficial compactado).

## Código
| Classe | Papel |
|---|---|
| `ref/IssImporter` | Leitura em streaming do formato oficial, conversões e resumo da carga |
| `ref/ReferenceLoader` | Novos tipos ICMS_INTER, ICMS_INTERNA, LC116, ISS; `importIssFile`; preservação do importado; andamento |
| `ref/ReferenceService` | `icmsInternal`, `icmsInterstate`, `icmsMatrix`, `icmsInternalHistory`, `searchMunicipalities`, `issRate`, `issRatesOf`, `lc116` |
| `ref/IcmsRate`, `IssRate`, `Municipality`, `Rates` | Modelo |
| `ui/ReferenceTablesFrame` | Abas ICMS e ISS; importação em segundo plano |
| `ui/MainFrame` | Carga das tabelas em segundo plano |
| `tools/ref/gerar_tabelas.py` | Gera `icms-*.tsv` (com conferência da planilha Fiscalia), `lc116.tsv` e copia o TXT do ISS |

## Pontos a validar
1. Alíquotas conflitantes do ISS (32 casos): manter indeterminado ou usar a mais recente?
2. Atos legais das alíquotas internas das UFs sem mudança recente; abrangência do FECP do RJ; adicional de 1% de SE.
3. Exceções da Res. Senado 13/2012 (não modeladas).
4. cTribNac → código do Portal (`+000`) com NFS-e real.

## Estado de validação
- 112 testes aprovados (102 anteriores + 10 novos em `IcmsIssTest`), compilado com `--release 8`. Mesmas limitações da v0.1.3 (substituto do JUnit; `mvn package` não executado; sem teste no Windows).
- Telas conferidas em ambiente gráfico virtual.
