package br.com.facilitasig.ui;

import br.com.facilitasig.core.Feature;
import br.com.facilitasig.oa.BusinessCalendar;
import br.com.facilitasig.oa.ObligationService;
import br.com.facilitasig.ref.Obligation;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Obrigações > Catálogo de Obrigações (Lucro Presumido e Real) e Consultas > Calendário de Obrigações.
 * Não depende de empresa: mostra o catálogo, o calendário de vencimentos de um mês por regime e UF e os feriados.
 */
public class ObligationsCatalogFrame extends JInternalFrame {

    static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Locale PT = Locale.forLanguageTag("pt-BR");

    private final ObligationService service;

    public ObligationsCatalogFrame(Feature feature, ObligationService service, boolean calendarFirst) {
        super(feature.title(), true, true, true, true);
        this.service = service;
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Catálogo (LP e LR)", catalogTab());
        tabs.addTab("Calendário de vencimentos", calendarTab());
        tabs.addTab("Feriados nacionais", holidaysTab());
        if (calendarFirst) {
            tabs.setSelectedIndex(1);
        }
        add(tabs, BorderLayout.CENTER);
        JLabel foot = new JLabel("Catálogo de curadoria (grau: Curadoria). Itens [validar] ainda sem conferência no texto oficial. "
                + "Dia útil: feriados nacionais; feriados estaduais e municipais não entram.");
        foot.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(foot, BorderLayout.SOUTH);
        setSize(1060, 640);
    }

    static String yesNo(String derivable) {
        return "SIM".equals(derivable) ? "Sim" : "PARCIAL".equals(derivable) ? "Parcial" : "Não";
    }

    static String situation(Obligation o) {
        Validity v = o.validity();
        if (v.end() == null) {
            return "Vigente";
        }
        return v.end().isBefore(LocalDate.now()) ? "Extinta após " + MONTH.format(v.end()) : "Vigente até " + MONTH.format(v.end());
    }

    static String detail(Obligation o) {
        StringBuilder sb = new StringBuilder();
        sb.append(o.name()).append(" (").append(o.code()).append(o.isGeneric() ? "" : ", " + o.uf()).append(")")
                .append("\nEsfera: ").append(o.sphere().charAt(0)).append(o.sphere().substring(1).toLowerCase())
                .append(" · Periodicidade: ").append(o.periodicity().toLowerCase())
                .append(" · Situação: ").append(situation(o))
                .append("\nRegimes: ").append(o.regimesText())
                .append("\nQuem está obrigado: ").append(o.condition())
                .append("\nPrazo: ").append(o.deadlineText()).append("  [regra ").append(o.deadlineRule()).append("]")
                .append("\nBase legal: ").append(o.legalBasis())
                .append("\nVigência (competências): ").append(o.validity())
                .append("\nAs notas fiscais sustentam? ").append(yesNo(o.derivable()))
                .append(o.fromInvoices().isEmpty() ? "" : " — " + o.fromInvoices());
        if (!o.replacedBy().isEmpty()) {
            sb.append("\nSubstituída por: ").append(o.replacedBy());
        }
        if (!o.note().isEmpty()) {
            sb.append("\nObservação: ").append(o.note());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ catálogo

    private JPanel catalogTab() {
        final JComboBox<String> regime = new JComboBox<String>(new String[]{"Lucro Presumido e Lucro Real", "Lucro Presumido", "Lucro Real"});
        final JComboBox<String> sphere = new JComboBox<String>(new String[]{"Todas as esferas", "Federal", "Estadual", "Municipal"});
        final JCheckBox extinct = new JCheckBox("Mostrar extintas", true);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(regime);
        bar.add(sphere);
        bar.add(extinct);
        final JLabel count = new JLabel();
        bar.add(count);

        final DefaultTableModel model = ReferenceTablesFrame.readOnlyModel("Obrigação", "Esfera", "UF", "Lucro Presumido",
                "Lucro Real", "Periodicidade", "Prazo", "Notas", "Situação");
        final JTable table = ReferenceTablesFrame.table(model, 260, 70, 35, 120, 120, 75, 230, 55, 120);
        final List<Obligation> shown = new ArrayList<Obligation>();
        final JTextArea detail = ReferenceTablesFrame.area("Selecione uma obrigação para ver quem está obrigado, prazo, base legal "
                + "e o que as notas fiscais sustentam.");
        Runnable reload = () -> {
            model.setRowCount(0);
            shown.clear();
            for (Obligation o : service.catalog()) {
                if (regime.getSelectedIndex() == 1 && o.conditionFor(Obligation.LP) == null
                        || regime.getSelectedIndex() == 2 && o.conditionFor(Obligation.LR) == null) {
                    continue;
                }
                if (sphere.getSelectedIndex() > 0 && !o.sphere().equalsIgnoreCase(
                        new String[]{"", "FEDERAL", "ESTADUAL", "MUNICIPAL"}[sphere.getSelectedIndex()])) {
                    continue;
                }
                if (!extinct.isSelected() && o.validity().end() != null && o.validity().end().isBefore(LocalDate.now())) {
                    continue;
                }
                shown.add(o);
                Obligation.Condition lp = o.conditionFor(Obligation.LP);
                Obligation.Condition lr = o.conditionFor(Obligation.LR);
                model.addRow(new Object[]{o.name(), o.sphere().charAt(0) + o.sphere().substring(1).toLowerCase(),
                        o.isGeneric() ? "Todas" : o.uf(), lp == null ? "—" : lp.label(), lr == null ? "—" : lr.label(),
                        o.periodicity().charAt(0) + o.periodicity().substring(1).toLowerCase(), o.deadlineText(),
                        yesNo(o.derivable()), situation(o)});
            }
            count.setText("   " + shown.size() + " obrigação(ões)");
        };
        regime.addActionListener(e -> reload.run());
        sphere.addActionListener(e -> reload.run());
        extinct.addActionListener(e -> reload.run());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                detail.setText(detail(shown.get(table.convertRowIndexToModel(row))));
                detail.setCaretPosition(0);
            }
        });
        reload.run();
        return ReferenceTablesFrame.withDetail(bar, table, detail);
    }

    // ------------------------------------------------------------------ calendário

    private JPanel calendarTab() {
        final JTextField month = new JTextField(MONTH.format(LocalDate.now()), 7);
        final JComboBox<String> regime = new JComboBox<String>(new String[]{"Lucro Presumido", "Lucro Real"});
        final JComboBox<String> uf = new JComboBox<String>(ReferenceTablesFrame.UF_LIST);
        uf.setSelectedItem("MG");
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Mês de vencimento:"));
        bar.add(prev);
        bar.add(month);
        bar.add(next);
        bar.add(regime);
        bar.add(new JLabel("UF:"));
        bar.add(uf);
        final DefaultTableModel model = ReferenceTablesFrame.readOnlyModel("Vencimento", "Dia", "Obrigação", "Competência", "Para quem");
        final JTable table = ReferenceTablesFrame.table(model, 85, 70, 330, 130, 300);
        final List<ObligationService.Due> shown = new ArrayList<ObligationService.Due>();
        final JTextArea detail = ReferenceTablesFrame.area("Vencimentos calculados pelas regras do catálogo. Prazos da UF (quando não "
                + "cadastrados) e do município não aparecem aqui.");
        Runnable reload = () -> {
            YearMonth m;
            try {
                m = YearMonth.parse(month.getText().trim(), MONTH);
            } catch (Exception ex) {
                detail.setText("Mês inválido: use mm/aaaa.");
                return;
            }
            model.setRowCount(0);
            shown.clear();
            String code = regime.getSelectedIndex() == 0 ? Obligation.LP : Obligation.LR;
            for (ObligationService.Due d : service.calendar(m, code, (String) uf.getSelectedItem())) {
                shown.add(d);
                model.addRow(new Object[]{BR.format(d.date()), d.date().getDayOfWeek().getDisplayName(TextStyle.FULL, PT),
                        d.obligation().name(), d.period(), d.mandatory() ? "Todos do regime" : "Condicional: "
                        + d.obligation().condition()});
            }
            detail.setText(shown.size() + " vencimento(s) em " + MONTH.format(m) + " para o "
                    + regime.getSelectedItem() + " em " + uf.getSelectedItem() + ".");
        };
        prev.addActionListener(e -> shift(month, -1, reload));
        next.addActionListener(e -> shift(month, 1, reload));
        month.addActionListener(e -> reload.run());
        regime.addActionListener(e -> reload.run());
        uf.addActionListener(e -> reload.run());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                ObligationService.Due d = shown.get(table.convertRowIndexToModel(row));
                detail.setText("Vence em " + BR.format(d.date()) + " (competência " + d.period() + ")\n\n" + detail(d.obligation()));
                detail.setCaretPosition(0);
            }
        });
        reload.run();
        return ReferenceTablesFrame.withDetail(bar, table, detail);
    }

    static void shift(JTextField month, int delta, Runnable reload) {
        try {
            month.setText(MONTH.format(YearMonth.parse(month.getText().trim(), MONTH).plusMonths(delta)));
        } catch (Exception ignored) {
            month.setText(MONTH.format(LocalDate.now()));
        }
        reload.run();
    }

    // ------------------------------------------------------------------ feriados

    private JPanel holidaysTab() {
        final JTextField year = new JTextField(String.valueOf(LocalDate.now().getYear()), 5);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Ano:"));
        bar.add(year);
        final DefaultTableModel model = ReferenceTablesFrame.readOnlyModel("Data", "Dia", "Feriado");
        final JTable table = ReferenceTablesFrame.table(model, 90, 110, 400);
        final JTextArea detail = ReferenceTablesFrame.area("Feriados nacionais (Lei 662/1949, Lei 6.802/1980, Lei 14.759/2023) e "
                + "pontos facultativos sem expediente bancário (Carnaval, Corpus Christi), usados no cálculo de dias úteis.");
        Runnable reload = () -> {
            model.setRowCount(0);
            try {
                for (Map.Entry<LocalDate, String> h : BusinessCalendar.holidays(Integer.parseInt(year.getText().trim())).entrySet()) {
                    model.addRow(new Object[]{BR.format(h.getKey()), h.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL, PT), h.getValue()});
                }
            } catch (NumberFormatException ex) {
                detail.setText("Ano inválido.");
            }
        };
        year.addActionListener(e -> reload.run());
        reload.run();
        return ReferenceTablesFrame.withDetail(bar, table, detail);
    }
}
