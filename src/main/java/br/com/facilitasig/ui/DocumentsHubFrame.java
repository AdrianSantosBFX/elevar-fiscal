package br.com.facilitasig.ui;

import br.com.facilitasig.core.Feature;
import br.com.facilitasig.fiscal.Direction;
import br.com.facilitasig.fiscal.DocStatus;
import br.com.facilitasig.fiscal.FiscalDocRepository;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;

/** Etapa 3 da rotina: resume as notas de entrada e de saída e abre cada módulo de importação. */
public class DocumentsHubFrame extends JInternalFrame {

    private final long companyId;
    private final FiscalDocRepository repository;
    private final JLabel entrada = new JLabel();
    private final JLabel saida = new JLabel();

    public DocumentsHubFrame(Feature feature, long companyId, String companyName, FiscalDocRepository repository,
                             Runnable openEntrada, Runnable openSaida, PlaceholderFrame.StepActions actions) {
        super(feature.title(), true, true, true, true);
        this.companyId = companyId;
        this.repository = repository;
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Documentos fiscais do período — " + companyName);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel sub = new JLabel("Importe as notas de entrada e de saída (NF-e e NFS-e). Resolva os erros para concluir a etapa.");
        sub.setForeground(new Color(0x6B7785));
        JPanel head = new JPanel(new BorderLayout());
        head.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.CENTER);
        add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 0));
        grid.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 16));
        grid.add(card("Notas de ENTRADA", entrada, "Abrir importação de entradas", openEntrada));
        grid.add(card("Notas de SAÍDA", saida, "Abrir importação de saídas", openSaida));
        add(grid, BorderLayout.CENTER);

        if (actions != null) {
            JPanel step = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            JButton reopen = new JButton("Reabrir etapa");
            reopen.addActionListener(e -> actions.reopen());
            JButton complete = new JButton(actions.isDone() ? "Etapa concluída" : "Concluir etapa e avançar");
            complete.setEnabled(!actions.isDone());
            complete.addActionListener(e -> actions.complete());
            step.add(reopen);
            step.add(complete);
            add(step, BorderLayout.SOUTH);
        }
        addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                refresh();
            }
        });
        refresh();
        setPreferredSize(new Dimension(760, 330));
        pack();
        setSize(760, 330);
    }

    private JPanel card(String name, JLabel counts, String button, Runnable action) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xC8CDD3)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel t = new JLabel(name);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 14f));
        p.add(t, BorderLayout.NORTH);
        p.add(counts, BorderLayout.CENTER);
        JButton b = new JButton(button);
        b.addActionListener(e -> action.run());
        p.add(b, BorderLayout.SOUTH);
        return p;
    }

    private void refresh() {
        entrada.setText(text(repository.counts(companyId, Direction.ENTRADA)));
        saida.setText(text(repository.counts(companyId, Direction.SAIDA)));
    }

    private static String text(Map<DocStatus, Integer> c) {
        int total = 0;
        for (int v : c.values()) {
            total += v;
        }
        return "<html>Importadas: <b>" + total + "</b><br>Conformes: " + c.get(DocStatus.CONFORME)
                + "<br>Com alerta: " + c.get(DocStatus.ALERTA) + "<br>Com erro: <b>" + c.get(DocStatus.ERRO)
                + "</b><br>Duplicadas: " + c.get(DocStatus.DUPLICADA) + "</html>";
    }
}
