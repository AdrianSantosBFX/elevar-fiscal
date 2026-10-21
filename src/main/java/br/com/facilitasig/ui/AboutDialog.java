package br.com.facilitasig.ui;

import br.com.facilitasig.AppInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import javax.swing.ImageIcon;

/** Janela "Sobre". */
public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(JFrame owner) {
        JDialog dialog = new JDialog(owner, "Sobre o " + AppInfo.NAME, true);
        JPanel content = new JPanel(new BorderLayout(14, 10));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 12, 18));

        content.add(new JLabel(new ImageIcon(Icons.appIcon(64))), BorderLayout.WEST);

        JLabel title = new JLabel(AppInfo.NAME + " — versão " + AppInfo.VERSION);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JTextArea text = new JTextArea(
                "Casca do sistema de conferência fiscal autônoma.\n\n"
                        + "Hipótese de pesquisa: " + AppInfo.HYPOTHESIS + "\n\n"
                        + "Esta versão contém apenas a estrutura de áreas de trabalho, menus e rotina linear. "
                        + "Nenhuma funcionalidade de backend foi implementada.");
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setColumns(44);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(title, BorderLayout.NORTH);
        center.add(text, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JButton ok = new JButton("Fechar");
        ok.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(ok);
        content.add(south, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
