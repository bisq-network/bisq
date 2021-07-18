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

package bisq.desktop.main.support.dispute.client.arbitration;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.support.dispute.client.DisputeClientView;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.dao.DaoFacade;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.ArbitrationSession;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import javax.inject.Inject;
import javax.inject.Named;

@FxmlView
public class ArbitrationClientView extends DisputeClientView {
    @Inject
    public ArbitrationClientView(ArbitrationManager arbitrationManager,
                                 KeyRing keyRing,
                                 P2PService p2PService,
                                 TradeManager tradeManager,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                                 Preferences preferences,
                                 DisputeSummaryWindow disputeSummaryWindow,
                                 PrivateNotificationManager privateNotificationManager,
                                 ContractWindow contractWindow,
                                 TradeDetailsWindow tradeDetailsWindow,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 MediatorManager mediatorManager,
                                 RefundAgentManager refundAgentManager,
                                 DaoFacade daoFacade,
                                 @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(arbitrationManager, keyRing, p2PService, tradeManager, formatter, preferences, disputeSummaryWindow,
                privateNotificationManager, contractWindow, tradeDetailsWindow, accountAgeWitnessService,
                mediatorManager, refundAgentManager, daoFacade, useDevPrivilegeKeys);
    }

    @Override
    protected SupportType getType() {
        return SupportType.ARBITRATION;
    }

    @Override
    protected DisputeSession getConcreteDisputeChatSession(Dispute dispute) {
        return new ArbitrationSession(dispute, disputeManager.isTrader(dispute));
    }
}
