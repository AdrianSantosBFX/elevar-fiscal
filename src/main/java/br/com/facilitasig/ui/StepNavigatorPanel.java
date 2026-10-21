package br.com.facilitasig.ui;

import br.com.facilitasig.core.ApuracaoSession;
import br.com.facilitasig.core.ApuracaoSession.Status;
import br.com.facilitasig.core.Step;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/** Painel lateral com o roteiro linear de etapas da apuração. */
public class StepNavigatorPanel extends JPanel {

    private static final Color DONE = new Color(0x2E8B57);
    private static final Color CURRENT = new Color(0x1F5FA8);
    private static final Color PENDING = new Color(0xA7B0BA);

    private final ApuracaoSession session;
    private final IntConsumer onOpen;
    private final JPanel list = new JPanel();

    public StepNavigatorPanel(ApuracaoSession session, IntConsumer onOpen) {
        super(new BorderLayout());
        this.session = session;
        this.onOpen = onOpen;
        setPreferredSize(new Dimension(270, 100));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xC8CDD3)));

        JLabel title = new JLabel("Roteiro de apuração");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setBorder(BorderFactory.createEmptyBorder(12, 14, 2, 14));
        JLabel subtitle = new JLabel("Siga as etapas na ordem.");
        subtitle.setForeground(new Color(0x6B7785));
        subtitle.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(subtitle);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        add(header, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        add(list, BorderLayout.CENTER);

        JCheckBox free = new JCheckBox("Modo livre (liberar todas as etapas)");
        free.setToolTipText("Para desenvolvimento: permite abrir qualquer etapa sem concluir as anteriores.");
        free.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        free.addActionListener(e -> session.setFreeMode(free.isSelected()));
        add(free, BorderLayout.SOUTH);

        session.addListener(this::refresh);
        refresh();
    }

    public void refresh() {
        list.removeAll();
        int current = session.currentIndex();
        for (int i = 0; i < session.steps().size(); i++) {
            list.add(row(i, session.steps().get(i), session.statusOf(i), session.isUnlocked(i), i == current));
        }
        list.add(Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    private JComponent row(int index, Step step, Status status, boolean unlocked, boolean isCurrent) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setAlignmentX(LEFT_ALIGNMENT);
        if (isCurrent) {
            row.setBackground(new Color(0xE8F0FA));
        } else {
            row.setOpaque(false);
        }

        row.add(new Badge(index + 1, status), BorderLayout.WEST);

        JLabel name = new JLabel(step.title());
        name.setFont(name.getFont().deriveFont(isCurrent ? Font.BOLD : Font.PLAIN));
        name.setForeground(unlocked ? Color.BLACK : new Color(0x8A94A0));
        JLabel hint = new JLabel("<html><div style='width:160px'>" + escape(step.objective()) + "</div></html>");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(new Color(0x6B7785));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        name.setAlignmentX(LEFT_ALIGNMENT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        text.add(name);
        text.add(hint);
        row.add(text, BorderLayout.CENTER);

        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.accept(index);
            }
        });
        return row;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Círculo numerado; verde com visto quando concluída, azul quando atual, cinza quando pendente. */
    private static final class Badge extends JComponent {
        private final int number;
        private final Status status;

        Badge(int number, Status status) {
            this.number = number;
            this.status = status;
            setPreferredSize(new Dimension(26, 26));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int d = 24;
            int y = 1;
            if (status == Status.DONE) {
                g.setColor(DONE);
                g.fillOval(1, y, d, d);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{7, 11, 19}, new int[]{14, 18, 9}, 3);
            } else if (status == Status.CURRENT) {
                g.setColor(CURRENT);
                g.fillOval(1, y, d, d);
                drawNumber(g, Color.WHITE);
            } else {
                g.setColor(PENDING);
                g.setStroke(new BasicStroke(1.6f));
                g.drawOval(2, y + 1, d - 2, d - 2);
                drawNumber(g, PENDING.darker());
            }
            g.dispose();
        }

        private void drawNumber(Graphics2D g, Color color) {
            g.setColor(color);
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            String s = String.valueOf(number);
            int w = g.getFontMetrics().stringWidth(s);
            g.drawString(s, 13 - w / 2, 18);
        }
    }
}
