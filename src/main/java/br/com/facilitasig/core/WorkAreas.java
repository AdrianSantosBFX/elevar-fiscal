package br.com.facilitasig.core;

import java.util.Collections;
import java.util.List;

/** Registro das Áreas de Trabalho disponíveis. */
public final class WorkAreas {

    private WorkAreas() {
    }

    public static List<WorkArea> all() {
        return Collections.singletonList(ApuracaoFiscalArea.create());
    }
}
