package br.com.facilitasig.ui;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.Company;
import br.com.facilitasig.fiscal.DocKind;
import br.com.facilitasig.fiscal.DocValidator;
import br.com.facilitasig.fiscal.Fields;
import br.com.facilitasig.fiscal.Finding;
import br.com.facilitasig.fiscal.FiscalDoc;
import br.com.facilitasig.fiscal.FiscalService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Correção de uma nota importada. O XML original não é alterado: as correções ficam numa
 * camada separada (com o valor original guardado) e a nota é revalidada ao salvar.
 */
public class DocumentEditDialog extends JDialog {

    private static final String NA = Fields.NA;

    private final FiscalService service;
    private final Company company;
    private final FiscalDoc doc;
    private final DeclaredField entryDate = new DeclaredField("Data de entrada/saída", 10, true, false);
    private final DeclaredField nature = new DeclaredField("Natureza interna", 12, false, true);
    private final JComboBox<String> uf = new JComboBox<>();
    private final DeclaredField ibge = new DeclaredField("Município (IBGE)", 10, false, true);
    private final DeclaredField ie = new DeclaredField("Inscrição estadual", 16, false, true);
    private final JComboBox<String> issRetained = new JComboBox<>(new String[]{"SIM", "NÃO", NA});
    private final DefaultTableModel itemModel;
    private final JTable itemTable;
    private final JTextArea findings = new JTextArea(5, 60);

    public DocumentEditDialog(Window owner, FiscalService service, Company company, long docId) {
        super(owner, "Corrigir nota", ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.company = company;
        this.doc = service.repository().load(docId);
        setLayout(new BorderLayout());

        uf.addItem(NA);
        for (String u : BrDocs.ufs()) {
            uf.addItem(u);
        }
        boolean nfe = doc.kind() == DocKind.NFE;

        JTextArea head = new JTextArea(summary());
        head.setEditable(false);
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
        add(head, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        int r = 0;
        r = row(form, r, "Data de entrada/saída", entryDate);
        r = row(form, r, "Natureza interna (código)", nature);
        String party = doc.partyPrefix().equals("issuer_") ? "Fornecedor (emitente)" : "Cliente (destinatário/tomador)";
        r = row(form, r, party + " — UF", uf);
        r = row(form, r, party + " — município (IBGE)", ibge);
        if (nfe) {
            r = row(form, r, party + " — inscrição estadual", ie);
        } else {
            r = row(form, r, "ISS retido", issRetained);
        }

        String[] cols = {"#", "Descrição", "NCM", "CFOP", "CST/CSOSN"};
        itemModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return doc.kind() == DocKind.NFE && col >= 2;
            }
        };
        itemTable = new JTable(itemModel);
        itemTable.setRowHeight(22);
        itemTable.getColumnModel().getColumn(0).setMaxWidth(40);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        JScrollPane isp = new JScrollPane(itemTable);
        isp.setPreferredSize(new Dimension(700, 140));
        isp.setBorder(BorderFactory.createTitledBorder(nfe ? "Itens (NCM, CFOP e CST editáveis)" : "Serviço"));

        findings.setEditable(false);
        findings.setLineWrap(true);
        findings.setWrapStyleWord(true);
        JScrollPane fsp = new JScrollPane(findings);
        fsp.setBorder(BorderFactory.createTitledBorder("Pendências atuais"));

        JPanel center = new JPanel(new BorderLayout());
        center.add(form, BorderLayout.NORTH);
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(isp, BorderLayout.CENTER);
        lower.add(fsp, BorderLayout.SOUTH);
        center.add(lower, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JButton save = new JButton("Salvar e revalidar");
        JButton restore = new JButton("Restaurar valores do XML");
        JButton cancel = new JButton("Cancelar");
        save.addActionListener(e -> onSave());
        restore.addActionListener(e -> {
            doc.overrides().clear();
            load();
        });
        cancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        south.add(restore);
        south.add(save);
        south.add(cancel);
        add(south, BorderLayout.SOUTH);

        load();
        setSize(820, 680);
        setLocationRelativeTo(owner);
    }

    private static int row(JPanel p, int r, String label, java.awt.Component c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = r;
        g.insets = new Insets(3, 2, 3, 8);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.setOpaque(false);
        line.add(c);
        p.add(line, g);
        return r + 1;
    }

    private String summary() {
        return doc.kind().label() + " nº " + doc.get("number") + "   série " + doc.get("series")
                + "   emissão " + DocumentsFrame.brDate(doc.get("issued_at")) + "   valor " + DocumentsFrame.money(doc.get("total_value")) + "\n"
                + "Chave: " + doc.accessKey() + "\n"
                + "Emitente: " + doc.get("issuer_doc") + " — " + doc.get("issuer_name") + "\n"
                + "Destinatário/tomador: " + doc.get("recipient_doc") + " — " + doc.get("recipient_name") + "\n"
                + "Situação: " + doc.status().label();
    }

    private void load() {
        LocalDate d = DocValidator.date(doc.get("entry_exit_date"));
        entryDate.setDate(d);
        if (d == null) {
            entryDate.textField().setText("");
        }
        nature.setText(NA.equals(doc.get("nature_code")) ? null : doc.get("nature_code"));
        uf.setSelectedItem(doc.partyGet("uf"));
        ibge.setText(NA.equals(doc.partyGet("ibge")) ? null : doc.partyGet("ibge"));
        ie.setText(NA.equals(doc.partyGet("ie")) ? null : doc.partyGet("ie"));
        issRetained.setSelectedItem(doc.get("iss_retained"));
        itemModel.setRowCount(0);
        for (int i = 0; i < doc.itemCount(); i++) {
            itemModel.addRow(new Object[]{i + 1, doc.itemGet(i, "description"), doc.itemGet(i, "ncm"),
                    doc.itemGet(i, "cfop"), doc.itemGet(i, "cst")});
        }
        StringBuilder sb = new StringBuilder();
        for (Finding f : doc.findings()) {
            sb.append(f).append('\n');
        }
        findings.setText(sb.length() == 0 ? "Sem pendências." : sb.toString());
        findings.setBackground(sb.length() == 0 ? new Color(0xEAF7EA) : new Color(0xFFF4D6));
    }

    private void set(Map<String, String> ov, String key, String original, String value) {
        if (!value.equals(original)) {
            ov.put(key, value);
        }
    }

    private void onSave() {
        if (itemTable.getCellEditor() != null) {
            itemTable.getCellEditor().stopCellEditing();
        }
        try {
            Map<String, String> ov = new LinkedHashMap<String, String>();
            LocalDate date = entryDate.requiredDate();
            set(ov, "entry_exit_date", doc.original("entry_exit_date"), date.toString());
            set(ov, "nature_code", doc.original("nature_code"), nature.value().orElse(NA));
            String p = doc.partyPrefix();
            set(ov, p + "uf", doc.original(p + "uf"), String.valueOf(uf.getSelectedItem()));
            set(ov, p + "ibge", doc.original(p + "ibge"), BrDocs.digits(ibge.value().orElse(NA)).isEmpty() ? NA : BrDocs.digits(ibge.value().get()));
            if (doc.kind() == DocKind.NFE) {
                set(ov, p + "ie", doc.original(p + "ie"), ie.value().isPresent() ? BrDocs.alphanumeric(ie.value().get()) : NA);
                for (int i = 0; i < doc.itemCount(); i++) {
                    set(ov, FiscalDoc.itemKey(i, "ncm"), doc.itemOriginal(i, "ncm"), clean(itemModel.getValueAt(i, 2), true));
                    set(ov, FiscalDoc.itemKey(i, "cfop"), doc.itemOriginal(i, "cfop"), clean(itemModel.getValueAt(i, 3), true));
                    set(ov, FiscalDoc.itemKey(i, "cst"), doc.itemOriginal(i, "cst"), clean(itemModel.getValueAt(i, 4), false));
                }
            } else {
                set(ov, "iss_retained", doc.original("iss_retained"), String.valueOf(issRetained.getSelectedItem()));
            }
            FiscalDoc result = service.review(doc.id(), ov, company);
            String msg = result.findings().isEmpty()
                    ? "Nota revalidada: CONFORME."
                    : "Nota revalidada: " + result.status().label().toUpperCase() + " (" + result.findings().size() + " pendência(s)).";
            JOptionPane.showMessageDialog(this, msg, "Revalidação", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Corrigir nota", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static String clean(Object v, boolean digitsOnly) {
        String s = v == null ? "" : v.toString().trim();
        if (Fields.NA.equals(s) || Declared_isNone(s)) {
            return NA;
        }
        s = digitsOnly ? BrDocs.digits(s) : BrDocs.alphanumeric(s);
        return s.isEmpty() ? NA : s;
    }

    private static boolean Declared_isNone(String s) {
        return br.com.facilitasig.company.Declared.isNoneMarker(s);
    }
}
