package br.com.facilitasig.ref;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Lê o arquivo oficial de alíquotas de ISSQN do Portal NFS-e (gov.br/nfse, "Alíquotas") e grava no banco
 * de referência, em streaming (1,9 milhão de linhas; não cabe em memória como texto).
 *
 * <p>Formato (TXT consolidado ou CSV por UF, UTF-8 com BOM, separador ";"):
 * {@code codigo_ibge;uf;nome_municipio;codigo_servico;incidencia;aliquota;dt_ini;dt_fim}, ex.:
 * {@code 3101904;MG;Alpinópolis;01.01.01.000;01.01.01.000;2;2025-12-23T00:00:00;}.
 *
 * <p>Linhas com fim de vigência anterior ao início são ignoradas e contadas; alíquota vazia é gravada como
 * nula (o Portal publicou a vigência sem alíquota: a consulta responde SEM_COBERTURA); alíquotas fora de 2% a 5%
 * (LC 116/2003, art. 8º-A e art. 8º, II) são gravadas e contadas, para a conferência apontar.
 */
final class IssImporter {

    static final String HEADER = "codigo_ibge;uf;nome_municipio;codigo_servico;incidencia;aliquota;dt_ini;dt_fim";

    /** Andamento da carga (para a barra de status). */
    interface Progress {
        void rows(int count);
    }

    /** Resumo da carga, gravado nas observações do conjunto. */
    static final class Stats {
        int rows;
        int invalidPeriod;
        int duplicated;
        int outOfRange;
        int emptyRate;
        int municipalities;
        int services;
        Set<String> ufs = new HashSet<String>();

        String summary() {
            return rows + " alíquotas de " + municipalities + " municípios (" + ufs.size() + " UFs) e " + services
                    + " códigos de serviço. Ignoradas: " + invalidPeriod + " linhas com fim de vigência anterior ao início"
                    + (duplicated > 0 ? " e " + duplicated + " repetidas" : "") + ". "
                    + (emptyRate > 0 ? emptyRate + " vigências sem alíquota informada (campo vazio no Portal). " : "")
                    + (outOfRange > 0 ? outOfRange + " alíquotas fora de 2% a 5%. " : "Todas as alíquotas informadas entre 2% e 5%. ");
        }
    }

    private static final class Municipality {
        final String uf;
        final String name;

        Municipality(String uf, String name) {
            this.uf = uf;
            this.name = name;
        }
    }

    private final Connection c;
    private final String dataset;
    private final Progress progress;
    private final Stats stats = new Stats();
    private final Map<Integer, Municipality> municipalities = new TreeMap<Integer, Municipality>();
    private final Set<Integer> services = new HashSet<Integer>();
    private PreparedStatement insert;
    private int pending;

    IssImporter(Connection c, String dataset, Progress progress) {
        this.c = c;
        this.dataset = dataset;
        this.progress = progress;
    }

    /** Apaga as alíquotas e os municípios anteriores (a chamada roda dentro da transação da carga). */
    static void clear(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM ref_iss_aliquota");
        }
    }

    void begin() throws SQLException {
        clear(c);
        insert = c.prepareStatement("INSERT OR IGNORE INTO ref_iss_aliquota (ibge, servico, vig_ini, vig_fim, aliq_x100) "
                + "VALUES (?,?,?,?,?)");
    }

    /** Lê um arquivo (TXT ou um CSV de UF) no formato oficial. Pode ser chamado várias vezes (um CSV por UF). */
    void read(InputStream in, String name) throws IOException, SQLException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        String header = r.readLine();
        if (header != null && !header.isEmpty() && header.charAt(0) == '﻿') {
            header = header.substring(1);
        }
        if (header == null || !HEADER.equalsIgnoreCase(header.trim())) {
            throw new IllegalArgumentException(name + ": cabeçalho diferente do arquivo oficial do Portal NFS-e (esperado: "
                    + HEADER + ")");
        }
        String line;
        int n = 1;
        while ((line = r.readLine()) != null) {
            n++;
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] f = line.split(";", -1);
            if (f.length != 8) {
                throw new IllegalArgumentException(name + ", linha " + n + ": " + f.length + " campos (esperado 8)");
            }
            try {
                row(f);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(name + ", linha " + n + ": " + e.getMessage(), e);
            }
        }
    }

    private void row(String[] f) throws SQLException {
        int ibge = Integer.parseInt(f[0].trim());
        String uf = f[1].trim().toUpperCase();
        String name = f[2].trim();
        int service = serviceCode(f[3]);
        Integer rate = f[5].trim().isEmpty() ? null : rateX100(f[5]);
        int start = date(f[6]);
        Integer end = f[7].trim().isEmpty() ? null : date(f[7]);
        if (ibge < 1100000 || ibge > 5399999 || uf.length() != 2 || name.isEmpty()) {
            throw new IllegalArgumentException("município inválido: " + f[0] + " " + f[1] + " " + f[2]);
        }
        Municipality known = municipalities.get(ibge);
        if (known == null) {
            municipalities.put(ibge, new Municipality(uf, name));
        } else if (!known.uf.equals(uf)) {
            throw new IllegalArgumentException("município " + ibge + " aparece em duas UFs");
        }
        stats.ufs.add(uf);
        services.add(service);
        if (end != null && end < start) {
            stats.invalidPeriod++;
            return;
        }
        if (rate == null) {
            stats.emptyRate++;
        } else if (rate < 200 || rate > 500) {
            stats.outOfRange++;
        }
        insert.setInt(1, ibge);
        insert.setInt(2, service);
        insert.setInt(3, start);
        if (end == null) {
            insert.setNull(4, java.sql.Types.INTEGER);
        } else {
            insert.setInt(4, end);
        }
        if (rate == null) {
            insert.setNull(5, java.sql.Types.INTEGER);
        } else {
            insert.setInt(5, rate);
        }
        insert.addBatch();
        if (++pending == 20000) {
            flush();
        }
    }

    private void flush() throws SQLException {
        if (pending == 0) {
            return;
        }
        for (int count : insert.executeBatch()) {
            if (count == 0) {
                stats.duplicated++;
            } else {
                stats.rows++;
            }
        }
        pending = 0;
        if (progress != null) {
            progress.rows(stats.rows);
        }
    }

    Stats finish() throws SQLException {
        flush();
        insert.close();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ref_municipio (ibge, uf, name, search_text, dataset) "
                + "VALUES (?,?,?,?,?)")) {
            for (Map.Entry<Integer, Municipality> e : municipalities.entrySet()) {
                ps.setInt(1, e.getKey());
                ps.setString(2, e.getValue().uf);
                ps.setString(3, e.getValue().name);
                ps.setString(4, ReferenceService.searchKey(e.getValue().name));
                ps.setString(5, dataset);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        stats.municipalities = municipalities.size();
        stats.services = services.size();
        if (stats.rows == 0) {
            throw new IllegalArgumentException("Arquivo de ISS sem alíquotas válidas");
        }
        return stats;
    }

    // ------------------------------------------------------------------ conversões

    /** "01.01.01.000" → 10101000 (9 dígitos: item, subitem, desdobro nacional e complemento). */
    static int serviceCode(String text) {
        String d = text == null ? "" : text.replaceAll("\\D", "");
        if (d.length() != 9) {
            throw new IllegalArgumentException("código de serviço inválido: " + text);
        }
        return Integer.parseInt(d);
    }

    static String formatService(int code) {
        String d = String.format("%09d", code);
        return d.substring(0, 2) + "." + d.substring(2, 4) + "." + d.substring(4, 6) + "." + d.substring(6);
    }

    /** "2,5" → 250. */
    static int rateX100(String text) {
        java.math.BigDecimal v = new java.math.BigDecimal(text.trim().replace(',', '.'));
        return v.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).intValueExact();
    }

    /** "2026-01-01T00:00:00" → 20260101. */
    static int date(String text) {
        String t = text.trim();
        if (t.length() < 10 || t.charAt(4) != '-' || t.charAt(7) != '-') {
            throw new IllegalArgumentException("data inválida: " + text);
        }
        java.time.LocalDate d = java.time.LocalDate.parse(t.substring(0, 10));
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    static java.time.LocalDate toDate(Integer yyyymmdd) {
        return yyyymmdd == null ? null : java.time.LocalDate.of(yyyymmdd / 10000, (yyyymmdd / 100) % 100, yyyymmdd % 100);
    }

    static int toInt(java.time.LocalDate d) {
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }
}
