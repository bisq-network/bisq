package bisq.desktop.components;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.offer.Offer;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.NodeAddress;

public class PeerInfoIconSmall extends PeerInfoIcon {
    public PeerInfoIconSmall(NodeAddress nodeAddress,
                             String role, Offer offer,
                             Preferences preferences,
                             AccountAgeWitnessService accountAgeWitnessService,
                             BSFormatter formatter,
                             boolean useDevPrivilegeKeys) {
        // We don't want to show number of trades in that case as it would be unreadable.
        // Also we don't need the privateNotificationManager as no interaction will take place with this icon.
        super(nodeAddress, role,
                0,
                null,
                offer,
                preferences,
                accountAgeWitnessService,
                formatter,
                useDevPrivilegeKeys);
    }

    @Override
    protected double getScaleFactor() {
        return 0.6;
    }

    @Override
    protected void addMouseListener(int numTrades,
                                    PrivateNotificationManager privateNotificationManager,
                                    Offer offer, Preferences preferences,
                                    BSFormatter formatter,
                                    boolean useDevPrivilegeKeys,
                                    boolean isFiatCurrency,
                                    long makersAccountAge) {
    }

    @Override
    protected void updatePeerInfoIcon() {
        numTradesPane.setVisible(false);
        tagPane.setVisible(false);
    }
}
