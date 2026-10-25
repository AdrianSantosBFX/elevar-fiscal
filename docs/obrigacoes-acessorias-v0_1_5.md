# Elevar Fiscal (anteriormente Facilita.sig) — Obrigações acessórias do Lucro Presumido e do Lucro Real (v0.1.5)

Versão de 23/10/2026. Catálogo das obrigações acessórias do LP e do LR, com aplicação à empresa em uso e cálculo de vencimentos, no menu **Obrigações**. Complementa `lp-07-obrigacoes-acessorias-e-guias` e `regras-tributarias-por-regime` (pesquisa anterior), agora com fontes de 2026.

## Onde está
| Menu | Tela | Empresa |
|---|---|---|
| Obrigações > **Central de Obrigações Acessórias** (etapa 6 da rotina) | Aba *Obrigações da competência*: cada obrigação da empresa na competência (mensais) e no ano-calendário (anuais), com situação, vencimento, esfera e motivo. Aba *Vencimentos do mês*: o que vence no mês e de qual competência | Exige empresa; como toda etapa, abre depois das anteriores ou com "Modo livre" |
| Obrigações > **Catálogo de Obrigações (Lucro Presumido e Real)** | Aba *Catálogo* (filtros por regime, esfera, extintas), aba *Calendário de vencimentos* (mês × regime × UF) e aba *Feriados nacionais* | Não exige |
| Consultas > **Calendário de Obrigações** | A mesma tela, aberta no calendário | Não exige |

## Catálogo (20 linhas)
| Obrigação | Esfera | LP | LR | Prazo (regra) | Notas sustentam |
|---|---|---|---|---|---|
| EFD-Contribuições | Federal | Obrigatória | Obrigatória | 10º dia útil do 2º mês seguinte (IN RFB 1.252/2012, art. 7º). **Até a competência 12/2026** (PIS/Cofins extintos em 2027) | Parcial |
| DCTFWeb (com MIT) | Federal | Obrigatória | Obrigatória | Último dia útil do mês seguinte (IN RFB 2.005/2021; IN RFB 2.237/2024: IRPJ, CSLL, PIS, Cofins, IPI pelo MIT desde 01/2025) [dia a validar] | Parcial |
| DCTF Mensal | Federal | — | — | **Extinta** desde os fatos de 01/2025 (substituída pela DCTFWeb) | — |
| EFD-Reinf | Federal | Condicional | Condicional | Dia 15 do mês seguinte, antecipa (IN RFB 2.043/2021) | Parcial |
| eSocial | Federal | Condicional | Condicional | Dia 15 do mês seguinte, antecipa | Não |
| DCTFWeb do 13º | Federal | Condicional | Condicional | 20/12 do próprio ano | Não |
| ECD | Federal | Condicional (distribuição de lucros acima da base presumida) | Obrigatória | Último dia útil de junho do ano seguinte (IN RFB 2.003/2021, art. 5º; 30/06/2026 para 2025) | Não |
| ECF (e-Lalur/e-Lacs) | Federal | Obrigatória | Obrigatória | Último dia útil de julho do ano seguinte (IN RFB 2.004/2021, art. 3º; 31/07/2026 para 2025) | Parcial |
| DIRBI | Federal | Condicional | Condicional | Dia 20 do 2º mês seguinte (IN RFB 2.198/2024; 173 benefícios desde 01/2026) | Parcial (CST PIS/Cofins 04, 06–09) |
| DME | Federal | Condicional | Condicional | Último dia útil do mês seguinte (IN RFB 1.761/2017; espécie ≥ R$ 30 mil) | Parcial (tPag 01) |
| DIMOB | Federal | Condicional | Condicional | Último dia útil de fevereiro | Não |
| DMED | Federal | Condicional | Condicional | Último dia útil de fevereiro | Não |
| Comprovante de rendimentos | Federal | Condicional | Condicional | Último dia útil de fevereiro | Parcial |
| DIRF | Federal | — | — | **Extinta** a partir do ano-calendário 2025 (eSocial + EFD-Reinf) | — |
| CBE (Banco Central) | Federal | Condicional | Condicional | 5 de abril do ano seguinte [validar] | Não |
| EFD ICMS/IPI — regra geral | Estadual | Com IE | Com IE | Prazo da UF (Ajuste SINIEF 2/2009) — não calculado | Sim |
| EFD ICMS/IPI — MG | Estadual | Com IE | Com IE | Dia 15 do mês seguinte (RICMS/MG 2023, Anexo V, Parte 2, art. 12) | Sim |
| DAPI 1 — MG | Estadual | Com IE | Com IE | **Dispensa ampla desde 07/2026** (Portaria SRE 285/2026); devida até a competência 06/2026 | Sim |
| GIA-ST | Estadual | Condicional | Condicional | Dia 10 do mês seguinte, por UF de destino [validar] | Parcial |
| Declaração municipal de serviços | Municipal | Com IM: depende do município | Idem | Prazo do município — não calculado | Sim |

Fora do catálogo, por serem de setores que não são o foco (podem entrar depois): e-Financeira (instituições financeiras), DOI (cartórios), DeSTDA e PGDAS-D (Simples Nacional).

## Como a empresa é avaliada
- **Regime** na competência, pelo cadastro (períodos de regime). Regime fora de LP/LR → "Não se aplica" (o catálogo é só desses regimes).
- **Inscrição estadual** ativa (INSCRITA) → obrigações estaduais. Linha da UF (ex.: MG) substitui a regra geral; empresa com IE em outra UF sem linha própria usa a regra geral (prazo não calculado).
- **Inscrição municipal** → declaração municipal "A confirmar" (depende da lei do município).
- **Vigência**: obrigação extinta ou ainda inexistente na competência → "Fora de vigência", com a substituta.
- Fatos que o cadastro não mostra (folha, retenções, benefícios fiscais, recebimento em espécie, imóveis, saúde, bens no exterior) → **"A confirmar"**, nunca "Não se aplica" (regra de ouro do `lp-00`).
- Exemplo (Sinteprol, LR, IE MG, IM 201662, competência 09/2026): 5 obrigatórias (EFD-Contribuições 16/11/2026, DCTFWeb 30/10/2026, EFD ICMS/IPI MG 15/10/2026, ECD 30/06/2027, ECF 30/07/2027) e 11 a confirmar; DAPI fora de vigência.

## Prazos
- Regras na coluna `prazo_regra`: `DU:m:n` (n-ésimo dia útil), `DIA:m:d:A|P|F` (dia fixo; antecipa, prorroga ou mantém), `UDU:m` (último dia útil), `UF`/`MUN` (não calculado). Mês-base = competência; nas anuais, dezembro do ano-calendário.
- Dia útil: segunda a sexta, exceto feriados nacionais (incluindo 20/11 desde 2024, Lei 14.759/2023) e Carnaval/Corpus Christi (pontos facultativos sem expediente bancário) [validar caso a caso]. Páscoa calculada (algoritmo de Meeus). **Feriados estaduais e municipais não entram.**

## Dados e código
- Catálogo mantido em `tools/ref/obrigacoes.py` → `src/main/resources/ref/obrigacoes.tsv` → banco de referência (conjunto `obrigacoes`, grau **Curadoria**, esquema de referência v3, tabela `ref_obrigacao`). Para mudar um prazo ou incluir obrigação: editar o .py, rodar e recompilar; a carga atualiza sozinha pelo hash.
- `ref/Obligation` (modelo), `oa/BusinessCalendar`, `oa/DeadlineRule`, `oa/ObligationService` (avaliação, vencimentos do mês, calendário genérico), `oa/Applicability`, `ui/ObligationsCentralFrame`, `ui/ObligationsCatalogFrame`.
- Testes: `ObligationsTest` (9 casos: Páscoa e feriados, regras de prazo com datas conferidas, catálogo, Sinteprol LR/MG, fim da EFD-Contribuições, ECD condicional no LP, regime fora do catálogo, UF sem linha própria, vencimentos do mês). Total: 123 testes aprovados.

## Pontos a validar
1. Dia da DCTFWeb: último dia útil (CRC-CE, ago/2026) × dia 25 (fórum, IN 2.237/2024).
2. DIRBI: antecipação quando o dia 20 não é útil.
3. Prazo da DAPI em MG até 06/2026 e as vedações à dispensa.
4. GIA-ST: dia e ajuste; dispensa para quem entrega EFD em cada UF.
5. CBE, DIMOB, DMED, comprovante de rendimentos, eSocial: atos normativos atuais.
6. Prazos de EFD ICMS/IPI das outras UFs (hoje só MG tem linha própria).
7. Obrigações substitutas da EFD-Contribuições para CBS/IBS em 2027 (a regulamentar).
8. Dispensas da EFD-Contribuições (art. 5º da IN RFB 1.252/2012).
