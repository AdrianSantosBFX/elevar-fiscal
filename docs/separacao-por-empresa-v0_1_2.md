# Elevar Fiscal (anteriormente Facilita.sig) — Separação por empresa (v0.1.2)

Decisões do usuário em 22/10/2026:
- O trabalho começa escolhendo a empresa no menu **Empresa**. Notas, SPED, apuração e relatórios mostram apenas os dados dela.
- XML de outra empresa na importação: **descartado**, com aviso no resumo. Nada é gravado.
- **Cada CNPJ é totalmente separado**: matriz e filiais são empresas diferentes (comparação pelo CNPJ completo de 14 posições).
- Notas de outras empresas já gravadas por versões anteriores (com achado IMP-003): **apagadas** na atualização do banco.

## Menu Empresa
- Mostra a empresa em uso (CNPJ e razão social), que também aparece no título da janela e na barra de status.
- **Selecionar empresa...** (Ctrl+E): lista os CNPJs cadastrados com tipo (matriz/filial), município/UF e regime vigente.
- **Fechar empresa**: volta ao estado sem empresa.
- **Cadastro de empresas e estabelecimentos...**: o mesmo cadastro de Cadastros > Empresas e Estabelecimentos. O botão "Usar esta empresa" do cadastro faz o mesmo que selecionar.
- A última empresa usada é reaberta ao iniciar o sistema.

## Telas que exigem empresa
Toda funcionalidade é, por padrão, ligada a uma empresa (`Feature.companyScoped()`). Sem empresa selecionada, ao abrir uma delas o sistema pergunta se quer selecionar agora.
Funcionam sem empresa apenas (`withoutCompany()`): cadastro de empresas, Regras Aplicadas e Base Legal, calendário de obrigações, consulta à legislação, validador de chave, visualizador de XML e as duas telas de configuração.

## Troca de empresa
- Fecha as janelas com dados da empresa anterior (notas, SPED, apuração, relatórios). Cadastros gerais e utilitários continuam abertos.
- Reinicia a rotina de apuração: as etapas são sempre de uma empresa.

## Importação
- Pertencimento conferido **antes** de gravar (`DocValidator.belongsTo`):
  - Saída: emitente (prestador) é a empresa em uso.
  - Entrada: destinatário (tomador) é a empresa, ou nota própria de entrada (emitente = empresa e tpNF = 0).
- Nota de outra empresa: não é gravada; o resumo conta em "De outra empresa (descartados)" e lista o arquivo com o CNPJ encontrado.
- Eventos de NF-e: na Saída, evento cuja chave tem outro CNPJ emitente é descartado. Na Entrada o evento não informa o destinatário; ele é gravado na empresa em uso, mas só tem efeito sobre notas dela e nunca aparece como documento.
- Arquivos ilegíveis ou NFS-e em leiaute municipal continuam registrados (IMP-001/ERR-1.12) na empresa em uso, porque não dá para saber de quem são.
- A regra IMP-003 continua no validador como proteção, mas não deve mais aparecer.

## Banco de dados
- Esquema v4. Ao atualizar, apaga as notas com IMP-003 e as duplicatas delas, e avisa uma vez quantas foram apagadas.
- Acesso a nota individual só por empresa: `FiscalDocRepository.load(empresa, id)` devolve nulo para nota de outra empresa; `delete(empresa, id)` não apaga nota alheia; `FiscalService.review` recusa nota de outra empresa.
- Excluir o cadastro de uma empresa apaga as notas dela (já era assim, por chave estrangeira).

## SPED e demais módulos
Ainda são telas provisórias. Já ficam bloqueados sem empresa selecionada; ao serem implementados, devem gravar e ler sempre com o id da empresa em uso, como as notas.

## Estado de validação
- 87 testes aprovados (83 da v0.1.1, ajustados, e novos sobre descarte de nota alheia, filial como outra empresa, isolamento entre empresas, evento de outro emitente, migração v4 e funcionalidades que exigem empresa).
- Mesmas limitações da v0.1.1: compilado para Java 8 no JDK 21 sem `--release 8`, testes com substituto do JUnit, `mvn package` não executado, sem teste em Java 8 real nem no Windows.
- A janela de seleção de empresa e o menu não foram abertos em tela (o ambiente não tem suporte gráfico); a lógica por trás está coberta por testes.
