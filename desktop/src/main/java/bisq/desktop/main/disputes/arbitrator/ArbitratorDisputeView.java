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

package bisq.desktop.main.disputes.arbitrator;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.disputes.trader.TraderDisputeView;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.arbitration.DisputeManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.trade.TradeManager;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import com.google.inject.name.Named;

import javax.inject.Inject;

@FxmlView
public class ArbitratorDisputeView extends TraderDisputeView {

    @Inject
    public ArbitratorDisputeView(DisputeManager disputeManager,
                                 KeyRing keyRing,
                                 TradeManager tradeManager,
                                 BSFormatter formatter,
                                 DisputeSummaryWindow disputeSummaryWindow,
                                 PrivateNotificationManager privateNotificationManager,
                                 ContractWindow contractWindow,
                                 TradeDetailsWindow tradeDetailsWindow,
                                 P2PService p2PService,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(disputeManager,
                keyRing,
                tradeManager,
                formatter,
                disputeSummaryWindow,
                privateNotificationManager,
                contractWindow,
                tradeDetailsWindow,
                p2PService,
                accountAgeWitnessService,
                useDevPrivilegeKeys);
    }

    @Override
    public void initialize() {
        super.initialize();

        filterBox.setVisible(true);
        filterBox.setManaged(true);
    }

    @Override
    protected void applyFilteredListPredicate(String filterString) {
        // If in arbitrator view we must only display disputes where we are selected as arbitrator (must not receive others anyway)
        filteredList.setPredicate(dispute -> {
            boolean matchesTradeId = dispute.getTradeId().contains(filterString);
            boolean matchesDate = formatter.formatDate(dispute.getOpeningDate()).contains(filterString);
            boolean isBuyerOnion = dispute.getContract().getBuyerNodeAddress().getFullAddress().contains(filterString);
            boolean isSellerOnion = dispute.getContract().getSellerNodeAddress().getFullAddress().contains(filterString);
            boolean matchesBuyersPaymentAccountData = dispute.getContract().getBuyerPaymentAccountPayload().getPaymentDetails().contains(filterString);
            boolean matchesSellersPaymentAccountData = dispute.getContract().getSellerPaymentAccountPayload().getPaymentDetails().contains(filterString);

            boolean anyMatch = matchesTradeId || matchesDate || isBuyerOnion || isSellerOnion ||
                    matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;

            boolean open = !dispute.isClosed() && filterString.toLowerCase().equals("open");
            boolean isMyCase = dispute.getArbitratorPubKeyRing().equals(keyRing.getPubKeyRing());
            return isMyCase && (open || filterString.isEmpty() || anyMatch);
        });
    }

}


