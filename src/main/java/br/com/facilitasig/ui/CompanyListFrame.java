package br.com.facilitasig.ui;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.CompanyRepository;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.company.SampleCompanies;
import br.com.facilitasig.core.Feature;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Lista de empresas cadastradas: cadastrar, editar, excluir e escolher a empresa ativa. */
public class CompanyListFrame extends JInternalFrame {

    /** Ligação com a janela principal. */
    public interface Host {
        Company activeCompany();

        void setActiveCompany(Company company);
    }

    private static final String[] COLUMNS = {"Ativa", "CNPJ / CPF", "Nome empresarial", "Estabelecimento", "Município / UF", "Regime vigente"};

    private final CompanyRepository repository;
    private final Host host;
    private final List<Company> rows = new ArrayList<Company>();
    private final JTable table;
    private final JButton complete = new JButton("Concluir etapa e avançar");
    private final JButton sample = new JButton("Cadastrar empresa fictícia");
    private final PlaceholderFrame.StepActions actions;

    private final AbstractTableModel model = new AbstractTableModel() {
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Company x = rows.get(r);
            switch (c) {
                case 0: return isActive(x) ? "●" : "";
                case 1: return x.documentFormatted();
                case 2: return x.legalName();
                case 3: return x.establishment().label();
                case 4: return x.city() + " / " + x.uf();
                default:
                    RegimePeriod p = x.regimeOn(LocalDate.now());
                    return p == null ? "(nenhum vigente)" : p.regime().label();
            }
        }
    };

    public CompanyListFrame(Feature feature, CompanyRepository repository, Host host, PlaceholderFrame.StepActions actions) {
        super(feature.title(), true, true, true, true);
        this.repository = repository;
        this.host = host;
        this.actions = actions;
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Empresas cadastradas");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        JLabel sub = new JLabel("Cada CNPJ é um cadastro. A empresa ativa (●) é a usada na rotina de apuração.");
        sub.setForeground(new Color(0x6B7785));
        JPanel head = new JPanel(new BorderLayout());
        head.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.CENTER);
        add(head, BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(300);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    edit();
                }
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        add(sp, BorderLayout.CENTER);

        JButton add = new JButton("Nova empresa...");
        JButton edit = new JButton("Editar...");
        JButton remove = new JButton("Excluir");
        JButton activate = new JButton("Definir como ativa");
        add.addActionListener(e -> openForm(null));
        edit.addActionListener(e -> edit());
        remove.addActionListener(e -> remove());
        activate.addActionListener(e -> activate());
        sample.addActionListener(e -> registerSample());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        buttons.add(add);
        buttons.add(edit);
        buttons.add(activate);
        buttons.add(remove);
        buttons.add(sample);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        south.add(buttons, BorderLayout.WEST);
        if (actions != null) {
            JPanel step = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
            JButton reopen = new JButton("Reabrir etapa");
            reopen.addActionListener(e -> actions.reopen());
            complete.setText(actions.isDone() ? "Etapa concluída" : "Concluir etapa e avançar");
            complete.setEnabled(!actions.isDone());
            complete.addActionListener(e -> actions.complete());
            step.add(reopen);
            step.add(complete);
            south.add(step, BorderLayout.EAST);
        }
        add(south, BorderLayout.SOUTH);

        reload();
        setPreferredSize(new Dimension(1080, 440));
        pack();
        setSize(1080, 440);
    }

    private boolean isActive(Company c) {
        Company a = host.activeCompany();
        return a != null && c.id() != null && c.id().equals(a.id());
    }

    private void reload() {
        rows.clear();
        rows.addAll(repository.findAll());
        model.fireTableDataChanged();
        boolean hasDemo = repository.findByDocument(br.com.facilitasig.company.IdType.CNPJ, SampleCompanies.demo().document()) != null;
        sample.setVisible(!hasDemo);
        complete.setEnabled(actions != null && !actions.isDone() && host.activeCompany() != null);
        complete.setToolTipText(host.activeCompany() == null ? "Defina a empresa ativa para concluir a etapa." : null);
    }

    private Company selected() {
        int i = table.getSelectedRow();
        return i < 0 ? null : rows.get(i);
    }

    private void openForm(Company existing) {
        CompanyFormDialog dlg = new CompanyFormDialog(SwingUtilities.getWindowAncestor(this), repository, existing);
        dlg.setVisible(true);
        Company saved = dlg.saved();
        if (saved != null) {
            if (host.activeCompany() == null || (saved.id().equals(host.activeCompany().id()))) {
                host.setActiveCompany(saved);
            }
            reload();
        }
    }

    private void edit() {
        Company c = selected();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma empresa.");
            return;
        }
        openForm(repository.findById(c.id()));
    }

    private void activate() {
        Company c = selected();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma empresa.");
            return;
        }
        host.setActiveCompany(c);
        reload();
    }

    private void remove() {
        Company c = selected();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma empresa.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Excluir definitivamente o cadastro de\n" + c.displayName() + "?",
                "Excluir empresa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            repository.delete(c.id());
            if (isActive(c)) {
                host.setActiveCompany(null);
            }
            reload();
        }
    }

    private void registerSample() {
        try {
            Company demo = repository.save(SampleCompanies.demo());
            if (host.activeCompany() == null) {
                host.setActiveCompany(demo);
            }
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cadastro de demonstração", JOptionPane.WARNING_MESSAGE);
        }
        reload();
    }
}
