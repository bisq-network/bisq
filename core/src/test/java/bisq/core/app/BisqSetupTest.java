package bisq.core.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class BisqSetupTest {

    @Test
    void doesNotTreatUpgradeFrom1922AsDowngrade() {
        assertFalse(BisqSetup.hasDowngraded("1.9.22"));
    }
}
