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

package bisq.desktop.main.support.dispute.agent.mediation;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.support.dispute.agent.DisputeAgentView;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.dao.DaoFacade;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.MediationSession;
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
public class MediatorView extends DisputeAgentView {

    @Inject
    public MediatorView(MediationManager mediationManager,
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
                        DaoFacade daoFacade,
                        MediatorManager mediatorManager,
                        RefundAgentManager refundAgentManager,
                        @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(mediationManager,
                keyRing,
                p2PService,
                tradeManager,
                formatter,
                preferences,
                disputeSummaryWindow,
                privateNotificationManager,
                contractWindow,
                tradeDetailsWindow,
                accountAgeWitnessService,
                daoFacade,
                mediatorManager,
                refundAgentManager,
                useDevPrivilegeKeys);
    }

    @Override
    public void initialize() {
        super.initialize();
        reOpenButton.setVisible(true);
        reOpenButton.setManaged(true);
        setupReOpenDisputeListener();
    }

    @Override
    protected void activate() {
        super.activate();
        activateReOpenDisputeListener();

        // We need to call applyFilteredListPredicate after we called activateReOpenDisputeListener as we use the
        // "open" string by default and it was applied in the super call but the disputes got set in
        // activateReOpenDisputeListener
        applyFilteredListPredicate(filterTextField.getText());
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        deactivateReOpenDisputeListener();
    }

    @Override
    protected SupportType getType() {
        return SupportType.MEDIATION;
    }

    @Override
    protected DisputeSession getConcreteDisputeChatSession(Dispute dispute) {
        return new MediationSession(dispute, disputeManager.isTrader(dispute));
    }

    @Override
    protected void onCloseDispute(Dispute dispute) {
        chatPopup.closeChat();
        disputeSummaryWindow.show(dispute);
    }
}
