package br.com.facilitasig.ui;

import br.com.facilitasig.core.Feature;
import br.com.facilitasig.ref.CstCode;
import br.com.facilitasig.ref.CstTable;
import br.com.facilitasig.ref.Dataset;
import br.com.facilitasig.ref.Lookup;
import br.com.facilitasig.ref.Ncm;
import br.com.facilitasig.ref.ReferenceLoader;
import br.com.facilitasig.ref.ReferenceService;
import br.com.facilitasig.ref.Validity;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas > Tabelas de Referência: NCM, Origem/CST/CSOSN e as fontes de cada tabela.
 * Só leitura; os dados vêm do banco de referência, separado do banco das empresas.
 */
public class ReferenceTablesFrame extends JInternalFrame {

    private static final int NCM_LIMIT = 500;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReferenceService refs;
    private Runnable reloadSources = () -> { };
    private Runnable reloadMunicipalities = () -> { };

    public ReferenceTablesFrame(Feature feature, ReferenceService refs, String unavailableReason,
                                List<ReferenceLoader.Entry> lastLoad) {
        super(feature.title(), true, true, true, true);
        this.refs = refs;
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());
        if (refs == null) {
            JTextArea msg = area("O banco de referência não pôde ser aberto:\n" + unavailableReason
                    + "\n\nAs demais funções do sistema continuam disponíveis.");
            add(new JScrollPane(msg), BorderLayout.CENTER);
            setSize(700, 300);
            return;
        }
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("NCM", ncmTab());
        tabs.addTab("Origem, CST e CSOSN", cstTab());
        tabs.addTab("ICMS", icmsTab());
        tabs.addTab("ISS", issTab());
        tabs.addTab("Fontes", sourcesTab(lastLoad));
        add(tabs, BorderLayout.CENTER);
        JLabel foot = new JLabel("Banco de referência: " + refs.file().getAbsolutePath());
        foot.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(foot, BorderLayout.SOUTH);
        setSize(1040, 660);
    }

    // ------------------------------------------------------------------ NCM

    private JPanel ncmTab() {
        final JTextField query = new JTextField(20);
        final JTextField date = new JTextField(BR.format(LocalDate.now()), 9);
        final JCheckBox onlyValid = new JCheckBox("Só vigentes na data", true);
        final JCheckBox onlyItems = new JCheckBox("Só 8 dígitos (código da nota)", false);
        JButton search = new JButton("Pesquisar");
        final JLabel count = new JLabel(" ");

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Código ou descrição:"));
        bar.add(query);
        bar.add(new JLabel("Data:"));
        bar.add(date);
        bar.add(onlyValid);
        bar.add(onlyItems);
        bar.add(search);

        final DefaultTableModel model = readOnlyModel("Código", "Descrição", "Nível", "Início", "Fim", "Ato legal");
        final JTable table = table(model, 95, 430, 140, 75, 75, 140);
        final List<Ncm> shown = new ArrayList<Ncm>();
        final JTextArea detail = area("Digite um código (com ou sem pontos, ou o começo dele) ou palavras da descrição. "
                + "Ao selecionar um código, aparece a hierarquia e a situação na data informada.");

        Runnable run = () -> {
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            List<Ncm> found = refs.searchNcm(query.getText(), onlyValid.isSelected() ? day : null, onlyItems.isSelected(), NCM_LIMIT + 1);
            model.setRowCount(0);
            shown.clear();
            for (int i = 0; i < Math.min(found.size(), NCM_LIMIT); i++) {
                Ncm n = found.get(i);
                shown.add(n);
                model.addRow(new Object[]{n.formatted(), n.description(), n.levelName(), n.validity().startText(),
                        n.validity().endText(), n.legalAct()});
            }
            count.setText(found.size() > NCM_LIMIT ? "Mais de " + NCM_LIMIT + " resultados: refine a pesquisa."
                    : found.size() + " resultado(s).");
            if (found.isEmpty() && !query.getText().trim().isEmpty()) {
                Lookup<Ncm> l = refs.lookupNcm(query.getText(), day);
                detail.setText(l.status().label() + "\n" + l.message());
            }
        };
        search.addActionListener(e -> run.run());
        query.addActionListener(e -> run.run());
        date.addActionListener(e -> run.run());

        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (e.getValueIsAdjusting() || row < 0) {
                return;
            }
            Ncm n = shown.get(table.convertRowIndexToModel(row));
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String indent = "";
            for (Ncm h : refs.ncmHierarchy(n.code())) {
                sb.append(indent).append(h.formatted()).append("  ").append(h.description()).append('\n');
                indent += "   ";
            }
            Lookup<Ncm> l = refs.lookupNcm(n.code(), day);
            sb.append("\nSituação em ").append(BR.format(day)).append(": ").append(l.status().label()).append('\n')
                    .append(l.message()).append('\n');
            if (!n.isItemCode()) {
                sb.append("Atenção: ").append(n.levelName().toLowerCase())
                        .append(" não é código de item; na nota vai o código de 8 dígitos.\n");
            }
            sb.append("Vigência: ").append(n.validity()).append(" · Ato legal: ").append(n.legalAct());
            detail.setText(sb.toString());
            detail.setCaretPosition(0);
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(bar, BorderLayout.CENTER);
        count.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        top.add(count, BorderLayout.SOUTH);
        return withDetail(top, table, detail);
    }

    // ------------------------------------------------------------------ CST

    private JPanel cstTab() {
        final JComboBox<CstTable> which = new JComboBox<CstTable>(CstTable.values());
        final JTextField date = new JTextField(BR.format(LocalDate.now()), 9);
        final JCheckBox onlyValid = new JCheckBox("Só vigentes na data", false);
        final JTextField check = new JTextField(4);
        JButton checkButton = new JButton("Conferir código");

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Tabela:"));
        bar.add(which);
        bar.add(new JLabel("Data:"));
        bar.add(date);
        bar.add(onlyValid);
        bar.add(new JLabel("   Código:"));
        bar.add(check);
        bar.add(checkButton);

        final DefaultTableModel model = readOnlyModel("Código", "Descrição", "Início", "Fim", "Conjunto");
        final JTable table = table(model, 60, 540, 85, 75, 120);
        final List<CstCode> shown = new ArrayList<CstCode>();
        final JTextArea detail = area("Selecione um código para ver a base legal e a fonte.");

        Runnable reload = () -> {
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            CstTable t = (CstTable) which.getSelectedItem();
            model.setRowCount(0);
            shown.clear();
            for (CstCode c : refs.cstCodes(t, onlyValid.isSelected() ? day : null)) {
                shown.add(c);
                model.addRow(new Object[]{c.code(), c.description(), c.validity().startText(), c.validity().endText(), c.dataset()});
            }
            detail.setText(t.label() + " — campo da NF-e: " + t.xmlField() + ". " + shown.size() + " código(s).");
        };
        which.addActionListener(e -> reload.run());
        onlyValid.addActionListener(e -> reload.run());
        date.addActionListener(e -> reload.run());
        Runnable doCheck = () -> {
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            Lookup<CstCode> l = refs.lookupCst((CstTable) which.getSelectedItem(), check.getText(), day);
            detail.setText(l.status().label() + "\n" + l.message()
                    + (l.value() == null ? "" : "\n" + l.value().code() + " — " + l.value().description()));
        };
        checkButton.addActionListener(e -> doCheck.run());
        check.addActionListener(e -> doCheck.run());

        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (e.getValueIsAdjusting() || row < 0) {
                return;
            }
            CstCode c = shown.get(table.convertRowIndexToModel(row));
            Dataset d = refs.dataset(c.dataset());
            detail.setText(c.table().label() + " " + c.code() + " — " + c.description()
                    + "\nVigência: " + c.validity()
                    + "\nBase legal: " + c.legalBasis()
                    + (c.note().isEmpty() ? "" : "\nObservação: " + c.note())
                    + "\nFonte: " + (d == null ? c.dataset() : d.title() + " (" + d.grade().label() + ": " + d.grade().meaning() + ")"));
            detail.setCaretPosition(0);
        });
        reload.run();
        return withDetail(bar, table, detail);
    }

    // ------------------------------------------------------------------ fontes

    private JPanel sourcesTab(List<ReferenceLoader.Entry> lastLoad) {
        final DefaultTableModel model = readOnlyModel("Conjunto", "Tipo", "Grau", "Linhas", "Data da fonte", "Carregado em", "SHA-256");
        final JTable table = table(model, 120, 80, 85, 70, 90, 130, 140);
        final List<Dataset> sets = new ArrayList<Dataset>();
        reloadSources = () -> {
            sets.clear();
            sets.addAll(refs.datasets());
            model.setRowCount(0);
            for (Dataset d : sets) {
                model.addRow(new Object[]{d.code(), d.kind(), d.grade().label(), d.rowCount(), Validity.br(d.sourceDate()),
                        d.loadedAt() + (Dataset.ORIGIN_IMPORTED.equals(d.origin()) ? " (importado)" : ""),
                        d.sha256().substring(0, 16) + "…"});
            }
        };
        reloadSources.run();
        StringBuilder intro = new StringBuilder("Cada tabela registra de onde veio, o grau da fonte e o hash do arquivo carregado.");
        if (lastLoad != null && !lastLoad.isEmpty()) {
            intro.append("\n\nÚltima carga (abertura do sistema):");
            for (ReferenceLoader.Entry e : lastLoad) {
                intro.append("\n  ").append(e);
            }
        }
        final JTextArea detail = area(intro.toString());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (e.getValueIsAdjusting() || row < 0) {
                return;
            }
            Dataset d = sets.get(table.convertRowIndexToModel(row));
            detail.setText(d.title()
                    + "\nGrau: " + d.grade().label() + " — " + d.grade().meaning()
                    + "\nFonte: " + d.source()
                    + "\nLicença: " + (d.license().isEmpty() ? "não informada" : d.license())
                    + "\nCobertura a partir de: " + (d.coverageFrom() == null ? "sem limite" : Validity.br(d.coverageFrom()))
                    + "\nAusência de código conclusiva a partir de: "
                    + (d.conclusiveFrom() == null ? "sempre" : Validity.br(d.conclusiveFrom()))
                    + "\nArquivo: " + d.file() + " · SHA-256: " + d.sha256()
                    + (d.notes().isEmpty() ? "" : "\nObservação: " + d.notes()));
            detail.setCaretPosition(0);
        });
        return withDetail(null, table, detail);
    }

    // ------------------------------------------------------------------ ICMS

    static final String[] UF_LIST = {"AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT",
            "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO"};

    private JPanel icmsTab() {
        final JTextField date = new JTextField(BR.format(LocalDate.now()), 9);
        final JComboBox<String> goods = new JComboBox<String>(new String[]{
                "Mercadoria nacional (origem 0, 4, 5, 6, 7)", "Mercadoria importada (origem 1, 2, 3, 8) — 4%"});
        JButton refresh = new JButton("Atualizar");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Data:"));
        bar.add(date);
        bar.add(goods);
        bar.add(refresh);
        bar.add(new JLabel("   Linha = UF de origem; coluna = UF de destino; diagonal = alíquota interna geral."));

        String[] cols = new String[UF_LIST.length + 1];
        cols[0] = "Origem";
        System.arraycopy(UF_LIST, 0, cols, 1, UF_LIST.length);
        final DefaultTableModel model = readOnlyModel(cols);
        final JTable table = new JTable(model);
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        for (int i = 1; i < cols.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(34);
        }
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                java.awt.Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                setHorizontalAlignment(c == 0 ? LEFT : CENTER);
                boolean diagonal = c == r + 1;
                setFont(getFont().deriveFont(diagonal || c == 0 ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
                if (!sel) {
                    comp.setBackground(diagonal ? new java.awt.Color(0xD6E6F5) : c == 0 ? new java.awt.Color(0xEEF1F4) : java.awt.Color.WHITE);
                }
                return comp;
            }
        });
        final JTextArea detail = area("Clique numa célula para ver a fundamentação. Alíquotas internas: gerais (modais), "
                + "sem fundo de pobreza (FCP à parte quando geral). Fora da cobertura da tabela a célula fica vazia.");

        Runnable reload = () -> {
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            java.util.Map<String, String> cell = new java.util.HashMap<String, String>();
            for (br.com.facilitasig.ref.IcmsRate r : refs.icmsMatrix(day, goods.getSelectedIndex() == 1)) {
                cell.put(r.ufOrigin() + r.ufDestination(), r.isInternal() && r.fcp() != null
                        ? pctText(r.rate()) + "+" + pctText(r.fcp()) : pctText(r.rate()));
            }
            model.setRowCount(0);
            for (String o : UF_LIST) {
                Object[] row = new Object[UF_LIST.length + 1];
                row[0] = o;
                for (int i = 0; i < UF_LIST.length; i++) {
                    String v = cell.get(o + UF_LIST[i]);
                    row[i + 1] = v == null ? "" : v;
                }
                model.addRow(row);
            }
        };
        refresh.addActionListener(e -> reload.run());
        date.addActionListener(e -> reload.run());
        goods.addActionListener(e -> reload.run());
        javax.swing.event.ListSelectionListener show = e -> {
            int r = table.getSelectedRow();
            int c = table.getSelectedColumn();
            if (e.getValueIsAdjusting() || r < 0 || c < 1) {
                return;
            }
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            String o = UF_LIST[r];
            String d = UF_LIST[c - 1];
            Lookup<br.com.facilitasig.ref.IcmsRate> l = refs.icmsInterstate(o, d, goods.getSelectedIndex() == 1 ? "1" : "0", day);
            StringBuilder sb = new StringBuilder(l.status().label()).append('\n').append(l.message());
            if (l.value() != null) {
                sb.append("\nBase legal: ").append(l.value().legalBasis());
                if (!l.value().note().isEmpty()) {
                    sb.append("\nObservação: ").append(l.value().note());
                }
            }
            if (o.equals(d)) {
                sb.append("\n\nHistórico registrado de ").append(o).append(':');
                for (br.com.facilitasig.ref.IcmsRate h : refs.icmsInternalHistory(o)) {
                    sb.append("\n  ").append(h.display()).append(" — ").append(h.validity()).append(" — ").append(h.legalBasis());
                }
            }
            detail.setText(sb.toString());
            detail.setCaretPosition(0);
        };
        table.getSelectionModel().addListSelectionListener(show);
        table.getColumnModel().getSelectionModel().addListSelectionListener(show);
        reload.run();
        return withDetail(bar, table, detail);
    }

    private static String pctText(java.math.BigDecimal v) {
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // ------------------------------------------------------------------ ISS

    private static final String PORTAL_URL = "https://www.gov.br/nfse/pt-br/biblioteca/aliquotas";

    private JPanel issTab() {
        String[] ufs = new String[UF_LIST.length + 1];
        ufs[0] = "Todas";
        System.arraycopy(UF_LIST, 0, ufs, 1, UF_LIST.length);
        final JComboBox<String> uf = new JComboBox<String>(ufs);
        final JTextField name = new JTextField(16);
        final JTextField date = new JTextField(BR.format(LocalDate.now()), 9);
        final JTextField filter = new JTextField(10);
        JButton search = new JButton("Pesquisar");
        final JButton importButton = new JButton("Importar arquivo do Portal NFS-e...");
        JButton portal = new JButton("Abrir página do Portal");
        final JLabel status = new JLabel(" ");

        JPanel bar1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar1.add(new JLabel("UF:"));
        bar1.add(uf);
        bar1.add(new JLabel("Município (nome ou IBGE):"));
        bar1.add(name);
        bar1.add(new JLabel("Data:"));
        bar1.add(date);
        bar1.add(new JLabel("Serviço:"));
        bar1.add(filter);
        bar1.add(search);
        JPanel bar2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar2.add(importButton);
        bar2.add(portal);
        bar2.add(status);
        JPanel top = new JPanel(new BorderLayout());
        top.add(bar1, BorderLayout.NORTH);
        top.add(bar2, BorderLayout.SOUTH);

        final javax.swing.DefaultListModel<br.com.facilitasig.ref.Municipality> cities = new javax.swing.DefaultListModel<br.com.facilitasig.ref.Municipality>();
        final javax.swing.JList<br.com.facilitasig.ref.Municipality> cityList = new javax.swing.JList<br.com.facilitasig.ref.Municipality>(cities);
        cityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final DefaultTableModel model = readOnlyModel("Código", "cTribNac", "Subitem da LC 116", "Alíquota", "Início", "Fim", "Situação");
        final JTable table = table(model, 85, 60, 330, 60, 75, 75, 110);
        final List<Lookup<br.com.facilitasig.ref.IssRate>> shown = new ArrayList<Lookup<br.com.facilitasig.ref.IssRate>>();
        final JTextArea detail = area("Escolha um município. Fonte: Portal da NFS-e (alíquotas cadastradas pelos municípios). "
                + "Municípios com emissor próprio (ex.: São Paulo, Rio de Janeiro, Brasília) não constam: a consulta responde "
                + "\"indeterminado\". Para atualizar, baixe o ZIP mais novo no Portal e use \"Importar arquivo\".");

        Runnable findCities = () -> {
            cities.clear();
            String u = uf.getSelectedIndex() == 0 ? null : (String) uf.getSelectedItem();
            for (br.com.facilitasig.ref.Municipality m : refs.searchMunicipalities(name.getText(), u, 300)) {
                cities.addElement(m);
            }
            status.setText(cities.size() + (cities.size() == 300 ? "+ municípios (refine a pesquisa)" : " município(s)")
                    + " · " + String.format("%,d", refs.issCount()).replace(',', '.') + " alíquotas na tabela");
            if (cities.size() == 1) {
                cityList.setSelectedIndex(0);
            }
        };
        reloadMunicipalities = findCities;
        Runnable showRates = () -> {
            br.com.facilitasig.ref.Municipality m = cityList.getSelectedValue();
            model.setRowCount(0);
            shown.clear();
            if (m == null) {
                return;
            }
            LocalDate day = parseDate(date, detail);
            if (day == null) {
                return;
            }
            String f = br.com.facilitasig.ref.ReferenceService.searchKey(filter.getText());
            for (Lookup<br.com.facilitasig.ref.IssRate> l : refs.issRatesOf(m.ibge(), day)) {
                br.com.facilitasig.ref.IssRate r = l.value();
                String hay = r == null ? l.message() : r.serviceFormatted() + " " + r.cTribNac() + " " + r.subitemDescription();
                if (!f.isEmpty() && !br.com.facilitasig.ref.ReferenceService.searchKey(hay).contains(f)) {
                    continue;
                }
                shown.add(l);
                model.addRow(new Object[]{r == null ? serviceOf(l.message()) : r.serviceFormatted(), r == null ? "" : r.cTribNac(),
                        r == null ? "" : r.subitem() + " " + r.subitemDescription(),
                        l.found() && r != null ? r.rateText() : "—",
                        r == null ? "" : r.validity().startText(), r == null ? "" : r.validity().endText(),
                        l.found() ? "Vigente" : "Indeterminado"});
            }
            detail.setText(m + ": " + shown.size() + " código(s) de serviço em " + BR.format(day) + ".");
        };
        search.addActionListener(e -> findCities.run());
        name.addActionListener(e -> findCities.run());
        uf.addActionListener(e -> findCities.run());
        cityList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showRates.run();
            }
        });
        date.addActionListener(e -> showRates.run());
        filter.addActionListener(e -> showRates.run());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (e.getValueIsAdjusting() || row < 0) {
                return;
            }
            Lookup<br.com.facilitasig.ref.IssRate> l = shown.get(table.convertRowIndexToModel(row));
            StringBuilder sb = new StringBuilder(l.status().label()).append('\n').append(l.message());
            if (l.value() != null && !l.value().subitemDescription().isEmpty()) {
                sb.append("\nSubitem ").append(l.value().subitem()).append(" da LC 116/2003: ").append(l.value().subitemDescription());
            }
            if (l.value() != null && !l.value().withinLegalRange()) {
                sb.append("\nAtenção: fora da faixa de 2% a 5% da LC 116/2003.");
            }
            detail.setText(sb.toString());
            detail.setCaretPosition(0);
        });
        portal.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(PORTAL_URL));
            } catch (Exception ex) {
                detail.setText("Abra no navegador: " + PORTAL_URL);
            }
        });
        importButton.addActionListener(e -> {
            javax.swing.JFileChooser ch = new javax.swing.JFileChooser();
            ch.setDialogTitle("Arquivo de alíquotas do Portal NFS-e (ZIP, TXT ou CSV)");
            ch.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Alíquotas do Portal NFS-e (*.zip, *.txt, *.csv)",
                    "zip", "txt", "csv"));
            if (ch.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            final java.io.File file = ch.getSelectedFile();
            importButton.setEnabled(false);
            new javax.swing.SwingWorker<ReferenceLoader.Entry, String>() {
                @Override
                protected ReferenceLoader.Entry doInBackground() {
                    return refs.importIss(file, text -> publish(text));
                }

                @Override
                protected void process(List<String> chunks) {
                    status.setText(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    importButton.setEnabled(true);
                    try {
                        ReferenceLoader.Entry r = get();
                        status.setText(r.action() == ReferenceLoader.Action.ERRO ? "Importação falhou" : "Importação concluída");
                        detail.setText(r.action() == ReferenceLoader.Action.ERRO
                                ? "Não foi possível importar " + file.getName() + ":\n" + r.message()
                                : "Importado " + file.getName() + ": " + r.message());
                        reloadSources.run();
                        findCities.run();
                    } catch (Exception ex) {
                        status.setText("Importação falhou");
                        detail.setText(String.valueOf(ex.getMessage()));
                    }
                }
            }.execute();
        });

        JSplitPane middle = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(cityList), new JScrollPane(table));
        middle.setResizeWeight(0.25);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, middle, new JScrollPane(detail));
        split.setResizeWeight(0.72);
        p.add(split, BorderLayout.CENTER);
        uf.setSelectedItem("MG");
        return p;
    }

    /** Extrai o código de serviço da mensagem quando não há registro (linhas indeterminadas). */
    private static String serviceOf(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{3}").matcher(message);
        return m.find() ? m.group() : "";
    }

    // ------------------------------------------------------------------ apoio

    private static LocalDate parseDate(JTextField field, JTextArea feedback) {
        try {
            return LocalDate.parse(field.getText().trim(), BR);
        } catch (DateTimeParseException ex) {
            feedback.setText("Data inválida: use dd/mm/aaaa.");
            return null;
        }
    }

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static JTable table(DefaultTableModel model, int... widths) {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true);
        for (int i = 0; i < widths.length; i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return t;
    }

    static JTextArea area(String text) {
        JTextArea a = new JTextArea(7, 60);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setText(text);
        return a;
    }

    static JPanel withDetail(JPanel top, JTable table, JTextArea detail) {
        JPanel p = new JPanel(new BorderLayout());
        if (top != null) {
            p.add(top, BorderLayout.NORTH);
        }
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.7);
        p.add(split, BorderLayout.CENTER);
        return p;
    }
}
