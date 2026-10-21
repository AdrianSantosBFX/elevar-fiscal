package br.com.facilitasig.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Área de Trabalho: reúne menus, botões de acesso rápido e (opcionalmente) uma
 * rotina linear de etapas. Para criar uma nova área, basta instanciar esta
 * classe e registrá-la em {@link WorkAreas}.
 */
public final class WorkArea {

    private final String id;
    private final String name;
    private final String description;
    private final List<MenuGroup> menus;
    private final List<Feature> toolbar;
    private final List<Step> steps;

    /**
     * @param id          identificador estável
     * @param name        nome exibido no menu "Áreas de Trabalho"
     * @param description descrição curta
     * @param menus       menus do topo (na ordem em que aparecem)
     * @param toolbar     funcionalidades com botão grande na barra de ferramentas
     * @param steps       etapas da rotina linear (pode ser vazia)
     */
    public WorkArea(String id, String name, String description,
                    List<MenuGroup> menus, List<Feature> toolbar, List<Step> steps) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.menus = Collections.unmodifiableList(new ArrayList<>(menus));
        this.toolbar = Collections.unmodifiableList(new ArrayList<>(toolbar));
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<MenuGroup> menus() {
        return menus;
    }

    public List<Feature> toolbar() {
        return toolbar;
    }

    public List<Step> steps() {
        return steps;
    }

    /** Todas as funcionalidades da área, sem repetição e sem separadores. */
    public List<Feature> allFeatures() {
        List<Feature> result = new ArrayList<>();
        for (MenuGroup group : menus) {
            for (Feature feature : group.items()) {
                if (!feature.isSeparator() && !result.contains(feature)) {
                    result.add(feature);
                }
            }
        }
        return result;
    }

    public Optional<Feature> findFeature(String featureId) {
        for (Feature feature : allFeatures()) {
            if (feature.id().equals(featureId)) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }

    /** Índice da etapa cuja funcionalidade principal é a informada, ou -1. */
    public int stepIndexOf(Feature feature) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).feature().equals(feature)) {
                return i;
            }
        }
        return -1;
    }
}
