package br.com.facilitasig.ui;

import br.com.facilitasig.AppInfo;
import br.com.facilitasig.core.Feature;
import br.com.facilitasig.fiscal.Rule;
import br.com.facilitasig.fiscal.RuleCatalog;

import javax.swing.BorderFactory;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Conferência > Regras Aplicadas e Base Legal: lista as verificações da versão (IMP-nnn da
 * importação e ERR-g.n do XML), com o que cada uma afeta e a base legal.
 */
public class RulesCatalogFrame extends JInternalFrame {

    public RulesCatalogFrame(Feature feature) {
        super(feature.title(), true, true, true, true);
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());
        final List<Rule> rules = RuleCatalog.all();
        int err = 0;
        for (Rule r : rules) {
            if (r.id().startsWith("ERR-")) {
                err++;
            }
        }
        JLabel head = new JLabel("<html><b>" + rules.size() + " verificações na versão " + AppInfo.VERSION + "</b>: "
                + (rules.size() - err) + " da importação (IMP) e " + err + " do XML da nota (ERR, numeradas pelo catálogo de erros).</html>");
        head.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(head, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(new Object[]{"Regra", "O que verifica", "Afeta", "Item"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (Rule r : rules) {
            model.addRow(new Object[]{r.id(), r.title(), r.impacts(), r.catalogRef()});
        }
        final JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(50);

        final JTextArea detail = new JTextArea(5, 60);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setText("Selecione uma regra para ver a base legal. Trechos marcados [validar] ainda dependem de conferência.");
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            Rule r = RuleCatalog.get((String) table.getValueAt(row, 0));
            detail.setText(r.id() + " — " + r.title() + "\nAfeta: " + r.impacts() + "\nBase: " + r.basis()
                    + "\nItem do catálogo de erros: " + r.catalogRef());
            detail.setCaretPosition(0);
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.8);
        add(split, BorderLayout.CENTER);
        setSize(900, 560);
    }
}
