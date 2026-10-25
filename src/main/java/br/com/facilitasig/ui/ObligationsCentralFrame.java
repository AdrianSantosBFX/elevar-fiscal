package br.com.facilitasig.ui;

import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.core.Feature;
import br.com.facilitasig.oa.Applicability;
import br.com.facilitasig.oa.ObligationService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Obrigações > Central de Obrigações Acessórias (etapa 6 da rotina): as obrigações da empresa em uso na
 * competência (mensais) e no ano-calendário (anuais), com situação, motivo e vencimento; e os vencimentos do mês.
 */
public class ObligationsCentralFrame extends JInternalFrame {

    private static final Locale PT = Locale.forLanguageTag("pt-BR");

    private final ObligationService service;
    private final Company company;

    public ObligationsCentralFrame(Feature feature, ObligationService service, Company company,
                                   PlaceholderFrame.StepActions actions) {
        super(feature.title() + " — " + company.legalName(), true, true, true, true);
        this.service = service;
        this.company = company;
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());

        RegimePeriod rp = company.regimeOn(LocalDate.now());
        JLabel head = new JLabel("<html><b>" + company.legalName() + "</b> — " + company.documentFormatted() + " · "
                + company.city() + "/" + company.uf() + " · Regime vigente: " + (rp == null ? "não informado" : rp.regime().label())
                + "<br>Situação calculada pelo cadastro (regime na competência, inscrições estadual e municipal). "
                + "\"A confirmar\" = depende de fato que o cadastro não mostra (folha, retenções, benefícios, recebimentos em espécie).</html>");
        head.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(head, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Obrigações da competência", competenceTab());
        tabs.addTab("Vencimentos do mês", dueTab());
        add(tabs, BorderLayout.CENTER);

        if (actions != null) {
            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton reopen = new JButton("Reabrir etapa");
            reopen.addActionListener(e -> actions.reopen());
            JButton complete = new JButton(actions.isDone() ? "Etapa concluída" : "Concluir etapa e avançar");
            complete.setEnabled(!actions.isDone());
            complete.addActionListener(e -> actions.complete());
            south.add(reopen);
            south.add(complete);
            add(south, BorderLayout.SOUTH);
        }
        setSize(1080, 640);
    }

    private static String dueText(LocalDate d) {
        return d == null ? "Prazo da UF/município" : ObligationsCatalogFrame.BR.format(d) + " ("
                + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT) + ")";
    }

    private static final Color OK = new Color(0x1E7B34);
    private static final Color CHECK = new Color(0xB4570A);
    private static final Color OFF = new Color(0x8A939D);

    private static void colorStatus(JTable table, int column) {
        table.getColumnModel().getColumn(column).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                if (!sel) {
                    String s = String.valueOf(v);
                    comp.setForeground(s.startsWith("Obrigat") ? OK : s.startsWith("A confirmar") ? CHECK : OFF);
                }
                return comp;
            }
        });
    }

    private JPanel competenceTab() {
        final JTextField month = new JTextField(ObligationsCatalogFrame.MONTH.format(YearMonth.now().minusMonths(1)), 7);
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        final JCheckBox all = new JCheckBox("Mostrar as que não se aplicam e as extintas", false);
        final JLabel summary = new JLabel();
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Competência:"));
        bar.add(prev);
        bar.add(month);
        bar.add(next);
        bar.add(all);
        bar.add(summary);
        final DefaultTableModel model = ReferenceTablesFrame.readOnlyModel("Obrigação", "Período", "Situação", "Vencimento", "Esfera", "Motivo");
        final JTable table = ReferenceTablesFrame.table(model, 250, 120, 95, 120, 70, 420);
        colorStatus(table, 2);
        final List<Applicability> shown = new ArrayList<Applicability>();
        final JTextArea detail = ReferenceTablesFrame.area("Selecione uma obrigação.");
        Runnable reload = () -> {
            YearMonth m;
            try {
                m = YearMonth.parse(month.getText().trim(), ObligationsCatalogFrame.MONTH);
            } catch (Exception ex) {
                detail.setText("Competência inválida: use mm/aaaa.");
                return;
            }
            model.setRowCount(0);
            shown.clear();
            int mandatory = 0;
            int check = 0;
            for (Applicability a : service.evaluate(company, m)) {
                if (a.status() == Applicability.Status.OBRIGATORIA) {
                    mandatory++;
                } else if (a.status() == Applicability.Status.A_CONFIRMAR) {
                    check++;
                } else if (!all.isSelected()) {
                    continue;
                }
                shown.add(a);
                String sphere = a.obligation().sphere();
                model.addRow(new Object[]{a.obligation().name(), a.period(), a.status().label(),
                        a.status() == Applicability.Status.OBRIGATORIA || a.status() == Applicability.Status.A_CONFIRMAR
                                ? dueText(a.dueDate()) : "", sphere.charAt(0) + sphere.substring(1).toLowerCase(), a.reason()});
            }
            summary.setText("   " + mandatory + " obrigatória(s), " + check + " a confirmar");
        };
        prev.addActionListener(e -> ObligationsCatalogFrame.shift(month, -1, reload));
        next.addActionListener(e -> ObligationsCatalogFrame.shift(month, 1, reload));
        month.addActionListener(e -> reload.run());
        all.addActionListener(e -> reload.run());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                Applicability a = shown.get(table.convertRowIndexToModel(row));
                detail.setText(a.status().label() + " — " + a.period() + (a.dueDate() == null ? "" : " — vence em " + dueText(a.dueDate()))
                        + "\nMotivo: " + a.reason() + "\n\n" + ObligationsCatalogFrame.detail(a.obligation()));
                detail.setCaretPosition(0);
            }
        });
        reload.run();
        return ReferenceTablesFrame.withDetail(bar, table, detail);
    }

    private JPanel dueTab() {
        final JTextField month = new JTextField(ObligationsCatalogFrame.MONTH.format(YearMonth.now()), 7);
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Mês de vencimento:"));
        bar.add(prev);
        bar.add(month);
        bar.add(next);
        final DefaultTableModel model = ReferenceTablesFrame.readOnlyModel("Vencimento", "Obrigação", "Competência", "Situação", "Motivo");
        final JTable table = ReferenceTablesFrame.table(model, 120, 260, 130, 95, 420);
        colorStatus(table, 3);
        final List<Applicability> shown = new ArrayList<Applicability>();
        final JTextArea detail = ReferenceTablesFrame.area("Obrigações da empresa que vencem no mês (só as com prazo calculado).");
        Runnable reload = () -> {
            YearMonth m;
            try {
                m = YearMonth.parse(month.getText().trim(), ObligationsCatalogFrame.MONTH);
            } catch (Exception ex) {
                detail.setText("Mês inválido: use mm/aaaa.");
                return;
            }
            model.setRowCount(0);
            shown.clear();
            for (Applicability a : service.dueIn(company, m)) {
                shown.add(a);
                model.addRow(new Object[]{dueText(a.dueDate()), a.obligation().name(), a.period(), a.status().label(), a.reason()});
            }
            detail.setText(shown.size() + " vencimento(s) em " + ObligationsCatalogFrame.MONTH.format(m) + ".");
        };
        prev.addActionListener(e -> ObligationsCatalogFrame.shift(month, -1, reload));
        next.addActionListener(e -> ObligationsCatalogFrame.shift(month, 1, reload));
        month.addActionListener(e -> reload.run());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                Applicability a = shown.get(table.convertRowIndexToModel(row));
                detail.setText("Vence em " + dueText(a.dueDate()) + " — " + a.period() + "\nMotivo: " + a.reason() + "\n\n"
                        + ObligationsCatalogFrame.detail(a.obligation()));
                detail.setCaretPosition(0);
            }
        });
        reload.run();
        return ReferenceTablesFrame.withDetail(bar, table, detail);
    }
}
