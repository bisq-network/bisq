package bisq.desktop;

import bisq.desktop.app.BisqAppModule;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.guice.InjectorViewFactory;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.main.funds.transactions.TradableRepository;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.main.presentation.DaoPresentation;
import bisq.desktop.main.presentation.MarketPricePresentation;
import bisq.desktop.util.Transitions;

import bisq.core.app.AvoidStandbyModeService;
import bisq.core.app.P2PNetworkSetup;
import bisq.core.app.TorSetup;
import bisq.core.app.WalletAppSetup;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.notifications.MobileMessageEncryption;
import bisq.core.notifications.MobileModel;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.notifications.MobileNotificationValidator;
import bisq.core.notifications.alerts.MyOfferTakenEvents;
import bisq.core.notifications.alerts.TradeEvents;
import bisq.core.notifications.alerts.market.MarketAlerts;
import bisq.core.notifications.alerts.price.PriceAlert;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.TradeLimits;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.support.dispute.arbitration.ArbitrationDisputeListService;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorService;
import bisq.core.support.dispute.mediation.MediationDisputeListService;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.mediation.mediator.MediatorService;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.BsqFormatter;

import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.ClockWatcher;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GuiceSetupTest {

    private Injector injector;

    @Before
    public void setUp() {
        Res.setup();
        CurrencyUtil.setup();

        injector = Guice.createInjector(new BisqAppModule(new Config()));
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
        assertSingleton(BsqFormatter.class);
        assertSingleton(TorNetworkSettingsWindow.class);
        assertSingleton(MarketPricePresentation.class);
        assertSingleton(ViewLoader.class);
        assertSingleton(DaoPresentation.class);
        assertSingleton(Transitions.class);
        assertSingleton(TradableRepository.class);
        assertSingleton(BondingViewUtils.class);

        // core module
//        assertSingleton(BisqSetup.class); // this is a can of worms
//        assertSingleton(DisputeMsgEvents.class);
        assertSingleton(TorSetup.class);
        assertSingleton(P2PNetworkSetup.class);
        assertSingleton(WalletAppSetup.class);
        assertSingleton(TradeLimits.class);
        assertSingleton(KeyStorage.class);
        assertSingleton(KeyRing.class);
        assertSingleton(PubKeyRing.class);
        assertSingleton(User.class);
        assertSingleton(ClockWatcher.class);
        assertSingleton(Preferences.class);
        assertSingleton(BridgeAddressProvider.class);
        assertSingleton(CorruptedStorageFileHandler.class);
        assertSingleton(AvoidStandbyModeService.class);
        assertSingleton(DefaultSeedNodeRepository.class);
        assertSingleton(SeedNodeRepository.class);
        assertTrue(injector.getInstance(SeedNodeRepository.class) instanceof DefaultSeedNodeRepository);
        assertSingleton(CoreNetworkProtoResolver.class);
        assertSingleton(NetworkProtoResolver.class);
        assertTrue(injector.getInstance(NetworkProtoResolver.class) instanceof CoreNetworkProtoResolver);
        assertSingleton(PersistenceProtoResolver.class);
        assertSingleton(CorePersistenceProtoResolver.class);
        assertTrue(injector.getInstance(PersistenceProtoResolver.class) instanceof CorePersistenceProtoResolver);
        assertSingleton(MobileMessageEncryption.class);
        assertSingleton(MobileNotificationService.class);
        assertSingleton(MobileNotificationValidator.class);
        assertSingleton(MobileModel.class);
        assertSingleton(MyOfferTakenEvents.class);
        assertSingleton(TradeEvents.class);
        assertSingleton(PriceAlert.class);
        assertSingleton(MarketAlerts.class);
        assertSingleton(ChargeBackRisk.class);
        assertSingleton(ArbitratorService.class);
        assertSingleton(ArbitratorManager.class);
        assertSingleton(ArbitrationManager.class);
        assertSingleton(ArbitrationDisputeListService.class);
        assertSingleton(MediatorService.class);
        assertSingleton(MediatorManager.class);
        assertSingleton(MediationManager.class);
        assertSingleton(MediationDisputeListService.class);
        assertSingleton(TraderChatManager.class);

        assertNotSingleton(PersistenceManager.class);
    }

    private void assertSingleton(Class<?> type) {
        assertSame(injector.getInstance(type), injector.getInstance(type));
    }

    private void assertNotSingleton(Class<?> type) {
        assertNotSame(injector.getInstance(type), injector.getInstance(type));
    }
}
