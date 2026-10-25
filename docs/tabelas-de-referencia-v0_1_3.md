# Elevar Fiscal (anteriormente Facilita.sig) — Tabelas de referência (v0.1.3)

Versão de 23/10/2026. Implementa a Fase 0 (infraestrutura) e a Fase 1 (NCM e CST/CSOSN/Origem) do plano `base-de-dados-plano-de-implantacao`. Nesta versão as tabelas **só são consultadas**; nenhuma regra de conferência passou a usá-las ainda.

## Banco separado
- Arquivo `elevar-fiscal-ref.db`, na mesma pasta de dados do `elevar-fiscal.db` (`%APPDATA%\FacilitaSig` ou `~/.facilita-sig`; muda com `-Dfacilitasig.home`).
- Esquema próprio, versionado por `PRAGMA user_version` (v1), independente do banco das empresas (que continua no esquema v4).
- Não guarda nenhum dado de empresa. Pode ser apagado a qualquer momento: é recriado e recarregado na próxima abertura.
- Se o banco de referência não abrir, o sistema funciona normalmente; a tela de consulta mostra o motivo e a barra de status avisa.

## Como as tabelas chegam ao banco
- Os dados vão dentro do JAR, em `/ref`: `manifest.tsv` (um conjunto por linha), `ncm.tsv.gz`, `cst-planilha.tsv`, `cst-complemento.tsv`.
- Na abertura do sistema, cada conjunto é comparado pelo **SHA-256** do arquivo: igual → nada a fazer; diferente → recarga numa transação. Arquivo com erro → a versão anterior continua valendo e o erro aparece em Consultas > Tabelas de Referência > Fontes.
- Conjunto retirado do manifesto é apagado do banco.
- Os arquivos são gerados por `tools/ref/gerar_tabelas.py` a partir das planilhas da Base de Dados (rodar de novo quando a planilha mudar e recompilar).

## Registro de cada conjunto (`ref_dataset`)
Título, tipo, **grau da fonte** (OFICIAL, SECUNDARIA, CURADORIA), fonte, data da fonte, licença, cobertura, hash, número de linhas e data da carga.

| Conjunto | Grau | Linhas | Conteúdo |
|---|---|---|---|
| `ncm` | Oficial | 15.157 | NCM vigente em 21/10/2026 (Res. Gecex 926/2026): capítulos, posições, subposições, itens e os 10.516 códigos de 8 dígitos |
| `cst-planilha` | Secundária | 30 | Origem (0–8), CST do ICMS (Tabela B) e CSOSN, da planilha da Base de Dados, com erros de digitação corrigidos |
| `cst-complemento` | Curadoria | 51 | Códigos que faltavam: CST 02, 15, 53 e 61 do ICMS (monofásico de combustíveis, Ajuste SINIEF 1/2023); CST do IPI (14 códigos) e CST do PIS/Cofins (33 códigos), IN RFB 1.009/2010 |

## Consulta na data do fato
Toda consulta de código recebe a data e devolve uma de quatro situações:

| Situação | Quando |
|---|---|
| ENCONTRADO | O código existe e estava vigente na data |
| FORA_DE_VIGENCIA | O código existe, mas fora da vigência (ex.: CST 61 em 2022) |
| NAO_ENCONTRADO | O código não existe, e a tabela permite afirmar isso |
| SEM_COBERTURA | A tabela não permite afirmar (vira INDETERMINADO nas regras) |

Cobertura da NCM: a planilha só traz os códigos **vigentes em 21/10/2026**. Por isso:
- antes de 01/04/2022 (NCM 2022) a tabela não responde → SEM_COBERTURA;
- código ausente em nota anterior a 21/10/2026 → SEM_COBERTURA (pode ser código extinto antes da data da tabela);
- código ausente a partir de 21/10/2026 → NAO_ENCONTRADO.

CST: datas de início não informadas na planilha ficam "não registrado" (o código vale em qualquer data). Os códigos complementares têm início: 01/04/2010 (IPI e PIS/Cofins) e 01/05/2023 (monofásico, [validar]).

## Tela
**Consultas > Tabelas de Referência (NCM, CST, CSOSN)**: abre sem empresa selecionada. Três abas:
- **NCM**: pesquisa por código (com ou sem pontos, ou o começo dele) ou por palavras da descrição, sem diferenciar acentos e maiúsculas; a busca considera também as descrições dos níveis superiores ("cavalos reprodutores" acha 0101.21.00). Filtros "Só vigentes na data" e "Só 8 dígitos". Ao selecionar: hierarquia do capítulo ao código, situação na data, vigência e ato legal. Máximo de 500 resultados por pesquisa.
- **Origem, CST e CSOSN**: escolha da tabela (Origem, CST do ICMS, CSOSN, CST do IPI, CST do PIS/Cofins), lista com vigência e conjunto de origem; "Conferir código" mostra a situação na data. Ao selecionar: base legal, observação da curadoria e grau da fonte.
- **Fontes**: os conjuntos carregados, com grau, linhas, hash, data da carga e o resultado da última carga.

## Código
| Classe | Papel |
|---|---|
| `db/Database` | Aceita esquema próprio (`new Database(arquivo, migrações)`) |
| `db/DataDirectory.referenceDatabaseFile()` | Caminho do `elevar-fiscal-ref.db` |
| `ref/RefSchema` | Tabelas `ref_dataset`, `ref_ncm`, `ref_cst` |
| `ref/ReferenceLoader` | Lê o manifesto, compara hash e carrega |
| `ref/ReferenceService` | Consultas (`lookupNcm`, `searchNcm`, `ncmHierarchy`, `lookupCst`, `cstCodes`, `datasets`) |
| `ref/Lookup`, `LookupStatus`, `Validity`, `Dataset`, `Ncm`, `CstCode`, `CstTable`, `SourceGrade` | Modelo |
| `ui/ReferenceTablesFrame` | Tela de consulta |

## Pontos a validar
1. Data de início dos CST 02/15/53/61 (publicação do Ajuste SINIEF 1/2023 no DOU); usado 01/05/2023.
2. Redação das descrições do CST do PIS/Cofins conforme o texto atual da IN RFB 1.009/2010 (o CST 04 foi alterado após 2010).
3. Origem da planilha de CST (classificada como fonte secundária).
4. Ainda faltam: CST de IBS/CBS e `cClassTrib` (depende da tabela oficial), histórico de NCM extinta, TIPI.

## Estado de validação
- 100 testes aprovados (87 anteriores + 13 novos em `ReferenceServiceTest`: carga, idempotência, separação dos bancos, recarga quando o arquivo muda, arquivo com erro mantendo a versão anterior, as quatro situações da consulta, busca, hierarquia e os códigos acrescentados).
- Compilado com `javac --release 8` (bytecode Java 8) e testado em Java 21. Os testes rodaram com um substituto mínimo do JUnit, porque o Maven Central estava inacessível no ambiente de desenvolvimento; `mvn package` não foi executado.
- Tela aberta e conferida em ambiente gráfico virtual (Linux). Não testado no Windows.
