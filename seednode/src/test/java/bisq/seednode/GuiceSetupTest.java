package bisq.seednode;

import bisq.core.app.misc.AppSetupWithP2PAndDAO;
import bisq.core.app.misc.ModuleForAppWithP2p;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;

import bisq.common.config.Config;

import com.google.inject.Guice;

import org.junit.Test;

public class GuiceSetupTest {
    @Test
    public void testGuiceSetup() {
        Res.setup();
        CurrencyUtil.setup();

        ModuleForAppWithP2p module = new ModuleForAppWithP2p(new Config());
        Guice.createInjector(module).getInstance(AppSetupWithP2PAndDAO.class);
    }
}
