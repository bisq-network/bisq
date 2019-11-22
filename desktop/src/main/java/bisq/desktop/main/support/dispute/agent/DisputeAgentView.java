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

package bisq.desktop.main.support.dispute.agent;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.support.dispute.DisputeView;
import bisq.desktop.util.DisplayUtils;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.trade.TradeManager;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.crypto.KeyRing;

import javafx.scene.control.Button;

public abstract class DisputeAgentView extends DisputeView {

    public DisputeAgentView(DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager,
                            KeyRing keyRing,
                            TradeManager tradeManager,
                            CoinFormatter formatter,
                            DisputeSummaryWindow disputeSummaryWindow,
                            PrivateNotificationManager privateNotificationManager,
                            ContractWindow contractWindow,
                            TradeDetailsWindow tradeDetailsWindow,
                            AccountAgeWitnessService accountAgeWitnessService,
                            boolean useDevPrivilegeKeys) {
        super(disputeManager,
                keyRing,
                tradeManager,
                formatter,
                disputeSummaryWindow,
                privateNotificationManager,
                contractWindow,
                tradeDetailsWindow,
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
            boolean matchesDate = DisplayUtils.formatDate(dispute.getOpeningDate()).contains(filterString);
            boolean isBuyerOnion = dispute.getContract().getBuyerNodeAddress().getFullAddress().contains(filterString);
            boolean isSellerOnion = dispute.getContract().getSellerNodeAddress().getFullAddress().contains(filterString);
            boolean matchesBuyersPaymentAccountData = dispute.getContract().getBuyerPaymentAccountPayload().getPaymentDetails().contains(filterString);
            boolean matchesSellersPaymentAccountData = dispute.getContract().getSellerPaymentAccountPayload().getPaymentDetails().contains(filterString);

            boolean anyMatch = matchesTradeId || matchesDate || isBuyerOnion || isSellerOnion ||
                    matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;

            boolean open = !dispute.isClosed() && filterString.toLowerCase().equals("open");
            boolean isMyCase = dispute.getAgentPubKeyRing().equals(keyRing.getPubKeyRing());
            return isMyCase && (open || filterString.isEmpty() || anyMatch);
        });
    }

    @Override
    protected void handleOnSelectDispute(Dispute dispute) {
        Button closeDisputeButton = null;
        if (!dispute.isClosed() && !disputeManager.isTrader(dispute)) {
            closeDisputeButton = new AutoTooltipButton(Res.get("support.closeTicket"));
            closeDisputeButton.setOnAction(e -> onCloseDispute(getSelectedDispute()));
        }
        DisputeSession chatSession = getConcreteDisputeChatSession(dispute);
        chatView.display(chatSession, closeDisputeButton, root.widthProperty());
    }
}


