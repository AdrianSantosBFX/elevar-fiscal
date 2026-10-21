package br.com.facilitasig.core;

import java.awt.Color;

/** Cor de identificação de uma funcionalidade (usada nos ícones). */
public enum Tone {
    BLUE(0x2F6FB5),
    GREEN(0x2E8B57),
    ORANGE(0xE07B1F),
    RED(0xC0392B),
    PURPLE(0x7D4FA8),
    TEAL(0x1F8A8A),
    GRAY(0x6B7785);

    private final Color color;

    Tone(int rgb) {
        this.color = new Color(rgb);
    }

    public Color color() {
        return color;
    }
}
