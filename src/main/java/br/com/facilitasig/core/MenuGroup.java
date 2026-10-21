package br.com.facilitasig.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Um menu do topo do sistema, com suas funcionalidades (e separadores). */
public final class MenuGroup {

    private final String title;
    private final List<Feature> items;

    public MenuGroup(String title, List<Feature> items) {
        this.title = title;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public String title() {
        return title;
    }

    public List<Feature> items() {
        return items;
    }
}
