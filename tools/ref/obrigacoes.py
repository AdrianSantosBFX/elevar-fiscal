#!/usr/bin/env python3
"""
Catálogo de obrigações acessórias do Lucro Presumido (LP) e do Lucro Real (LR), mantido à mão.
Gera src/main/resources/ref/obrigacoes.tsv:

    python3 tools/ref/obrigacoes.py src/main/resources/ref

Colunas: codigo, uf ("*" = todas; UF específica sobrepõe a genérica), nome, esfera, regimes
(LP=<cond>;LR=<cond>, cond = SEMPRE | IE | IM | CONDICIONAL), periodicidade (MENSAL | ANUAL),
prazo_regra, prazo_texto, condicao, derivavel (SIM | PARCIAL | NAO), notas_sustentam, base_legal,
vig_ini, vig_fim (competências, aaaa-mm-dd), substituida_por, observacao.

Regras de prazo (mês-base = mês da competência; nas anuais, dezembro do ano-calendário):
  DU:m:n        n-ésimo dia útil do mês-base + m
  DIA:m:d:A|P|F dia d do mês-base + m; se não for dia útil: A antecipa, P prorroga, F mantém
  UDU:m         último dia útil do mês-base + m
  UF | MUN      prazo da UF ou do município (o sistema não calcula)
Dia útil = segunda a sexta, exceto feriados nacionais (BusinessCalendar). Feriados estaduais e municipais
não entram.
"""
import os
import sys

V = "[validar]"
ROWS = [
    # --------------------------------------------------------------- federais
    dict(codigo="EFD_CONTRIB", nome="EFD-Contribuições", esfera="FEDERAL",
         regimes="LP=SEMPRE;LR=SEMPRE", per="MENSAL", regra="DU:2:10",
         texto="10º dia útil do 2º mês seguinte ao da competência",
         cond="Toda PJ do Lucro Presumido ou Real; dispensas do art. 5º da IN RFB 1.252/2012 (ex.: imunes e isentas abaixo do limite, inativas) " + V,
         deriv="PARCIAL",
         sust="Receitas por CST de PIS/Cofins (blocos A e C), bases e valores, retenções na fonte (F600) das NF-e e NFS-e",
         base="IN RFB 1.252/2012, art. 7º (prazo); LP cumulativo (Lei 9.718/1998), LR não cumulativo (Leis 10.637/2002 e 10.833/2003)",
         ini="2013-01-01", fim="2026-12-31", subst="",
         obs="PIS e Cofins são extintos em 2027 (EC 132/2023; LC 214/2025): últimos fatos geradores em 12/2026; arquivos até 12/2026 seguem devidos e retificáveis (NT EFD-Contribuições 011/2026) " + V),
    dict(codigo="DCTFWEB", nome="DCTFWeb (com MIT)", esfera="FEDERAL",
         regimes="LP=SEMPRE;LR=SEMPRE", per="MENSAL", regra="UDU:1",
         texto="Último dia útil do mês seguinte ao da competência",
         cond="Toda PJ; sem fatos geradores no mês, entrega 'sem movimento'",
         deriv="PARCIAL",
         sust="Débitos de PIS, Cofins e IPI do mês; IRPJ e CSLL no último mês do trimestre; retenções federais das NFS-e tomadas",
         base="IN RFB 2.005/2021; IN RFB 2.237/2024 (IRPJ, CSLL, PIS, Cofins, IPI e demais tributos pelo MIT a partir de 01/2025); IN RFB 2.248/2025",
         ini="2025-01-01", fim="", subst="",
         obs="Substituiu a DCTF mensal a partir dos fatos de 01/2025. Fontes divergem sobre o dia (último dia útil × dia 25): adotado último dia útil (CRC-CE, ago/2026) " + V),
    dict(codigo="DCTF", nome="DCTF Mensal (convencional)", esfera="FEDERAL",
         regimes="LP=SEMPRE;LR=SEMPRE", per="MENSAL", regra="DU:2:15",
         texto="15º dia útil do 2º mês seguinte ao da competência",
         cond="Extinta para fatos geradores a partir de 01/2025; valem só as retificações de períodos anteriores",
         deriv="PARCIAL", sust="Débitos federais apurados das notas",
         base="IN RFB 2.005/2021 (revogada nessa parte pela IN RFB 2.237/2024)",
         ini="", fim="2024-12-31", subst="DCTFWEB", obs=""),
    dict(codigo="EFD_REINF", nome="EFD-Reinf", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="MENSAL", regra="DIA:1:15:A",
         texto="Dia 15 do mês seguinte; antecipa se não for dia útil",
         cond="PJ que contrata ou presta serviços com cessão de mão de obra (INSS retido), que retém IRRF, PIS, Cofins ou CSLL (série R-4000), optante pela CPRB, adquirente de produção rural, entre outros",
         deriv="PARCIAL",
         sust="R-2010 e R-2020 (serviços com retenção de INSS nas NFS-e), R-4020 (retenções federais pagas a PJ), R-2055 (produção rural)",
         base="IN RFB 2.043/2021",
         ini="2018-05-01", fim="", subst="", obs="Substituiu a DIRF (com o eSocial) a partir de 2025."),
    dict(codigo="ESOCIAL", nome="eSocial (eventos periódicos)", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="MENSAL", regra="DIA:1:15:A",
         texto="Dia 15 do mês seguinte; antecipa se não for dia útil",
         cond="Empregador; pagamento de pró-labore ou a contribuinte individual",
         deriv="NAO", sust="", base="Decreto 8.373/2014; leiautes e manual do eSocial " + V,
         ini="2018-01-01", fim="", subst="", obs="Folha, pró-labore e vínculos: fora das notas fiscais."),
    dict(codigo="DCTFWEB_13", nome="DCTFWeb anual do 13º salário", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="ANUAL", regra="DIA:0:20:A",
         texto="20 de dezembro do próprio ano-calendário; antecipa se não for dia útil",
         cond="Com folha de 13º salário no ano", deriv="NAO", sust="",
         base="IN RFB 2.005/2021 " + V, ini="2018-01-01", fim="", subst="", obs=""),
    dict(codigo="ECD", nome="ECD — Escrituração Contábil Digital", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=SEMPRE", per="ANUAL", regra="UDU:6",
         texto="Último dia útil de junho do ano seguinte (30/06/2026 para o ano-calendário 2025)",
         cond="Lucro Real: sempre. Lucro Presumido: quando distribuir lucros sem IRRF acima da base presumida diminuída dos tributos devidos, sem escrituração contábil que comprove lucro maior",
         deriv="NAO", sust="Só conferência cruzada de receita",
         base="IN RFB 2.003/2021, arts. 3º e 5º",
         ini="2008-01-01", fim="", subst="", obs="Situações especiais (extinção, cisão, fusão, incorporação) têm prazo próprio."),
    dict(codigo="ECF", nome="ECF — Escrituração Contábil Fiscal (inclui e-Lalur/e-Lacs)", esfera="FEDERAL",
         regimes="LP=SEMPRE;LR=SEMPRE", per="ANUAL", regra="UDU:7",
         texto="Último dia útil de julho do ano seguinte (31/07/2026 para o ano-calendário 2025)",
         cond="Toda PJ tributada pelo lucro real, presumido ou arbitrado; dispensadas: optantes do Simples e inativas",
         deriv="PARCIAL",
         sust="Lucro Presumido: bloco P (receita bruta por atividade, IRPJ e CSLL, retenções). Lucro Real: LALUR/LACS (bloco M) não deriva das notas",
         base="IN RFB 2.004/2021, art. 3º", ini="2014-01-01", fim="", subst="", obs=""),
    dict(codigo="DIRBI", nome="DIRBI — Declaração de Incentivos, Renúncias, Benefícios e Imunidades", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="MENSAL", regra="DIA:2:20:A",
         texto="Dia 20 do 2º mês seguinte ao da competência " + V,
         cond="PJ que usufruiu no mês algum benefício do Anexo Único da IN RFB 2.198/2024 (173 itens desde 01/2026; ex.: alíquota zero ou suspensão de PIS/Cofins, PAT, CPRB). Sem fruição no mês: não devida",
         deriv="PARCIAL",
         sust="Saídas e entradas com CST de PIS/Cofins 04, 06, 07, 08 ou 09 indicam possível fruição de benefício",
         base="Lei 14.973/2024, arts. 43 e 44; IN RFB 2.198/2024 (alterada pelas IN RFB 2.241/2024 e 2.294/2025)",
         ini="2024-01-01", fim="", subst="", obs="Multa de 0,5% a 1,5% da receita bruta, limitada a 30% do benefício (Lei 14.973/2024, art. 44)."),
    dict(codigo="DME", nome="DME — Declaração de Operações Liquidadas com Moeda em Espécie", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="MENSAL", regra="UDU:1",
         texto="Último dia útil do mês seguinte ao do recebimento",
         cond="Recebimento em espécie de R$ 30.000,00 ou mais de uma mesma pessoa no mês (venda de bens ou serviços, cessão de direitos)",
         deriv="PARCIAL", sust="Pagamento em dinheiro (tPag 01) nas NF-e, somado por cliente no mês",
         base="IN RFB 1.761/2017", ini="2018-01-01", fim="", subst="", obs=""),
    dict(codigo="DIMOB", nome="DIMOB — Declaração de Informações sobre Atividades Imobiliárias", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="ANUAL", regra="UDU:2",
         texto="Último dia útil de fevereiro do ano seguinte",
         cond="Construtoras e incorporadoras que vendem imóveis; imobiliárias e administradoras que intermedeiam ou administram locações",
         deriv="NAO", sust="", base="IN RFB 1.115/2010 " + V, ini="2010-01-01", fim="", subst="", obs=""),
    dict(codigo="DMED", nome="DMED — Declaração de Serviços Médicos e de Saúde", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="ANUAL", regra="UDU:2",
         texto="Último dia útil de fevereiro do ano seguinte",
         cond="PJ prestadora de serviços de saúde e operadoras de planos de saúde",
         deriv="NAO", sust="", base="IN RFB 985/2009 " + V, ini="2010-01-01", fim="", subst="", obs=""),
    dict(codigo="COMPROVANTE_RENDIMENTOS", nome="Comprovante de Rendimentos Pagos e de Retenção (aos beneficiários)", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="ANUAL", regra="UDU:2",
         texto="Último dia útil de fevereiro do ano seguinte " + V,
         cond="Quem pagou rendimentos com retenção de IRRF ou de PIS/Cofins/CSLL no ano (entrega ao beneficiário, não à RFB)",
         deriv="PARCIAL", sust="Retenções informadas nas NFS-e tomadas, por prestador",
         base="IN SRF 119/2000; IN RFB 459/2004, art. 4º " + V, ini="2000-01-01", fim="", subst="", obs=""),
    dict(codigo="DIRF", nome="DIRF — Declaração do Imposto sobre a Renda Retido na Fonte", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="ANUAL", regra="UDU:2",
         texto="Último dia útil de fevereiro do ano seguinte (última: ano-calendário 2024)",
         cond="Extinta a partir do ano-calendário 2025; informações passaram ao eSocial e à EFD-Reinf",
         deriv="PARCIAL", sust="Retenções das NFS-e tomadas",
         base="IN RFB 2.181/2024 " + V, ini="", fim="2024-12-31", subst="EFD_REINF", obs=""),
    dict(codigo="CBE", nome="CBE — Declaração de Capitais Brasileiros no Exterior (Banco Central)", esfera="FEDERAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="ANUAL", regra="DIA:4:5:F",
         texto="Até 5 de abril do ano seguinte (data-base 31/12) " + V,
         cond="Residente no Brasil com bens e valores no exterior de US$ 1 milhão ou mais em 31/12",
         deriv="NAO", sust="", base="Resolução BCB 279/2022 " + V, ini="2022-01-01", fim="", subst="", obs=""),
    # --------------------------------------------------------------- estaduais
    dict(codigo="EFD_ICMS_IPI", uf="*", nome="EFD ICMS/IPI (SPED Fiscal)", esfera="ESTADUAL",
         regimes="LP=IE;LR=IE", per="MENSAL", regra="UF",
         texto="Prazo da UF (regra do Ajuste SINIEF 2/2009: até o dia 25 do mês seguinte, salvo prazo próprio da UF) " + V,
         cond="Contribuinte do ICMS ou do IPI com inscrição estadual, um arquivo por estabelecimento",
         deriv="SIM",
         sust="Documentos e itens (C100, C170, C190), DIFAL, ST, ajustes (C197) e apuração (bloco E)",
         base="Convênio ICMS 143/2006; Ajuste SINIEF 2/2009",
         ini="2009-01-01", fim="", subst="",
         obs="Fora das notas: inventário (bloco H, anual na competência de fevereiro, ref. 31/12), CIAP (bloco G), produção e estoque (bloco K, industriais)."),
    dict(codigo="EFD_ICMS_IPI", uf="MG", nome="EFD ICMS/IPI (SPED Fiscal) — MG", esfera="ESTADUAL",
         regimes="LP=IE;LR=IE", per="MENSAL", regra="DIA:1:15:A",
         texto="Dia 15 do mês seguinte (RICMS/MG 2023) " + V + " ajuste quando não for dia útil",
         cond="Contribuinte do ICMS com inscrição estadual em MG (todos os que não são do Simples; Simples acima do sublimite)",
         deriv="SIM",
         sust="Documentos e itens (C100, C170, C190), DIFAL, ST, ajustes (C197) e apuração (bloco E); desde 07/2026 a apuração do ICMS em MG é feita só pela EFD",
         base="RICMS/MG 2023, Anexo V, Parte 2, art. 12 (redação do Decreto 48.646/2023)",
         ini="2009-01-01", fim="", subst="",
         obs="Feriados estaduais e municipais não entram no cálculo do dia útil."),
    dict(codigo="DAPI_MG", uf="MG", nome="DAPI 1 — Declaração de Apuração e Informação do ICMS (MG)", esfera="ESTADUAL",
         regimes="LP=IE;LR=IE", per="MENSAL", regra="UF",
         texto="Prazo do RICMS/MG " + V,
         cond="Contribuinte de MG não dispensado; dispensa ampla a partir de 01/07/2026, exceto vedações (escrituração centralizada, IE suspensa, regime especial com apuração inferior ao mês, IPI em IE única)",
         deriv="SIM", sust="Débitos, créditos e ST das NF-e",
         base="Portaria SRE 285/2026 (dispensa; revoga a Portaria SRE 177/2020)",
         ini="", fim="2026-06-30", subst="EFD_ICMS_IPI",
         obs="A partir das competências de 07/2026 a apuração do ICMS em MG é feita só pela EFD ICMS/IPI. Quem está nas vedações continua obrigado: confirmar " + V),
    dict(codigo="GIA_ST", uf="*", nome="GIA-ST — Guia Nacional de Informação e Apuração do ICMS-ST", esfera="ESTADUAL",
         regimes="LP=CONDICIONAL;LR=CONDICIONAL", per="MENSAL", regra="DIA:1:10:A",
         texto="Dia 10 do mês seguinte, uma por UF de destino (antecipado se não for dia útil) " + V,
         cond="Inscrito como substituto tributário em outra UF (vendas interestaduais com ICMS-ST retido para a UF de destino)",
         deriv="PARCIAL", sust="NF-e de saída interestadual com ICMS-ST (vBCST, vICMSST) por UF de destino",
         base="Ajuste SINIEF 04/1993 " + V, ini="", fim="", subst="",
         obs="Algumas UFs dispensam a GIA-ST de quem entrega a EFD ICMS/IPI " + V),
    # --------------------------------------------------------------- municipais
    dict(codigo="DECL_ISS", uf="*", nome="Declaração municipal de serviços (DES, DeISS ou equivalente)", esfera="MUNICIPAL",
         regimes="LP=IM;LR=IM", per="MENSAL", regra="MUN",
         texto="Prazo do município",
         cond="Prestador ou tomador de serviços, conforme a lei do município; municípios no padrão nacional da NFS-e podem dispensar",
         deriv="SIM", sust="NFS-e emitidas e tomadas, ISS próprio e retido",
         base="LC 116/2003 (art. 6º, responsabilidade do tomador); legislação municipal",
         ini="", fim="", subst="", obs="Depende do município: a confirmar no cadastro municipal da empresa."),
]

COLS = ["codigo", "uf", "nome", "esfera", "regimes", "per", "regra", "texto", "cond", "deriv", "sust", "base",
        "ini", "fim", "subst", "obs"]


def main(out_dir):
    seen = set()
    lines = []
    for r in ROWS:
        r.setdefault("uf", "*")
        key = (r["codigo"], r["uf"])
        if key in seen:
            raise ValueError("repetida: %s" % (key,))
        seen.add(key)
        vals = [r[c] for c in COLS]
        for v in vals:
            if "\t" in v or "\n" in v:
                raise ValueError(v)
        lines.append("\t".join(vals))
    with open(os.path.join(out_dir, "obrigacoes.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("Obrigações:", len(lines))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
