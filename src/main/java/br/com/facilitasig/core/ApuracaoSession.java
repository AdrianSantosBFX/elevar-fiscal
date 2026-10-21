package br.com.facilitasig.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Estado da rotina linear de apuração: quais etapas estão concluídas e qual é
 * a etapa atual. Em memória apenas (a versão 0.0.1 não tem persistência).
 *
 * <p>Regra linear: uma etapa só pode ser aberta se todas as anteriores estiverem
 * concluídas. O "modo livre" (para desenvolvimento) desliga essa trava.
 */
public class ApuracaoSession {

    public enum Status { DONE, CURRENT, PENDING }

    private final List<Step> steps;
    private final Set<Integer> done = new TreeSet<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean freeMode;

    public ApuracaoSession(List<Step> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public List<Step> steps() {
        return steps;
    }

    /** Primeira etapa não concluída; igual ao total de etapas quando tudo está concluído. */
    public int currentIndex() {
        for (int i = 0; i < steps.size(); i++) {
            if (!done.contains(i)) {
                return i;
            }
        }
        return steps.size();
    }

    public boolean allDone() {
        return currentIndex() == steps.size();
    }

    public Status statusOf(int index) {
        if (done.contains(index)) {
            return Status.DONE;
        }
        return index == currentIndex() ? Status.CURRENT : Status.PENDING;
    }

    public boolean isUnlocked(int index) {
        return freeMode || index <= currentIndex();
    }

    /** Conclui a etapa informada, se estiver liberada. */
    public boolean complete(int index) {
        if (index < 0 || index >= steps.size() || !isUnlocked(index)) {
            return false;
        }
        done.add(index);
        fireChanged();
        return true;
    }

    /** Reabre a etapa; as seguintes também voltam a pendente, para manter a ordem linear. */
    public void reopen(int index) {
        done.removeIf(i -> i >= index);
        fireChanged();
    }

    public void reset() {
        done.clear();
        fireChanged();
    }

    public boolean isFreeMode() {
        return freeMode;
    }

    public void setFreeMode(boolean freeMode) {
        this.freeMode = freeMode;
        fireChanged();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }
}
