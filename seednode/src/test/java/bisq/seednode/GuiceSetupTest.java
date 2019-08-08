package bisq.seednode;

import bisq.core.app.BisqEnvironment;
import bisq.core.app.misc.AppSetupWithP2PAndDAO;
import bisq.core.app.misc.ModuleForAppWithP2p;

import org.springframework.mock.env.MockPropertySource;

import com.google.inject.Guice;

import org.junit.Test;

public class GuiceSetupTest {
    @Test
    public void testGuiceSetup() {
        ModuleForAppWithP2p module = new ModuleForAppWithP2p(new BisqEnvironment(new MockPropertySource()));
        Guice.createInjector(module).getInstance(AppSetupWithP2PAndDAO.class);
    }
}
