package bisq.desktop;

import bisq.desktop.app.BisqAppModule;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.guice.InjectorViewFactory;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.main.funds.transactions.DisplayedTransactionsFactory;
import bisq.desktop.main.funds.transactions.TradableRepository;
import bisq.desktop.main.funds.transactions.TransactionAwareTradableFactory;
import bisq.desktop.main.funds.transactions.TransactionListItemFactory;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.main.presentation.DaoPresentation;
import bisq.desktop.main.presentation.MarketPricePresentation;
import bisq.desktop.util.Transitions;

import bisq.core.app.AvoidStandbyModeService;
import bisq.core.app.BisqEnvironment;
import bisq.core.app.P2PNetworkSetup;
import bisq.core.app.TorSetup;
import bisq.core.app.WalletAppSetup;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.payment.TradeLimits;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.ClockWatcher;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.storage.CorruptedDatabaseFilesHandler;

import org.springframework.mock.env.MockPropertySource;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class GuiceSetupTest {

    private Injector injector;

    @Before
    public void setUp() {
        Res.setup();
        CurrencyUtil.setup();

        injector = Guice.createInjector(new BisqAppModule(new BisqEnvironment(new MockPropertySource())));
    }

    @Test
    public void testGuiceSetup() {
        injector.getInstance(AvoidStandbyModeService.class);
        // desktop module
        assertSingleton(OfferBook.class);
        assertSingleton(CachingViewLoader.class);
        assertSingleton(Navigation.class);
        assertSingleton(InjectorViewFactory.class);
        assertSingleton(NotificationCenter.class);
        assertSingleton(BSFormatter.class);
        assertSingleton(BsqFormatter.class);
        assertSingleton(TorNetworkSettingsWindow.class);
        assertSingleton(MarketPricePresentation.class);
        assertSingleton(ViewLoader.class);
        assertSingleton(DaoPresentation.class);
        assertSingleton(Transitions.class);
        assertSingleton(TradableRepository.class);
        assertSingleton(TransactionListItemFactory.class);
        assertSingleton(TransactionAwareTradableFactory.class);
        assertSingleton(DisplayedTransactionsFactory.class);
        assertSingleton(BondingViewUtils.class);

        // core module
//        assertSingleton(BisqSetup.class); // this is a can of worms
        assertSingleton(TorSetup.class);
        assertSingleton(P2PNetworkSetup.class);
        assertSingleton(WalletAppSetup.class);
        assertSingleton(TradeLimits.class);
        assertSingleton(KeyStorage.class);
        assertSingleton(KeyRing.class);
        assertSingleton(User.class);
        assertSingleton(ClockWatcher.class);
        assertSingleton(Preferences.class);
        assertSingleton(BridgeAddressProvider.class);
        assertSingleton(CorruptedDatabaseFilesHandler.class);
        assertSingleton(AvoidStandbyModeService.class);
        assertSingleton(DefaultSeedNodeRepository.class);
        assertSingleton(SeedNodeRepository.class);
    }

    private void assertSingleton(Class<?> type) {
        assertSame(injector.getInstance(type), injector.getInstance(type));
    }
}
