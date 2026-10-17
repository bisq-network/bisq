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
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.config.Config;

import javafx.beans.property.SimpleBooleanProperty;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    // A local node that the wallet cannot use is replaced by other Bitcoin nodes only after the user agreed
    @Test
    void asksBeforeContinuingWithoutAnUnusableLocalNode() {
        LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);
        when(localBitcoinNode.getUnusableReason()).thenReturn(Optional.of("bloom filters are not enabled"));
        BisqSetup bisqSetup = createBisqSetup(mock(Preferences.class), localBitcoinNode);
        AtomicReference<String> askedReason = new AtomicReference<>();
        AtomicReference<Runnable> continueWithoutLocalNode = new AtomicReference<>();
        bisqSetup.setLocalBitcoinNodeUnusableHandler((reason, continueHandler) -> {
            askedReason.set(reason);
            continueWithoutLocalNode.set(continueHandler);
        });
        AtomicInteger nextStepCount = new AtomicInteger();

        bisqSetup.maybeAskToContinueWithoutLocalBitcoinNode(nextStepCount::incrementAndGet);

        assertEquals("bloom filters are not enabled", askedReason.get());
        assertEquals(0, nextStepCount.get(), "the wallet does not start before the user agreed");
        continueWithoutLocalNode.get().run();
        assertEquals(1, nextStepCount.get());
    }

    @Test
    void doesNotContinueWithoutAnUnusableLocalNodeWhenNobodyCanBeAsked() {
        LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);
        when(localBitcoinNode.getUnusableReason()).thenReturn(Optional.of("bloom filters are not enabled"));
        BisqSetup bisqSetup = createBisqSetup(mock(Preferences.class), localBitcoinNode);
        AtomicInteger nextStepCount = new AtomicInteger();

        bisqSetup.maybeAskToContinueWithoutLocalBitcoinNode(nextStepCount::incrementAndGet);

        assertEquals(0, nextStepCount.get());
    }

    // Positive control: without an unusable local node the setup continues at once
    @Test
    void continuesAtOnceWithoutAnUnusableLocalNode() {
        LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);
        when(localBitcoinNode.getUnusableReason()).thenReturn(Optional.empty());
        BisqSetup bisqSetup = createBisqSetup(mock(Preferences.class), localBitcoinNode);
        bisqSetup.setLocalBitcoinNodeUnusableHandler((reason, continueHandler) -> fail("nothing to ask"));
        AtomicInteger nextStepCount = new AtomicInteger();

        bisqSetup.maybeAskToContinueWithoutLocalBitcoinNode(nextStepCount::incrementAndGet);

        assertEquals(1, nextStepCount.get());
    }

    // The question comes after the resources are read and before the P2P network and the wallet start
    @Test
    void asksBeforeTheP2pNetworkAndTheWalletStart() {
        LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);
        when(localBitcoinNode.getUnusableReason()).thenReturn(Optional.of("bloom filters are not enabled"));
        // A real Config, as the start of the wallet reads its data directory
        Config config = new Config();
        P2PService p2PService = mock(P2PService.class);
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        when(p2PService.getP2PDataStorage()).thenReturn(p2PDataStorage);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(p2PDataStorage).readFromResources(anyString(), any());
        P2PNetworkSetup p2PNetworkSetup = mock(P2PNetworkSetup.class);
        when(p2PNetworkSetup.init(any(), any())).thenReturn(new SimpleBooleanProperty(false));
        BisqSetup bisqSetup = createBisqSetup(mock(Preferences.class), localBitcoinNode, config, p2PService,
                p2PNetworkSetup);
        AtomicReference<Runnable> continueWithoutLocalNode = new AtomicReference<>();
        bisqSetup.setLocalBitcoinNodeUnusableHandler((reason, continueHandler) ->
                continueWithoutLocalNode.set(continueHandler));
        BisqSetup.BisqSetupListener listener = mock(BisqSetup.BisqSetupListener.class);
        bisqSetup.addBisqSetupListener(listener);
        UserThread.setTimerClass(ManualTimer.class);
        try {
            bisqSetup.step2();

            verify(p2PDataStorage).readFromResources(anyString(), any());
            verify(listener, never()).onInitP2pNetwork();
            verify(listener, never()).onInitWallet();

            continueWithoutLocalNode.get().run();

            verify(listener).onInitP2pNetwork();
            verify(listener).onInitWallet();
        } finally {
            UserThread.setTimerClass(FrameRateTimer.class);
            ManualTimer.clear();
        }
    }

    private static BisqSetup createBisqSetup(Preferences preferences) {
        return createBisqSetup(preferences, mock(LocalBitcoinNode.class));
    }

    private static BisqSetup createBisqSetup(Preferences preferences, LocalBitcoinNode localBitcoinNode) {
        return createBisqSetup(preferences, localBitcoinNode, mock(Config.class), mock(P2PService.class),
                mock(P2PNetworkSetup.class));
    }

    private static BisqSetup createBisqSetup(Preferences preferences,
                                             LocalBitcoinNode localBitcoinNode,
                                             Config config,
                                             P2PService p2PService,
                                             P2PNetworkSetup p2PNetworkSetup) {
        return new BisqSetup(
                mock(DomainInitialisation.class),
                p2PNetworkSetup,
                mock(WalletAppSetup.class),
                mock(WalletsManager.class),
                mock(WalletsSetup.class),
                mock(BtcWalletService.class),
                p2PService,
                mock(PrivateNotificationManager.class),
                mock(SignedWitnessStorageService.class),
                mock(TradeManager.class),
                mock(OpenOfferManager.class),
                preferences,
                mock(User.class),
                mock(AlertManager.class),
                mock(UnconfirmedBsqChangeOutputListService.class),
                config,
                mock(AccountAgeWitnessService.class),
                mock(CoinFormatter.class),
                localBitcoinNode,
                mock(AppStartupState.class),
                mock(Socks5ProxyProvider.class),
                mock(MediationManager.class),
                mock(RefundManager.class),
                mock(ArbitrationManager.class));
    }
}
