package bisq.desktop.components;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.offer.Offer;
import bisq.core.trade.model.TradeModel;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import javax.annotation.Nullable;

public class PeerInfoIconSmall extends PeerInfoIconTrading {
    public PeerInfoIconSmall(NodeAddress nodeAddress,
                             String role,
                             Offer offer,
                             Preferences preferences,
                             AccountAgeWitnessService accountAgeWitnessService,
                             boolean useDevPrivilegeKeys) {
        // We don't want to show number of trades in that case as it would be unreadable.
        // Also we don't need the privateNotificationManager as no interaction will take place with this icon.
        super(nodeAddress, role,
                0,
                null,
                offer,
                preferences,
                accountAgeWitnessService,
                useDevPrivilegeKeys);
    }

    @Override
    protected double getScaleFactor() {
        return 0.6;
    }

    @Override
    protected void addMouseListener(int numTrades,
                                    PrivateNotificationManager privateNotificationManager,
                                    @Nullable TradeModel tradeModel,
                                    Offer offer,
                                    Preferences preferences,
                                    boolean useDevPrivilegeKeys,
                                    boolean isFiatCurrency,
                                    long peersAccountAge,
                                    long peersSignAge,
                                    String peersAccountAgeInfo,
                                    String peersSignAgeInfo,
                                    String accountSigningState) {
    }

    @Override
    protected void updatePeerInfoIcon() {
        numTradesPane.setVisible(false);
        tagPane.setVisible(false);
    }
}
