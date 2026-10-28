/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.filter.FilterPolicyService;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.network.BridgesCanHelpException;
import bisq.network.p2p.network.ExplainedStartFailureException;
import bisq.network.p2p.network.TorNetworkNode;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.tor.TorSandboxStatus;
import bisq.network.tor.controller.exceptions.TorBootstrapFailedException;

import javafx.beans.property.SimpleIntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P2PNetworkSetupTest {
    private P2PNetworkSetup p2PNetworkSetup;
    private P2PServiceListener listener;
    private TorNetworkNode torNetworkNode;
    private final List<TorSandboxNotice> torSandboxNotices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Res.setup();
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getP2PDataStorage()).thenReturn(mock(P2PDataStorage.class));
        torNetworkNode = mock(TorNetworkNode.class);
        when(p2PService.getNetworkNode()).thenReturn(torNetworkNode);
        when(p2PService.getNumConnectedPeers()).thenReturn(new SimpleIntegerProperty());
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.numPeersProperty()).thenReturn(new SimpleIntegerProperty());
        p2PNetworkSetup = new P2PNetworkSetup(mock(PriceFeedService.class), p2PService, walletsSetup,
                mock(Preferences.class), mock(FilterPolicyService.class));

        p2PNetworkSetup.init(() -> {
        }, torSandboxNotices::add);

        ArgumentCaptor<P2PServiceListener> listenerCaptor = ArgumentCaptor.forClass(P2PServiceListener.class);
        verify(p2PService).start(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    void keepsAFailureWhichOtherBridgesCanFixBeforeTheWarningWhichPointsToTheTorSettings() {
        // The desktop application chooses the popup of the warning with it
        var failureAtTheWarning = new AtomicReference<BridgesCanHelpException>();
        p2PNetworkSetup.getP2pNetworkWarnMsg().addListener((observable, oldValue, newValue) ->
                failureAtTheWarning.set(p2PNetworkSetup.getBridgesCanHelpFailure()));
        var failure = new BridgesCanHelpException("Could not bootstrap Tor in 120 seconds",
                new TorBootstrapFailedException("Could not bootstrap Tor in 120 seconds"));

        listener.onSetupFailed(failure);

        assertSame(failure, failureAtTheWarning.get());
        assertSame(failure, p2PNetworkSetup.getBridgesCanHelpFailure());
        assertEquals(Res.get("mainView.p2pNetworkWarnMsg.connectionToP2PFailedBridgesCanHelp",
                "Could not bootstrap Tor in 120 seconds"), p2PNetworkSetup.getP2pNetworkWarnMsg().get());
    }

    // The sandbox status of a start is final once Tor is ready
    @Test
    void reportsATorInAWeakLevelOfItsSandboxWhenTorIsReady() {
        TorSandboxStatus status = TorSandboxStatus.sandboxed("host-network", false, true);
        when(torNetworkNode.getTorSandboxStatus()).thenReturn(status);

        listener.onTorNodeReady();

        assertEquals(List.of(TorSandboxNotice.from(status).orElseThrow()), torSandboxNotices);
    }

    @Test
    void reportsNoTorWhoseSandboxTheUserTurnedOff() {
        when(torNetworkNode.getTorSandboxStatus()).thenReturn(TorSandboxStatus.DISABLED);

        listener.onTorNodeReady();

        assertEquals(List.of(), torSandboxNotices);
    }

    @Test
    void showsAFailureWhichSaysWhatHelpsWithoutTheAdviceToCheckTheInternetConnection() {
        String message = "Bisq does not run on a Whonix-Gateway, whose Tor serves the Whonix-Workstations behind it. " +
                "Run Bisq on a Whonix-Workstation, which uses the Tor of the Gateway.";

        listener.onSetupFailed(new ExplainedStartFailureException(message));

        assertEquals(Res.get("mainView.p2pNetworkWarnMsg.connectionToP2PFailedWithAdvice", message),
                p2PNetworkSetup.getP2pNetworkWarnMsg().get());
        assertNull(p2PNetworkSetup.getBridgesCanHelpFailure());
    }

    @Test
    void showsAnotherFailureWithTheAdviceToCheckTheInternetConnection() {
        listener.onSetupFailed(new RuntimeException("Connection reset"));

        assertEquals(Res.get("mainView.p2pNetworkWarnMsg.connectionToP2PFailed", "Connection reset"),
                p2PNetworkSetup.getP2pNetworkWarnMsg().get());
    }

    @Test
    void keepsNoOtherFailureAsOneWhichOtherBridgesCanFix() {
        listener.onSetupFailed(new RuntimeException("Tor is not installed."));

        assertNull(p2PNetworkSetup.getBridgesCanHelpFailure());
        assertNotNull(p2PNetworkSetup.getP2pNetworkWarnMsg().get());
    }
}
