#!/usr/bin/env python3
"""
Gera os arquivos de referência embutidos no Elevar Fiscal (src/main/resources/ref/)
a partir das planilhas da Base de Dados. Rodar de novo sempre que a planilha mudar:

    python3 tools/ref/gerar_tabelas.py <pasta "Documentos Fiscais"> src/main/resources/ref \
        --icms Matriz_ICMS_2026_Fiscalia.xlsx --iss aliquotas-municipios-AAAAMMDD-extr1.zip

Saídas (UTF-8, separadas por TAB, sem aspas; campos vazios = sem valor):
  ncm.tsv.gz  codigo, codigo_formatado, descricao, vig_ini, vig_fim, ato_legal
  cst-planilha.tsv, cst-complemento.tsv
              tabela, codigo, descricao, vig_ini, vig_fim, base_legal, observacao

  icms-interestadual.tsv  uf_origem, uf_destino, tipo (GERAL|IMPORTADO), aliquota, vig_ini, vig_fim, base_legal, observacao
  icms-interna.tsv        uf, aliquota, fcp, vig_ini, vig_fim, base_legal, observacao
  lc116.tsv               subitem (00.00), descricao
  iss-aliquotas.txt.gz    o TXT oficial do Portal NFS-e, sem alteração (só compactado)

A planilha de CST traz Origem, CST do ICMS e CSOSN. Os códigos que faltavam (CST 02, 15, 53 e
61 do ICMS, CST do IPI e CST do PIS/Cofins) são transcritos aqui, da norma, e entram no
conjunto "cst-complemento" (grau CURADORIA). Requer pandas e openpyxl.
"""
import gzip
import os
import sys

import pandas as pd

TYPOS = [
    ("Nacional. Exceto as indicadas nos códigos 3, 4, 5, e 8", "Nacional, exceto as indicadas nos códigos 3, 4, 5 e 8"),
    ("Nascional", "Nacional"),
    ("Decretp-Lei", "Decreto-Lei"),
    ("Adquirida na mercado", "Adquirida no mercado"),
    ("indaicada", "indicada"),
    ("Substituião", "Substituição"),
    ("70 % (setenta", "70% (setenta"),
]

ICMS_BASE = "Convênio s/nº de 15/12/1970 (SINIEF), Anexo — Código de Situação Tributária"

# Códigos ausentes na planilha, transcritos da norma.
ICMS_MONOFASICO = [
    ("02", "Tributação monofásica própria sobre combustíveis"),
    ("15", "Tributação monofásica própria e com responsabilidade pela retenção sobre combustíveis"),
    ("53", "Tributação monofásica sobre combustíveis com recolhimento diferido"),
    ("61", "Tributação monofásica sobre combustíveis cobrada anteriormente"),
]
ICMS_MONOFASICO_BASE = "Ajuste SINIEF 1/2023 (acrescenta à Tabela B); LC 192/2022; Convênios ICMS 199/2022 e 15/2023"
ICMS_MONOFASICO_NOTA = ("[validar] vig_ini: início do ICMS monofásico do diesel, biodiesel e GLP (01/05/2023); "
                        "conferir a data de publicação do Ajuste SINIEF 1/2023 no DOU")

IPI = [
    ("00", "Entrada com recuperação de crédito"),
    ("01", "Entrada tributável com alíquota zero"),
    ("02", "Entrada isenta"),
    ("03", "Entrada não tributada"),
    ("04", "Entrada imune"),
    ("05", "Entrada com suspensão"),
    ("49", "Outras entradas"),
    ("50", "Saída tributada"),
    ("51", "Saída tributável com alíquota zero"),
    ("52", "Saída isenta"),
    ("53", "Saída não tributada"),
    ("54", "Saída imune"),
    ("55", "Saída com suspensão"),
    ("99", "Outras saídas"),
]
IPI_BASE = "IN RFB 1.009/2010, Anexo Único, Tabela I (efeitos a partir de 01/04/2010)"

PIS_COFINS = [
    ("01", "Operação tributável com alíquota básica"),
    ("02", "Operação tributável com alíquota diferenciada"),
    ("03", "Operação tributável com alíquota por unidade de medida de produto"),
    ("04", "Operação tributável monofásica — revenda a alíquota zero"),
    ("05", "Operação tributável por substituição tributária"),
    ("06", "Operação tributável a alíquota zero"),
    ("07", "Operação isenta da contribuição"),
    ("08", "Operação sem incidência da contribuição"),
    ("09", "Operação com suspensão da contribuição"),
    ("49", "Outras operações de saída"),
    ("50", "Operação com direito a crédito — vinculada exclusivamente a receita tributada no mercado interno"),
    ("51", "Operação com direito a crédito — vinculada exclusivamente a receita não tributada no mercado interno"),
    ("52", "Operação com direito a crédito — vinculada exclusivamente a receita de exportação"),
    ("53", "Operação com direito a crédito — vinculada a receitas tributadas e não tributadas no mercado interno"),
    ("54", "Operação com direito a crédito — vinculada a receitas tributadas no mercado interno e de exportação"),
    ("55", "Operação com direito a crédito — vinculada a receitas não tributadas no mercado interno e de exportação"),
    ("56", "Operação com direito a crédito — vinculada a receitas tributadas e não tributadas no mercado interno e de exportação"),
    ("60", "Crédito presumido — operação de aquisição vinculada exclusivamente a receita tributada no mercado interno"),
    ("61", "Crédito presumido — operação de aquisição vinculada exclusivamente a receita não tributada no mercado interno"),
    ("62", "Crédito presumido — operação de aquisição vinculada exclusivamente a receita de exportação"),
    ("63", "Crédito presumido — operação de aquisição vinculada a receitas tributadas e não tributadas no mercado interno"),
    ("64", "Crédito presumido — operação de aquisição vinculada a receitas tributadas no mercado interno e de exportação"),
    ("65", "Crédito presumido — operação de aquisição vinculada a receitas não tributadas no mercado interno e de exportação"),
    ("66", "Crédito presumido — operação de aquisição vinculada a receitas tributadas e não tributadas no mercado interno e de exportação"),
    ("67", "Crédito presumido — outras operações"),
    ("70", "Operação de aquisição sem direito a crédito"),
    ("71", "Operação de aquisição com isenção"),
    ("72", "Operação de aquisição com suspensão"),
    ("73", "Operação de aquisição a alíquota zero"),
    ("74", "Operação de aquisição sem incidência da contribuição"),
    ("75", "Operação de aquisição por substituição tributária"),
    ("98", "Outras operações de entrada"),
    ("99", "Outras operações"),
]
PIS_COFINS_BASE = "IN RFB 1.009/2010, Anexo Único, Tabelas II (PIS/Pasep) e III (Cofins) (efeitos a partir de 01/04/2010)"
PIS_COFINS_NOTA = "[validar redação] Tabelas II e III têm a mesma codificação; descrições normalizadas a partir da norma"


def clean(text):
    text = " ".join(str(text).split())
    for wrong, right in TYPOS:
        text = text.replace(wrong, right)
    return text


def check(value):
    value = "" if value is None else str(value)
    if "\t" in value or "\n" in value or "\r" in value:
        raise ValueError("Caractere inválido em: " + value)
    return value


def iso(br):
    """dd/mm/aaaa -> aaaa-mm-dd; 31/12/9999 vira vazio (sem fim de vigência)."""
    br = str(br).strip()
    if br in ("", "nan", "31/12/9999"):
        return ""
    d, m, y = br.split("/")
    return "%s-%s-%s" % (y, m.zfill(2), d.zfill(2))


def ncm(src, out_dir):
    path = os.path.join(src, "NFe", "Tabela_NCM_Desc_Concatenada_Vigente_20261021.xlsx")
    df = pd.read_excel(path, header=4, dtype=str).dropna(subset=["Código"])
    lines = []
    seen = set()
    for r in df.itertuples(index=False):
        shown = str(r[0]).strip()
        digits = shown.replace(".", "")
        if not digits.isdigit():
            raise ValueError("Código NCM inválido: " + shown)
        if digits in seen:
            raise ValueError("Código NCM repetido: " + shown)
        seen.add(digits)
        act = "%s %s/%s" % (str(r[5]).strip(), str(r[6]).strip(), str(r[7]).strip())
        row = [digits, shown, clean(r[1]), iso(r[3]), iso(r[4]), act]
        lines.append("\t".join(check(v) for v in row))
    with gzip.GzipFile(os.path.join(out_dir, "ncm.tsv.gz"), "wb", mtime=0) as f:
        f.write(("\n".join(lines) + "\n").encode("utf-8"))
    return len(lines)


def cst(src, out_dir):
    path = os.path.join(src, "C#U00f3digo_de_Situa#U00e7#U00e3o_Tribut#U00e1ria_CST.xlsx")
    df = pd.read_excel(path, header=None, dtype=str).dropna(how="all").dropna(how="all", axis=1)
    rows = []
    section = None
    for r in df.itertuples(index=False):
        code, desc = str(r[0]).strip(), str(r[1]).strip()
        if code in ("CST", "Cód."):
            section = {"Descrição": "ORIGEM", "Descrição (como tributar)": "CST_ICMS"}.get(desc, "CSOSN")
            continue
        if section == "ORIGEM":
            base = ICMS_BASE + " (Tabela A — origem)"
        elif section == "CST_ICMS":
            base = ICMS_BASE + " (Tabela B — tributação pelo ICMS)"
        else:
            base = ICMS_BASE + " (Tabela B — CSOSN, Simples Nacional)"
        rows.append([section, code if section != "CST_ICMS" else code.zfill(2), clean(desc), "", "", base, "cst-planilha", ""])
    for code, desc in ICMS_MONOFASICO:
        rows.append(["CST_ICMS", code, desc, "2023-05-01", "", ICMS_MONOFASICO_BASE, "cst-complemento", ICMS_MONOFASICO_NOTA])
    for code, desc in IPI:
        rows.append(["CST_IPI", code, desc, "2010-04-01", "", IPI_BASE, "cst-complemento", ""])
    for code, desc in PIS_COFINS:
        rows.append(["CST_PIS_COFINS", code, desc, "2010-04-01", "", PIS_COFINS_BASE, "cst-complemento", PIS_COFINS_NOTA])
    keys = set()
    for row in rows:
        key = (row[0], row[1])
        if key in keys:
            raise ValueError("CST repetido: %s %s" % key)
        keys.add(key)
    order = ["ORIGEM", "CST_ICMS", "CSOSN", "CST_IPI", "CST_PIS_COFINS"]
    rows.sort(key=lambda x: (order.index(x[0]), x[1]))
    for dataset in ("cst-planilha", "cst-complemento"):
        with open(os.path.join(out_dir, dataset + ".tsv"), "w", encoding="utf-8", newline="\n") as f:
            for row in rows:
                if row[6] == dataset:
                    f.write("\t".join(check(v) for v in row[:6] + row[7:]) + "\n")
    return len(rows)


# ---------------------------------------------------------------- ICMS

UFS = ["AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT", "PA", "PB", "PE", "PI",
       "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO"]
# Res. Senado 22/1989: origem Sul/Sudeste (exceto ES) para Norte, Nordeste, Centro-Oeste e ES = 7%; demais = 12%.
SUL_SUDESTE_SEM_ES = {"MG", "PR", "RJ", "RS", "SC", "SP"}
INTER_BASE = "Resolução do Senado Federal 22/1989 (7% e 12%; 7% desde 01/01/1990)"
IMPORT_BASE = "Resolução do Senado Federal 13/2012 (4% para importados; efeitos desde 01/01/2013)"
IMPORT_NOTE = ("Aplica-se à mercadoria com origem 1, 2, 3 ou 8 (CST, Tabela A). Exceções da própria resolução "
               "(sem similar nacional/CAMEX, gás natural importado, PPB) [validar]")

# Alíquota interna geral (modal), sem fundo de pobreza. Cobertura a partir de 01/01/2025.
# Valores conferidos entre a planilha Fiscalia, a imagem Tax Group e fontes web (out/2026).
GENERAL = "Lei do ICMS da UF (alíquota interna geral) [validar ato e data]"
INTERNA = [
    # uf, aliquota, fcp, vig_ini, vig_fim, base, observacao
    ("AC", "19", "", "", "", GENERAL, ""),
    ("AL", "19", "", "", "2026-03-31", GENERAL, "Substituída pela Lei AL 9.776/2025"),
    ("AL", "20.5", "", "2026-04-01", "", "Lei AL 9.776/2025", ""),
    ("AM", "20", "", "", "", GENERAL, ""),
    ("AP", "18", "", "", "", GENERAL, ""),
    ("BA", "20.5", "", "", "", GENERAL, ""),
    ("CE", "20", "", "", "", GENERAL, ""),
    ("DF", "20", "", "", "", GENERAL, ""),
    ("ES", "17", "", "", "", GENERAL, ""),
    ("GO", "19", "", "", "", GENERAL, ""),
    ("MA", "22", "", "", "2025-02-22", GENERAL, "Substituída pela Lei MA 12.426/2024"),
    ("MA", "23", "", "2025-02-23", "", "Lei MA 12.426/2024", ""),
    ("MG", "18", "", "", "", GENERAL, ""),
    ("MS", "17", "", "", "", GENERAL, ""),
    ("MT", "17", "", "", "", GENERAL, ""),
    ("PA", "19", "", "", "", GENERAL, ""),
    ("PB", "20", "", "", "", GENERAL, ""),
    ("PE", "20.5", "", "", "", GENERAL, ""),
    ("PI", "21", "", "", "2025-03-31", GENERAL, "Substituída pela Lei PI 8.558/2024"),
    ("PI", "22.5", "", "2025-04-01", "", "Lei PI 8.558/2024", ""),
    ("PR", "19.5", "", "", "", GENERAL, ""),
    ("RJ", "20", "2", "", "", GENERAL + "; FECP: Lei RJ 4.056/2002",
     "Alíquota total usual 22% (20% + 2% de FECP) [validar abrangência do FECP]"),
    ("RN", "18", "", "", "2025-03-19", GENERAL, "Substituída pela Lei RN 11.999/2024"),
    ("RN", "20", "", "2025-03-20", "", "Lei RN 11.999/2024",
     "Adicional de 2% só para produtos específicos (refrigerantes, isotônicos, energéticos, cosméticos)"),
    ("RO", "19.5", "", "", "", GENERAL, ""),
    ("RR", "20", "", "", "", GENERAL, ""),
    ("RS", "17", "", "", "", GENERAL, ""),
    ("SC", "17", "", "", "", GENERAL, ""),
    ("SE", "19", "", "", "", GENERAL,
     "Fontes citam adicional de 1% (fundo de pobreza), somando 20%; não confirmado se é geral [validar]"),
    ("SP", "18", "", "", "", GENERAL, ""),
    ("TO", "20", "", "", "", GENERAL, ""),
]


def num(text):
    return ("%.2f" % float(str(text).replace(",", "."))).rstrip("0").rstrip(".")


def icms(matrix_path, out_dir):
    """Gera as tabelas de ICMS e confere com a matriz da planilha Fiscalia."""
    rows = []
    for o in UFS:
        for d in UFS:
            if o == d:
                continue
            rate = "7" if o in SUL_SUDESTE_SEM_ES and d not in SUL_SUDESTE_SEM_ES else "12"
            rows.append([o, d, "GERAL", rate, "1990-01-01", "", INTER_BASE, ""])
            rows.append([o, d, "IMPORTADO", "4", "2013-01-01", "", IMPORT_BASE, IMPORT_NOTE])
    if matrix_path:
        m = pd.read_excel(matrix_path, index_col=0, dtype=str).dropna(how="all")
        for r in rows:
            if r[2] == "GERAL" and num(m.loc[r[0], r[1]]) != r[3]:
                raise ValueError("Matriz Fiscalia diverge da Res. 22/89 em %s→%s" % (r[0], r[1]))
        current = {}
        for uf, rate, fcp, ini, fim, base, obs in INTERNA:
            if not fim:
                current[uf] = rate
        diverge = sorted(uf for uf in UFS if num(m.loc[uf, uf]) != num(current[uf]))
        print("Alíquota interna diferente da planilha Fiscalia (corrigidas):", diverge)
        if diverge != ["MA", "PI"]:
            raise ValueError("Divergências inesperadas com a planilha Fiscalia: %s" % diverge)
    with open(os.path.join(out_dir, "icms-interestadual.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write("\t".join(check(v) for v in r) + "\n")
    if sorted(set(r[0] for r in INTERNA)) != sorted(UFS):
        raise ValueError("Tabela interna incompleta")
    with open(os.path.join(out_dir, "icms-interna.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for r in INTERNA:
            f.write("\t".join(check(v) for v in r) + "\n")
    return len(rows), len(INTERNA)


# ---------------------------------------------------------------- ISS

def lc116(src, out_dir):
    path = os.path.join(src, "NFSe", "ISS - RETENSERV.xlsx")
    df = pd.read_excel(path, sheet_name="CTS", header=None, dtype=str).dropna(how="all").dropna(how="all", axis=1)
    out = []
    for code, desc in df.itertuples(index=False):
        code = str(code).strip()
        if code == "Código":
            continue
        item, sub = code.split(".")
        desc = clean(desc)
        if desc.startswith("- "):
            desc = desc[2:]
        out.append("%02d.%02d\t%s" % (int(item), int(sub), check(desc)))
    with open(os.path.join(out_dir, "lc116.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    return len(out)


def iss(zip_path, out_dir):
    """Copia o TXT consolidado do ZIP oficial, compactado de forma determinística."""
    import hashlib
    import zipfile
    with open(zip_path, "rb") as f:
        print("SHA-256 do ZIP oficial:", hashlib.sha256(f.read()).hexdigest())
    with zipfile.ZipFile(zip_path) as z:
        names = [n for n in z.namelist() if n.lower().endswith(".txt")]
        if len(names) != 1:
            raise ValueError("Esperado um único TXT no ZIP: %s" % names)
        data = z.read(names[0])
    with gzip.GzipFile(os.path.join(out_dir, "iss-aliquotas.txt.gz"), "wb", mtime=0) as f:
        f.write(data)
    return names[0], data.count(b"\n")


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("fonte")
    ap.add_argument("saida")
    ap.add_argument("--icms", help="planilha Matriz_ICMS_2026_Fiscalia.xlsx (conferência)")
    ap.add_argument("--iss", help="ZIP oficial do Portal NFS-e (aliquotas-municipios-*.zip)")
    a = ap.parse_args()
    print("NCM:", ncm(a.fonte, a.saida))
    print("CST:", cst(a.fonte, a.saida))
    print("ICMS (interestadual, interna):", icms(a.icms, a.saida))
    print("LC 116:", lc116(a.fonte, a.saida))
    if a.iss:
        print("ISS:", iss(a.iss, a.saida))
