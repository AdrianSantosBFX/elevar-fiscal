package br.com.facilitasig.core;

import java.util.Objects;

/**
 * Uma funcionalidade do sistema (tela). Na versão 0.0.1 todas abrem uma janela
 * provisória; no futuro cada uma terá a sua tela e o seu serviço.
 *
 * <p>Duas funcionalidades são iguais quando têm o mesmo {@code id}.
 */
public final class Feature {

    /** Marcador de separador nos menus. */
    public static final Feature SEPARATOR = new Feature("-", "-", "-", "", "", Tone.GRAY);

    private final String id;
    private final String title;
    private final String shortTitle;
    private final String description;
    private final String glyph;
    private final Tone tone;

    /**
     * @param id          identificador estável (ex.: "conf.autonoma")
     * @param title       título completo (menus e janela)
     * @param shortTitle  título curto (botões da barra de ferramentas)
     * @param description o que a funcionalidade fará
     * @param glyph       símbolo curto desenhado no ícone
     * @param tone        cor do ícone
     */
    public Feature(String id, String title, String shortTitle, String description, String glyph, Tone tone) {
        this.id = id;
        this.title = title;
        this.shortTitle = shortTitle;
        this.description = description;
        this.glyph = glyph;
        this.tone = tone;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String shortTitle() {
        return shortTitle;
    }

    public String description() {
        return description;
    }

    public String glyph() {
        return glyph;
    }

    public Tone tone() {
        return tone;
    }

    public boolean isSeparator() {
        return this == SEPARATOR;
    }

    public Feature withShortTitle(String newShortTitle) {
        return new Feature(id, title, newShortTitle, description, glyph, tone);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Feature && ((Feature) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Feature[" + id + "]";
    }
}
