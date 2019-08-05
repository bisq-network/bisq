package bisq.desktop;

import bisq.desktop.app.BisqAppModule;
import bisq.desktop.common.view.CachingViewLoader;

import bisq.core.app.AvoidStandbyModeService;
import bisq.core.app.BisqEnvironment;

import org.springframework.mock.env.MockPropertySource;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Test;

public class GuiceSetupTest {
    @Test
    public void testGuiceSetup() {
        BisqAppModule module = new BisqAppModule(new BisqEnvironment(new MockPropertySource()));
        Injector injector = Guice.createInjector(module);
        injector.getInstance(CachingViewLoader.class);
        injector.getInstance(AvoidStandbyModeService.class);
    }
}
