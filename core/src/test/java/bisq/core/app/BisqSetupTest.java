package bisq.core.app;

import bisq.core.account.sign.SignedWitnessStorageService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.Alert;
import bisq.core.alert.AlertManager;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.P2PService;

import bisq.common.config.Config;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BisqSetupTest {

    @Test
    void doesNotTreatUpgradeFrom1922AsDowngrade() {
        assertFalse(BisqSetup.hasDowngraded("1.9.22"));
    }

    @Test
    void showsUpdatePopupOnlyOncePerSessionPerVersion() {
        Preferences preferences = mock(Preferences.class);
        when(preferences.showAgain(anyString())).thenReturn(true);
        BisqSetup bisqSetup = createBisqSetup(preferences);
        AtomicInteger popupCount = new AtomicInteger();
        bisqSetup.setDisplayUpdateHandler((alert, key) -> popupCount.incrementAndGet());

        Alert alert = new Alert("New version available", true, false, "99.99.98");

        // The first automatic delivery shows the popup
        bisqSetup.displayAlertIfPresent(alert, false);
        assertEquals(1, popupCount.get());

        // A re-delivery of an equal alert does not show it again
        bisqSetup.displayAlertIfPresent(new Alert("New version available", true, false, "99.99.98"), false);
        assertEquals(1, popupCount.get());

        // A re-published alert for the same version does not show it again
        bisqSetup.displayAlertIfPresent(new Alert("Reworded message", true, false, "99.99.98"), false);
        assertEquals(1, popupCount.get());

        // An explicit request via the footer link always shows it
        bisqSetup.displayAlertIfPresent(alert, true);
        assertEquals(2, popupCount.get());

        // An alert for a newer version shows again
        bisqSetup.displayAlertIfPresent(new Alert("Newer version available", true, false, "99.99.99"), false);
        assertEquals(3, popupCount.get());
    }

    @Test
    void footerLinkShowsPopupDespiteDontShowAgainPreference() {
        Preferences preferences = mock(Preferences.class);
        when(preferences.showAgain(anyString())).thenReturn(false);
        BisqSetup bisqSetup = createBisqSetup(preferences);
        AtomicInteger popupCount = new AtomicInteger();
        bisqSetup.setDisplayUpdateHandler((alert, key) -> popupCount.incrementAndGet());

        Alert alert = new Alert("New version available", true, false, "99.99.98");

        // An automatic delivery respects the dont-show-again preference
        bisqSetup.displayAlertIfPresent(alert, false);
        assertEquals(0, popupCount.get());

        // The footer link bypasses the stored preference
        bisqSetup.displayAlertIfPresent(alert, true);
        assertEquals(1, popupCount.get());
    }

    private static BisqSetup createBisqSetup(Preferences preferences) {
        return new BisqSetup(
                mock(DomainInitialisation.class),
                mock(P2PNetworkSetup.class),
                mock(WalletAppSetup.class),
                mock(WalletsManager.class),
                mock(WalletsSetup.class),
                mock(BtcWalletService.class),
                mock(P2PService.class),
                mock(PrivateNotificationManager.class),
                mock(SignedWitnessStorageService.class),
                mock(TradeManager.class),
                mock(OpenOfferManager.class),
                preferences,
                mock(User.class),
                mock(AlertManager.class),
                mock(UnconfirmedBsqChangeOutputListService.class),
                mock(Config.class),
                mock(AccountAgeWitnessService.class),
                mock(CoinFormatter.class),
                mock(LocalBitcoinNode.class),
                mock(AppStartupState.class),
                mock(Socks5ProxyProvider.class),
                mock(MediationManager.class),
                mock(RefundManager.class),
                mock(ArbitrationManager.class));
    }
}
