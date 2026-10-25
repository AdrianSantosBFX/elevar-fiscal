# Elevar Fiscal — v0.1.5

**Produto da Elevar Labs.** Esta versão corresponde à v0.1.5 do antigo **Facilita.sig**.
A alteração de nome não modifica regras fiscais, estruturas SQL ou o conteúdo dos dados do usuário.

### Compatibilidade com a instalação Facilita.sig

- **Antes de atualizar**, feche todas as instâncias do programa e faça backup dos bancos existentes.
- **Nomes atuais:** `elevar-fiscal.db` (empresas e documentos) e `elevar-fiscal-ref.db` (tabelas de referência).
- **Migração automática:** se só houver `facilita-sig.db` ou `facilita-sig-ref.db`, o programa copia o arquivo antigo para o novo nome na primeira abertura. Os arquivos originais ficam guardados como backup e **não são sobrescritos**. Se já existir um banco com nome novo, esse banco prevalece.
- **Pasta de dados inalterada por compatibilidade:** `%APPDATA%\FacilitaSig` (Windows) ou `~/.facilita-sig` (Linux/macOS). Renomeamos os arquivos de banco, mas não deslocamos automaticamente a pasta. Não use duas versões em paralelo no mesmo diretório.
- **Banco enviado pelo usuário:** o ZIP contém `banco-fornecido/elevar-fiscal.db`, que é uma cópia do banco recebido. O sistema NÃO o copia automaticamente para `%APPDATA%`; veja instruções abaixo.
- Também são aceitas as novas configurações `-Delevarfiscal.home=<pasta>` e `ELEVAR_FISCAL_HOME`, com prioridade sobre as antigas `-Dfacilitasig.home` e `FACILITASIG_HOME`. Só defina outro diretório se desejar apontar intencionalmente para uma cópia dos bancos.
- O pacote Java interno `br.com.facilitasig` foi mantido provisoriamente, por segurança e compatibilidade; a marca, o artefato e a interface agora são **Elevar Fiscal**.
- Os dois arquivos de inicialização (`executar.bat` e `executar.sh`) já apontam para `dist/elevar-fiscal.jar`.


Sistema desktop de conferência fiscal autônoma. Roda no próprio computador, em Java, sem servidor.
Nesta versão: cadastro de empresas, **empresa de trabalho** (menu Empresa, v0.1.2), importação de
NF-e e NFS-e nacional, verificações que usam apenas o XML da nota (v0.1.1) e **tabelas de referência**
NCM, Origem, CST e CSOSN em banco separado, com consulta (v0.1.3), **alíquotas de ICMS e de ISS** (v0.1.4) e **obrigações acessórias do Lucro Presumido e do Lucro Real** (v0.1.5). Apuração e obrigações
ainda são telas provisórias.

## Requisitos
- **Para executar:** Java 8 ou superior (JRE basta). O projeto é compatível com Java 8.
- **Para compilar:** JDK 8 ou superior e Maven 3.9 ou superior.

## Executar (sem compilar)
No repositório GitHub, os executáveis e bancos locais não são versionados. Para executar a partir do clone, siga a seção **Compilar com Maven**. As instruções abaixo se referem ao ZIP de distribuição.

O JAR já compilado está em `dist/elevar-fiscal.jar`.
No Windows, dê duplo clique em `executar.bat`, ou rode:
```
java -jar dist/elevar-fiscal.jar
```
O sistema funciona sem nenhuma biblioteca externa, com o visual Nimbus que acompanha o Java.

### Visual moderno (opcional, FlatLaf)
Baixe `flatlaf-3.5.4.jar` (grupo `com.formdev`, no Maven Central) e coloque em `dist/lib/`. O JAR
detecta a biblioteca sozinho e passa a usar o visual FlatLaf.

## Compilar com Maven
```
mvn package          # compila, roda os testes e gera target/elevar-fiscal.jar (com o FlatLaf embutido)
java -jar target/elevar-fiscal.jar
```
Para abrir no IDE (IntelliJ, Eclipse, VS Code), importe como projeto Maven e execute `br.com.facilitasig.App`.

## Organização
| Pacote | Conteúdo |
|---|---|
| `br.com.facilitasig` | `App` (entrada), `AppInfo` (nome, versão, hipótese de pesquisa) |
| `br.com.facilitasig.core` | Modelo sem dependência de interface: `WorkArea`, `MenuGroup`, `Feature`, `Step`, `ApuracaoSession` |
| `br.com.facilitasig.ui` | Swing: `MainFrame`, `StepNavigatorPanel`, `PlaceholderFrame`, `StatusBar`, `Icons`, `AboutDialog` |

Interface: Swing (FlatLaf opcional), janelas internas (MDI) no centro, como no Questor.

## Conceitos
- **Área de Trabalho** (`WorkArea`): conjunto de menus, botões de acesso rápido e, opcionalmente, etapas.
  Hoje existe uma: **Apuração Fiscal**. O menu "Áreas de Trabalho" lista todas as registradas em `WorkAreas`.
- **Funcionalidade** (`Feature`): uma tela. Nesta versão todas abrem uma janela provisória que descreve o que farão.
- **Rotina linear** (`Step` + `ApuracaoSession`): 7 etapas em ordem — Empresa e período, Parâmetros fiscais,
  Documentos do período, Conferência, Apuração de impostos, Obrigações acessórias, Relatórios e fechamento.
  Uma etapa só abre depois de concluir a anterior. O "Modo livre" no roteiro desliga a trava (para desenvolvimento).
  O estado fica só em memória.

## Como acrescentar uma funcionalidade
1. Em `ApuracaoFiscalArea`, crie a `Feature` com `f(id, título, descrição, símbolo, cor)`.
2. Inclua-a em um `MenuGroup` (e, se for a tela principal de uma etapa, no `Step` correspondente).
3. Troque a janela provisória pela tela real quando ela existir (ponto único: `MainFrame.openFeature`).

## Como acrescentar uma Área de Trabalho
Crie uma classe parecida com `ApuracaoFiscalArea` que devolva um `WorkArea` e inclua-a em `WorkAreas.all()`.

## Atalhos
`F1` Sobre · `Ctrl+Q` Sair. A caixa de busca da barra de ferramentas abre funcionalidades por nome ou descrição.

## Cadastro de empresas e banco interno (SQLite)

- Menu **Cadastros > Empresas e Estabelecimentos**: cadastro padrão de empresa (Cartão CNPJ + dados do e-Contínuo), em 5 abas. Um cadastro = um CNPJ (matriz ou filial).
- **Regra "NÃO POSSUI"**: nenhum campo opcional fica em branco; se a empresa não tem o dado, ele é gravado como `NÃO POSSUI` (no banco, coluna `*_status` = `NAO_POSSUI`, com CHECK que impede vazio/NULL).
- O botão **Cadastrar exemplo...** carrega empresas reais usadas nos testes, a partir do Cartão CNPJ: L5 Participações Societárias (holding, Lucro Presumido) e Sinteprol Material de Construção (Alpinópolis/MG, Lucro Real; atacado e varejo de materiais de construção, pré-moldados, transporte de carga). Some quando todas já estão cadastradas. A **Sinteprol é cadastrada automaticamente** na primeira abertura da v0.1.4 (uma única vez: se for excluída, não volta) e vira a empresa em uso quando nenhuma estiver selecionada.
- Banco: arquivo único `elevar-fiscal.db` em `%APPDATA%\FacilitaSig` (ou `~/.facilita-sig`), fora da pasta do programa, para a atualização de versão não apagar os dados. A personalização aceita `-Delevarfiscal.home=...` ou `ELEVAR_FISCAL_HOME` (e também os parâmetros antigos).
- Mantenha `dist/lib/sqlite-jdbc-3.46.1.3.jar` ao lado do JAR (driver SQLite, Apache-2.0).
- Exportação do banco: prevista, ainda NÃO implementada.

## Importação de notas (NF-e e NFS-e nacional)

- **Movimentos > Importar Notas de Entrada / Importar Notas de Saída**: módulos separados. Escolha uma pasta (com ou sem subpastas) ou arquivos XML; cada nota é lida, validada (regras IMP-nnn) e gravada no banco.
- Abas: Todas, Conformes, Com alerta, Com erro, Duplicadas. Duplo clique abre a correção (data de entrada/saída, natureza interna, UF/município/IE do participante, NCM/CFOP/CST dos itens, ISS retido). O XML original nunca é alterado: a correção fica em camada separada e a nota é revalidada ao salvar.
- Empresa ativa obrigatória; nota de outro CNPJ vai para "Com erro". Eventos e resumos de NF-e são ignorados.
- Etapa 3 da rotina (Documentos) só conclui sem notas com erro. Ao concluir toda a rotina, o Roteiro de apuração some.
- NFS-e: leiaute nacional v1.00 (conferir com XMLs reais). Outros leiautes municipais: ainda não.

## Verificações do XML (v0.1.1)

- Ao importar ou revalidar, cada nota passa pelas regras IMP-nnn (importação) e pelas regras ERR-g.n, que releem o XML original. O número ERR é o item do catálogo de erros (ex.: ERR-5.1 = vProd diferente de quantidade × valor unitário).
- Cada achado mostra o que afeta (guias e obrigações) e a base legal. Lista completa: **Conferência > Regras Aplicadas e Base Legal**.
- Assinatura digital: o XML alterado depois de assinado vira erro. Não valida a cadeia ICP-Brasil.
- Eventos: arquivos de cancelamento e de carta de correção (`procEventoNFe`) são importados pelas telas de Entrada/Saída e aplicados à nota pela chave, mesmo que cheguem antes dela.
- NFS-e em leiaute municipal (ABRASF) é identificada (ERR-1.12) e fica fora da apuração.
- O banco é atualizado sozinho para o esquema 3 na primeira abertura; as notas já importadas são mantidas.
- Detalhes, pontos a validar e estado dos testes: `docs/verificacoes-xml-v0_1_1.md`.

## Empresa de trabalho (v0.1.2)

- Comece pelo menu **Empresa > Selecionar empresa...** (Ctrl+E). O sistema mostra só as notas, arquivos e apurações dessa empresa; a empresa em uso aparece no título e na barra de status.
- Matriz e filiais são empresas separadas (cada CNPJ, a sua).
- Telas com dados de empresa não abrem sem empresa selecionada. Trocar de empresa fecha essas telas e reinicia a rotina.
- Na importação, XML de outra empresa é descartado e aparece no resumo como "De outra empresa (descartados)".
- Na atualização para esta versão, notas de outras empresas gravadas antes (com IMP-003) são apagadas do banco; o sistema avisa quantas.
- Detalhes: `docs/separacao-por-empresa-v0_1_2.md`.


## Tabelas de referência (v0.1.3)

- Banco próprio `elevar-fiscal-ref.db`, ao lado do `elevar-fiscal.db`, só com tabelas oficiais ou curadas (nenhum dado de empresa). Pode ser apagado: é recriado na abertura.
- Tabelas embutidas no JAR (`/ref`) e carregadas na abertura quando o arquivo muda (hash SHA-256): **NCM** vigente (15.157 códigos, Res. Gecex 926/2026), **Origem, CST do ICMS e CSOSN** (planilha da Base de Dados) e os **códigos que faltavam**: CST 02/15/53/61 do ICMS, CST do IPI e CST do PIS/Cofins.
- Menu **Consultas > Tabelas de Referência (NCM, CST, CSOSN)**, sem empresa selecionada: pesquisa de NCM por código ou descrição, hierarquia, situação do código numa data; tabelas de CST com vigência e base legal; aba Fontes com grau da fonte, hash e resultado da carga.
- Consulta sempre na data do fato: ENCONTRADO, FORA_DE_VIGENCIA, NAO_ENCONTRADO ou SEM_COBERTURA (a tabela não permite afirmar; vira indeterminado).
- As regras de conferência ainda **não** usam as tabelas (próxima etapa).
- Atualizar uma tabela: `python3 tools/ref/gerar_tabelas.py "<pasta Documentos Fiscais>" src/main/resources/ref` e recompilar.
- Detalhes: `docs/tabelas-de-referencia-v0_1_3.md`.

## Alíquotas de ICMS e ISS (v0.1.4)

- **ICMS**: interestaduais (Res. Senado 22/1989 e 13/2012, 4% para importados) e internas gerais das 27 UFs desde 01/01/2025, com histórico das mudanças recentes (MA, PI, RN, AL) e FCP geral à parte (RJ). Aba ICMS: matriz origem × destino na data.
- **ISS**: tabela oficial do Portal NFS-e (gov.br/nfse — Alíquotas), 1,9 milhão de alíquotas de 5.341 municípios, com vigência. Aba ISS: município → alíquota de cada código de serviço (cTribNac) na data.
- Município fora da tabela (ex.: São Paulo, Rio de Janeiro, Brasília), serviço sem parametrização, vigência sem alíquota ou alíquotas conflitantes: resposta "indeterminado".
- **Atualizar o ISS**: baixe o ZIP novo no Portal (botão "Abrir página do Portal") e use "Importar arquivo do Portal NFS-e...". O arquivo importado não é sobrescrito pela tabela embutida, a menos que esta seja mais nova.
- Primeira abertura desta versão: a carga leva alguns segundos, em segundo plano (barra de status).
- As regras de conferência ainda não usam as tabelas. Detalhes: `docs/aliquotas-icms-iss-v0_1_4.md`.

## Obrigações acessórias (v0.1.5)

- **Obrigações > Catálogo de Obrigações (Lucro Presumido e Real)** (sem empresa): 20 obrigações federais, estaduais e municipais com quem está obrigado, prazo, base legal, vigência (inclusive as extintas: DCTF, DIRF, DAPI-MG) e o que as notas fiscais sustentam; calendário de vencimentos por mês, regime e UF; feriados nacionais.
- **Obrigações > Central de Obrigações Acessórias** (empresa em uso; etapa 6): situação de cada obrigação na competência (Obrigatória, A confirmar, Não se aplica, Fora de vigência), com o motivo e o vencimento; e os vencimentos do mês.
- O cadastro decide regime, inscrição estadual e municipal; o que depende de folha, retenções, benefícios etc. fica "A confirmar".
- Prazos com dias úteis nacionais; feriados estaduais e municipais não entram. Detalhes e pontos a validar: `docs/obrigacoes-acessorias-v0_1_5.md`.


## Banco de dados fornecido junto ao pacote

Em `banco-fornecido/elevar-fiscal.db` está o banco enviado para esta atualização, renomeado mas sem alteração do esquema ou dos registros. Este arquivo não é instalado automaticamente porque substituir um banco em uso poderia provocar perda de dados.

- Para recuperar esse banco em uma instalação nova, feche o programa e copie-o para `%APPDATA%\FacilitaSig\elevar-fiscal.db` no Windows (ou `~/.facilita-sig/elevar-fiscal.db` no Linux/macOS). Faça backup de qualquer arquivo existente antes de substituir.
- Para migrar uma instalação anterior, **não é necessário copiar o banco do ZIP**: basta manter `facilita-sig.db` no diretório de dados e abrir o Elevar Fiscal. A aplicação copiará o arquivo legado para o novo nome sem apagar o original.
- A abertura do app pode atualizar o esquema SQLite; não misture usos simultâneos das duas versões. O banco de referência também muda para `elevar-fiscal-ref.db`.
