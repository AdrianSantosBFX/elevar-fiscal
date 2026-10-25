package br.com.facilitasig.ui;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.CompanyRepository;
import br.com.facilitasig.company.RegimePeriod;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;

/**
 * Menu Empresa > Selecionar empresa. Lista os CNPJs cadastrados; a empresa escolhida passa a ser
 * a única cujos dados (notas, SPED, apuração) o sistema mostra. Matriz e filiais são empresas
 * distintas, cada uma com o próprio CNPJ.
 */
public final class CompanySelectDialog extends JDialog {

    /** Resultado: empresa escolhida, ou pedido para abrir o cadastro. */
    public enum Choice { SELECTED, OPEN_REGISTRY, CANCELLED }

    private final List<Company> companies;
    private final JTable table;
    private Choice choice = Choice.CANCELLED;
    private Company selected;

    public CompanySelectDialog(Window owner, CompanyRepository repository, Company current) {
        super(owner, "Selecionar empresa", ModalityType.APPLICATION_MODAL);
        this.companies = repository.findAll();
        setLayout(new BorderLayout());

        JLabel head = new JLabel("<html><b>Com qual empresa você vai trabalhar?</b><br>"
                + "O sistema mostra apenas as notas, arquivos e apurações da empresa selecionada. "
                + "Matriz e filiais são selecionadas separadamente.</html>");
        head.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        add(head, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(new Object[]{"", "CNPJ/CPF", "Razão social", "Tipo", "Município/UF", "Regime vigente"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        int currentRow = -1;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < companies.size(); i++) {
            Company c = companies.get(i);
            boolean isCurrent = current != null && current.id() != null && current.id().equals(c.id());
            if (isCurrent) {
                currentRow = i;
            }
            RegimePeriod rp = c.regimeOn(today);
            model.addRow(new Object[]{isCurrent ? "●" : "", c.documentFormatted(), c.legalName(),
                    c.establishment() == null ? "" : c.establishment().toString(), c.city() + "/" + c.uf(),
                    rp == null ? "—" : rp.regime().label()});
        }
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(24);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(300);
        if (currentRow >= 0) {
            table.setRowSelectionInterval(currentRow, currentRow);
        } else if (!companies.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    choose();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose");
        table.getActionMap().put("choose", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                choose();
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        add(sp, BorderLayout.CENTER);

        JButton ok = new JButton("Selecionar");
        JButton registry = new JButton("Cadastro de empresas...");
        JButton cancel = new JButton("Cancelar");
        ok.setEnabled(!companies.isEmpty());
        ok.addActionListener(e -> choose());
        registry.addActionListener(e -> {
            choice = Choice.OPEN_REGISTRY;
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        JLabel empty = new JLabel(companies.isEmpty() ? "Nenhuma empresa cadastrada. Use o cadastro para incluir a primeira." : " ");
        south.add(empty, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(registry);
        buttons.add(cancel);
        buttons.add(ok);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(820, 380);
        setLocationRelativeTo(owner);
    }

    private void choose() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        selected = companies.get(table.convertRowIndexToModel(row));
        choice = Choice.SELECTED;
        dispose();
    }

    public Choice choice() {
        return choice;
    }

    public Company selected() {
        return selected;
    }
}
