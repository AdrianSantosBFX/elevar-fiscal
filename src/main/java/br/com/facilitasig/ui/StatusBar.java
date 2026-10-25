package br.com.facilitasig.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

/** Barra de status: área de trabalho, empresa, período, etapa atual, avisos e versão. */
public class StatusBar extends JPanel {

    private final JLabel area = new JLabel();
    private final JLabel company = new JLabel("Empresa: (nenhuma selecionada)");
    private final JLabel period = new JLabel("Período: —");
    private final JLabel step = new JLabel();
    private final JLabel version = new JLabel();
    private final JLabel message = new JLabel();

    public StatusBar(String versionText) {
        super(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xC8CDD3)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        left.setOpaque(false);
        left.add(area);
        left.add(separator());
        left.add(company);
        left.add(separator());
        left.add(period);
        left.add(separator());
        left.add(step);
        add(left, BorderLayout.WEST);

        message.setForeground(new Color(0xB4570A));
        add(message, BorderLayout.CENTER);

        version.setText(versionText);
        version.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        version.setForeground(new Color(0x6B7785));
        add(version, BorderLayout.EAST);
    }

    private static JSeparator separator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 14));
        return s;
    }

    public void setArea(String name) {
        area.setText("Área de trabalho: " + name);
    }

    public void setCompany(String name) {
        company.setText("Empresa: " + (name == null || name.isEmpty() ? "(nenhuma selecionada)" : name));
    }

    /** Aviso do sistema (ex.: falha ao carregar as tabelas de referência). */
    public void setMessage(String text) {
        message.setText(text == null ? "" : text);
        message.setToolTipText(text);
    }

    public void setStep(String text) {
        step.setText(text);
    }
}
