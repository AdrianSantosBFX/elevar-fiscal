package br.com.facilitasig.ui;

import br.com.facilitasig.core.Feature;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Ícones desenhados em código (sem arquivos de imagem): quadrado arredondado colorido com um símbolo. */
public final class Icons {

    private Icons() {
    }

    public static Icon of(Feature feature, int size) {
        return new ImageIcon(draw(feature.glyph(), feature.tone().color(), size));
    }

    public static BufferedImage draw(String glyph, Color base, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int arc = Math.max(6, size / 4);
        g.setPaint(new GradientPaint(0, 0, base.brighter(), 0, size, base.darker()));
        g.fillRoundRect(1, 1, size - 2, size - 2, arc, arc);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(1, 1, size - 3, size - 3, arc, arc);

        float fontSize = glyph.length() <= 1 ? size * 0.58f : glyph.length() == 2 ? size * 0.44f : size * 0.34f;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(fontSize)));
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(glyph)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(Color.WHITE);
        g.drawString(glyph, x, y);
        g.dispose();
        return image;
    }

    /** Ícone da aplicação (janela e barra de tarefas). */
    public static BufferedImage appIcon(int size) {
        return draw("EF", new Color(0x1F5FA8), size);
    }
}
