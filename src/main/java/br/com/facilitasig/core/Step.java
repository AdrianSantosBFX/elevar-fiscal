package br.com.facilitasig.core;

/**
 * Etapa da rotina linear de apuração. Cada etapa tem uma funcionalidade
 * principal, que é aberta quando a etapa é acionada.
 */
public final class Step {

    private final String title;
    private final String objective;
    private final Feature feature;

    /**
     * @param title     nome da etapa
     * @param objective o que precisa estar pronto ao final da etapa
     * @param feature   funcionalidade principal da etapa
     */
    public Step(String title, String objective, Feature feature) {
        this.title = title;
        this.objective = objective;
        this.feature = feature;
    }

    public String title() {
        return title;
    }

    public String objective() {
        return objective;
    }

    public Feature feature() {
        return feature;
    }
}
