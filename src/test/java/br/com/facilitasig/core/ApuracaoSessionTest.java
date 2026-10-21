package br.com.facilitasig.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApuracaoSessionTest {

    private ApuracaoSession session;

    @BeforeEach
    void setUp() {
        session = new ApuracaoSession(ApuracaoFiscalArea.create().steps());
    }

    @Test
    void startsAtFirstStepAndLocksTheRest() {
        assertEquals(0, session.currentIndex());
        assertTrue(session.isUnlocked(0));
        assertFalse(session.isUnlocked(1));
        assertEquals(ApuracaoSession.Status.CURRENT, session.statusOf(0));
        assertEquals(ApuracaoSession.Status.PENDING, session.statusOf(1));
    }

    @Test
    void cannotCompleteALockedStep() {
        assertFalse(session.complete(2));
        assertEquals(0, session.currentIndex());
    }

    @Test
    void completingAdvancesInOrder() {
        assertTrue(session.complete(0));
        assertEquals(1, session.currentIndex());
        assertTrue(session.isUnlocked(1));
        assertFalse(session.isUnlocked(2));
    }

    @Test
    void reopeningAStepReopensTheFollowingOnes() {
        for (int i = 0; i < 4; i++) {
            assertTrue(session.complete(i));
        }
        assertEquals(4, session.currentIndex());
        session.reopen(1);
        assertEquals(1, session.currentIndex());
        assertEquals(ApuracaoSession.Status.DONE, session.statusOf(0));
        assertEquals(ApuracaoSession.Status.CURRENT, session.statusOf(1));
        assertEquals(ApuracaoSession.Status.PENDING, session.statusOf(3));
    }

    @Test
    void freeModeUnlocksEverything() {
        session.setFreeMode(true);
        assertTrue(session.isUnlocked(6));
        assertTrue(session.complete(6));
        assertEquals(0, session.currentIndex());
    }

    @Test
    void allDoneWhenEveryStepIsCompleted() {
        for (int i = 0; i < session.steps().size(); i++) {
            assertTrue(session.complete(i));
        }
        assertTrue(session.allDone());
    }

    @Test
    void notifiesListeners() {
        int[] calls = {0};
        session.addListener(() -> calls[0]++);
        session.complete(0);
        session.reopen(0);
        assertEquals(2, calls[0]);
    }
}
