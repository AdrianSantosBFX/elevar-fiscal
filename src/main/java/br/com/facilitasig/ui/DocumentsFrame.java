package br.com.facilitasig.ui;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;
import br.com.facilitasig.core.Feature;
import br.com.facilitasig.fiscal.Direction;
import br.com.facilitasig.fiscal.DocStatus;
import br.com.facilitasig.fiscal.DocValidator;
import br.com.facilitasig.fiscal.Finding;
import br.com.facilitasig.fiscal.Fields;
import br.com.facilitasig.fiscal.FiscalDoc;
import br.com.facilitasig.fiscal.FiscalService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Módulo de importação e conferência de notas de UM sentido (entrada ou saída). Importa XMLs em
 * lote a partir de uma pasta, valida cada nota e permite corrigir e revalidar.
 */
public class DocumentsFrame extends JInternalFrame {

    private static final String[] COLUMNS = {"Chave", "Cliente/Fornecedor", "Nome", "Documento", "Espécie", "Série",
            "Data Entrada/Saída", "Natureza", "Descrição", "Valor", "Situação", "Pendências"};
    private static final DocStatus[] TAB_FILTERS = {null, DocStatus.CONFORME, DocStatus.ALERTA, DocStatus.ERRO, DocStatus.DUPLICADA};
    private static final String[] TAB_NAMES = {"Todas", "Conformes", "Com alerta", "Com erro", "Duplicadas"};
    private static File lastDir;

    private final Direction direction;
    private final FiscalService service;
    private final Company company;
    private final List<FiscalDoc> all = new ArrayList<FiscalDoc>();
    private final List<FiscalDoc> shown = new ArrayList<FiscalDoc>();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTable table;
    private final JTextArea detail = new JTextArea(5, 40);

    private final AbstractTableModel model = new AbstractTableModel() {
        public int getRowCount() { return shown.size(); }
        public int getColumnCount() { return COLUMNS.length; }
        public String getColumnName(int c) { return COLUMNS[c]; }

        public Object getValueAt(int r, int c) {
            FiscalDoc d = shown.get(r);
            switch (c) {
                case 0: return d.id();
                case 1: return d.partyGet("doc");
                case 2: return d.partyGet("name");
                case 3: return d.get("number");
                case 4: return d.kind().label();
                case 5: return d.get("series");
                case 6: return brDate(d.get("entry_exit_date"));
                case 7: return d.get("nature_code");
                case 8: return d.get("nat_op");
                case 9: return money(d.get("total_value"));
                case 10: return d.status().label();
                default:
                    return d.findings().isEmpty() ? "" : d.findings().size() + " — " + d.findings().get(0).message();
            }
        }
    };

    public DocumentsFrame(Feature feature, Direction direction, FiscalService service, Company company) {
        super(feature.title() + " — " + company.legalName(), true, true, true, true);
        this.direction = direction;
        this.service = service;
        this.company = company;
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());

        // barra de ações
        JButton importFolder = new JButton("Importar pasta...");
        JButton importFiles = new JButton("Importar arquivos...");
        JButton edit = new JButton("Editar / corrigir...");
        JButton revalSel = new JButton("Revalidar selecionada");
        JButton revalAll = new JButton("Revalidar todas");
        JButton remove = new JButton("Excluir selecionadas");
        importFolder.addActionListener(e -> importFolder());
        importFiles.addActionListener(e -> importFiles());
        edit.addActionListener(e -> editSelected());
        revalSel.addActionListener(e -> revalidateSelected());
        revalAll.addActionListener(e -> revalidateAll());
        remove.addActionListener(e -> removeSelected());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        for (JButton b : Arrays.asList(importFolder, importFiles, edit, revalSel, revalAll, remove)) {
            bar.add(b);
        }

        for (String n : TAB_NAMES) {
            JPanel empty = new JPanel();
            empty.setPreferredSize(new Dimension(0, 0));
            tabs.addTab(n, empty);
        }
        tabs.setPreferredSize(new Dimension(100, 30));
        tabs.addChangeListener(e -> applyFilter());
        JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.NORTH);
        north.add(tabs, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {50, 130, 230, 80, 60, 50, 120, 100, 220, 90, 90, 420};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setDefaultRenderer(Object.class, new StatusRenderer());
        table.getSelectionModel().addListSelectionListener(e -> showDetail());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.8);
        add(split, BorderLayout.CENTER);

        JLabel hint = new JLabel(direction == Direction.ENTRADA
                ? "  Notas de ENTRADA: o destinatário (ou emitente, em nota própria de entrada) deve ser a empresa ativa."
                : "  Notas de SAÍDA: o emitente (NFS-e: o prestador) deve ser a empresa ativa.");
        hint.setForeground(new Color(0x6B7785));
        hint.setHorizontalAlignment(SwingConstants.LEFT);
        add(hint, BorderLayout.SOUTH);

        reload();
        setPreferredSize(new Dimension(1100, 560));
        pack();
        setSize(1100, 560);
    }

    // ------------------------------------------------------------------ dados

    private void reload() {
        all.clear();
        all.addAll(service.repository().list(company.id(), direction));
        int[] counts = new int[TAB_FILTERS.length];
        for (FiscalDoc d : all) {
            counts[0]++;
            for (int i = 1; i < TAB_FILTERS.length; i++) {
                if (d.status() == TAB_FILTERS[i]) {
                    counts[i]++;
                }
            }
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabs.setTitleAt(i, TAB_NAMES[i] + " (" + counts[i] + ")");
        }
        applyFilter();
    }

    private void applyFilter() {
        int i = Math.max(0, tabs.getSelectedIndex());
        shown.clear();
        for (FiscalDoc d : all) {
            if (TAB_FILTERS[i] == null || d.status() == TAB_FILTERS[i]) {
                shown.add(d);
            }
        }
        model.fireTableDataChanged();
        showDetail();
    }

    private List<FiscalDoc> selected() {
        List<FiscalDoc> out = new ArrayList<FiscalDoc>();
        for (int r : table.getSelectedRows()) {
            out.add(shown.get(table.convertRowIndexToModel(r)));
        }
        return out;
    }

    private void showDetail() {
        List<FiscalDoc> sel = selected();
        if (sel.size() != 1) {
            detail.setText(sel.isEmpty() ? "Selecione uma nota para ver as pendências." : sel.size() + " notas selecionadas.");
            return;
        }
        FiscalDoc d = sel.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(d.kind().label()).append(" ").append(d.get("number")).append(" — chave ").append(d.accessKey()).append('\n');
        sb.append("Arquivo: ").append(d.sourcePath()).append('\n');
        if (d.findings().isEmpty()) {
            sb.append("Sem pendências: nota conforme nas verificações da importação.");
        }
        for (Finding f : d.findings()) {
            sb.append(f).append('\n');
        }
        if (!d.overrides().isEmpty()) {
            sb.append("Campos corrigidos pelo usuário: ").append(d.overrides().size()).append('\n');
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    // ------------------------------------------------------------------ importação

    private void importFolder() {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Escolha a pasta com os XMLs de " + direction.label().toLowerCase());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dir = fc.getSelectedFile();
        lastDir = dir;
        int sub = JOptionPane.showConfirmDialog(this, "Incluir também as subpastas de\n" + dir.getAbsolutePath() + "?",
                "Importar pasta", JOptionPane.YES_NO_CANCEL_OPTION);
        if (sub == JOptionPane.CANCEL_OPTION || sub == JOptionPane.CLOSED_OPTION) {
            return;
        }
        List<File> files = FiscalService.collect(dir, sub == JOptionPane.YES_OPTION);
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhum arquivo .xml encontrado.", "Importar pasta", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        runImport(files);
    }

    private void importFiles() {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter("XML de nota fiscal (*.xml)", "xml"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastDir = fc.getCurrentDirectory();
        runImport(Arrays.asList(fc.getSelectedFiles()));
    }

    private void runImport(final List<File> files) {
        final AtomicBoolean cancel = new AtomicBoolean(false);
        final JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Importando notas de " + direction.label().toLowerCase(),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        final JProgressBar bar = new JProgressBar(0, files.size());
        bar.setStringPainted(true);
        final JLabel current = new JLabel(" ");
        JButton cancelBtn = new JButton("Cancelar");
        cancelBtn.addActionListener(e -> cancel.set(true));
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        p.add(current, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        p.add(cancelBtn, BorderLayout.SOUTH);
        dlg.setContentPane(p);
        dlg.setSize(460, 150);
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.setLocationRelativeTo(this);

        SwingWorker<FiscalService.ImportResult, Object[]> worker = new SwingWorker<FiscalService.ImportResult, Object[]>() {
            @Override
            protected FiscalService.ImportResult doInBackground() {
                return service.importFiles(files, direction, company, new FiscalService.Progress() {
                    public boolean step(int done, int total, String file) {
                        publish(new Object[]{done, file});
                        return !cancel.get();
                    }
                });
            }

            @Override
            protected void process(List<Object[]> chunks) {
                Object[] last = chunks.get(chunks.size() - 1);
                bar.setValue((Integer) last[0]);
                current.setText(String.valueOf(last[1]));
            }

            @Override
            protected void done() {
                dlg.dispose();
            }
        };
        worker.execute();
        dlg.setVisible(true);
        try {
            showSummary(worker.get());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Falha na importação: " + e.getMessage(), "Importação", JOptionPane.ERROR_MESSAGE);
        }
        reload();
    }

    private void showSummary(FiscalService.ImportResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Arquivos lidos: ").append(r.read).append('\n')
                .append("Conformes: ").append(r.conforme).append('\n')
                .append("Com alerta: ").append(r.alerta).append('\n')
                .append("Com erro: ").append(r.erro).append('\n')
                .append("Duplicadas: ").append(r.duplicada).append('\n')
                .append("Já registradas (repetidas): ").append(r.repetida).append('\n')
                .append("Ignorados (eventos/resumos): ").append(r.ignorada).append('\n');
        if (!r.messages.isEmpty()) {
            sb.append("\nObservações (até 15):\n");
            for (int i = 0; i < Math.min(15, r.messages.size()); i++) {
                sb.append("• ").append(r.messages.get(i)).append('\n');
            }
        }
        JTextArea area = new JTextArea(sb.toString(), 18, 60);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Resultado da importação", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ ações

    private void editSelected() {
        List<FiscalDoc> sel = selected();
        if (sel.size() != 1) {
            JOptionPane.showMessageDialog(this, "Selecione uma nota.");
            return;
        }
        if (sel.get(0).status() == DocStatus.DUPLICADA) {
            JOptionPane.showMessageDialog(this, "Duplicatas não são editadas. Exclua o registro duplicado.");
            return;
        }
        new DocumentEditDialog(SwingUtilities.getWindowAncestor(this), service, company, sel.get(0).id()).setVisible(true);
        reload();
    }

    private void revalidateSelected() {
        List<FiscalDoc> sel = selected();
        int n = 0;
        for (FiscalDoc d : sel) {
            if (d.status() != DocStatus.DUPLICADA) {
                service.review(d.id(), null, company);
                n++;
            }
        }
        reload();
        JOptionPane.showMessageDialog(this, n + " nota(s) revalidada(s).");
    }

    private void revalidateAll() {
        int changed = service.revalidateAll(direction, company);
        reload();
        JOptionPane.showMessageDialog(this, "Revalidação concluída. Notas que mudaram de situação: " + changed + ".");
    }

    private void removeSelected() {
        List<FiscalDoc> sel = selected();
        if (sel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione ao menos uma nota.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Excluir " + sel.size() + " nota(s) do banco?\nOs arquivos XML originais não são apagados.",
                "Excluir", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            for (FiscalDoc d : sel) {
                service.repository().delete(d.id());
            }
            reload();
        }
    }

    // ------------------------------------------------------------------ apresentação

    static String brDate(String iso) {
        LocalDate d = DocValidator.date(iso);
        return d == null ? iso : d.format(DeclaredField.DATE);
    }

    static String money(String v) {
        if (v == null || Fields.NA.equals(v)) {
            return v;
        }
        try {
            return new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(new java.util.Locale("pt", "BR")))
                    .format(new BigDecimal(v));
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
            if (!sel) {
                FiscalDoc d = shown.get(t.convertRowIndexToModel(row));
                Color bg = Color.WHITE;
                switch (d.status()) {
                    case ERRO: bg = new Color(0xFDE2E2); break;
                    case ALERTA: bg = new Color(0xFFF4D6); break;
                    case DUPLICADA: bg = new Color(0xE4E7EB); break;
                    default: break;
                }
                c.setBackground(bg);
                c.setForeground(Color.BLACK);
            }
            setHorizontalAlignment(col == 9 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            return c;
        }
    }
}
