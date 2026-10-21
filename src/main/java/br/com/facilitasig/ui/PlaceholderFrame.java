package br.com.facilitasig.ui;

import br.com.facilitasig.AppInfo;
import br.com.facilitasig.core.Feature;
import br.com.facilitasig.core.Step;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Janela provisória de uma funcionalidade (versão 0.0.1). Mostra o que a
 * funcionalidade fará e, quando é a tela principal de uma etapa, os botões de
 * conclusão da etapa.
 */
public class PlaceholderFrame extends JInternalFrame {

    /** Ações disponíveis para telas de etapa. */
    public interface StepActions {
        void complete();

        void reopen();

        boolean isDone();
    }

    public PlaceholderFrame(Feature feature, int stepNumber, Step step, StepActions actions) {
        super(feature.title(), true, true, true, true);
        setFrameIcon(Icons.of(feature, 16));
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        header.add(new JLabel(Icons.of(feature, 44)), BorderLayout.WEST);

        JLabel title = new JLabel(feature.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel(step == null
                ? "Funcionalidade"
                : "Etapa " + stepNumber + " da rotina de apuração: " + step.title());
        subtitle.setForeground(new Color(0x6B7785));
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(subtitle);
        header.add(titles, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JTextArea body = new JTextArea(buildText(feature, step));
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(4, 18, 12, 18));
        body.setFont(body.getFont().deriveFont(13f));
        add(body, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JLabel badge = new JLabel("Em construção — versão " + AppInfo.VERSION + ": sem funcionalidade ainda.");
        badge.setForeground(new Color(0xB35C00));
        badge.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        south.add(badge, BorderLayout.NORTH);

        if (actions != null) {
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
            JButton reopen = new JButton("Reabrir etapa");
            reopen.addActionListener(e -> actions.reopen());
            JButton complete = new JButton(actions.isDone() ? "Etapa concluída" : "Concluir etapa e avançar");
            complete.setEnabled(!actions.isDone());
            complete.addActionListener(e -> actions.complete());
            reopen.setEnabled(actions.isDone());
            buttons.add(reopen);
            buttons.add(complete);
            south.add(buttons, BorderLayout.SOUTH);
        }
        add(south, BorderLayout.SOUTH);

        setSize(640, 360);
    }

    private static String buildText(Feature feature, Step step) {
        StringBuilder sb = new StringBuilder();
        sb.append("O que esta funcionalidade fará\n").append(feature.description());
        if (step != null) {
            sb.append("\n\nAo final da etapa\n").append(step.objective());
        }
        return sb.toString();
    }
}
