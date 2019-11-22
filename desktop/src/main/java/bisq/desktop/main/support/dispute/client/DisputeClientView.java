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

package bisq.desktop.main.support.dispute.client;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.support.dispute.DisputeView;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.trade.TradeManager;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.crypto.KeyRing;

public abstract class DisputeClientView extends DisputeView {
    public DisputeClientView(DisputeManager<? extends DisputeList<? extends DisputeList>> DisputeManager,
                             KeyRing keyRing,
                             TradeManager tradeManager,
                             CoinFormatter formatter,
                             DisputeSummaryWindow disputeSummaryWindow,
                             PrivateNotificationManager privateNotificationManager,
                             ContractWindow contractWindow,
                             TradeDetailsWindow tradeDetailsWindow,
                             AccountAgeWitnessService accountAgeWitnessService,
                             boolean useDevPrivilegeKeys) {
        super(DisputeManager, keyRing, tradeManager, formatter, disputeSummaryWindow, privateNotificationManager,
                contractWindow, tradeDetailsWindow, accountAgeWitnessService, useDevPrivilegeKeys);
    }

    @Override
    protected void handleOnSelectDispute(Dispute dispute) {
        DisputeSession chatSession = getConcreteDisputeChatSession(dispute);
        chatView.display(chatSession, root.widthProperty());
    }
}
