package br.com.facilitasig.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkAreaTest {

    private final WorkArea area = ApuracaoFiscalArea.create();

    @Test
    void featureIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Feature feature : area.allFeatures()) {
            assertTrue(ids.add(feature.id()), "id repetido: " + feature.id());
        }
    }

    @Test
    void everyStepFeatureAppearsInAMenu() {
        for (Step step : area.steps()) {
            assertTrue(area.findFeature(step.feature().id()).isPresent(),
                    "etapa sem funcionalidade em menu: " + step.title());
        }
    }

    @Test
    void toolbarFollowsTheLinearSteps() {
        assertEquals(area.steps().size(), area.toolbar().size());
        for (int i = 0; i < area.steps().size(); i++) {
            assertEquals(area.steps().get(i).feature(), area.toolbar().get(i));
        }
    }

    @Test
    void separatorsAreNotListedAsFeatures() {
        assertFalse(area.allFeatures().contains(Feature.SEPARATOR));
    }

    @Test
    void registryExposesAtLeastOneArea() {
        assertFalse(WorkAreas.all().isEmpty());
    }

    @Test
    void onlyGeneralFeaturesWorkWithoutACompany() {
        java.util.Set<String> general = new java.util.HashSet<String>(java.util.Arrays.asList(
                "cad.empresa", "conf.regras", "cons.tabelas", "oa.catalogo", "cons.calendario", "cons.legislacao", "util.chave", "util.xml", "cfg.sistema", "cfg.pastas"));
        for (Feature f : area.allFeatures()) {
            assertEquals(!general.contains(f.id()), f.companyScoped(), f.id());
        }
        assertTrue(area.findFeature("mov.entradas").get().companyScoped());
        assertTrue(area.findFeature("oa.efdcontrib").get().companyScoped());
        assertFalse(area.findFeature("cad.empresa").get().companyScoped());
    }
}
