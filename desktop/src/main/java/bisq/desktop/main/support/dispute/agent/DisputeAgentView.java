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
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.support.dispute.DisputeView;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.support.dispute.agent.FraudDetection;
import bisq.core.trade.TradeManager;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Utilities;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

public abstract class DisputeAgentView extends DisputeView implements FraudDetection.Listener {

    private final FraudDetection fraudDetection;

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

        fraudDetection = new FraudDetection(disputeManager);
    }

    @Override
    public void initialize() {
        super.initialize();

        filterTextField.setText("open");

        sendPrivateNotificationButton.setVisible(true);
        sendPrivateNotificationButton.setManaged(true);

        reportButton.setVisible(true);
        reportButton.setManaged(true);

        fullReportButton.setVisible(true);
        fullReportButton.setManaged(true);

        fraudDetection.checkForMultipleHolderNames();
    }

    @Override
    protected void activate() {
        super.activate();

        fraudDetection.addListener(this);
        if (fraudDetection.hasSuspiciousDisputeDetected()) {
            showAlertIcon();
        }
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        fraudDetection.removeListener(this);
    }

    @Override
    protected void applyFilteredListPredicate(String filterString) {
        filteredList.setPredicate(dispute -> {
            // If in arbitrator view we must only display disputes where we are selected as arbitrator (must not receive others anyway)
            if (!dispute.getAgentPubKeyRing().equals(keyRing.getPubKeyRing())) {
                return false;
            }
            boolean isOpen = !dispute.isClosed() && filterString.toLowerCase().equals("open");
            return filterString.isEmpty() ||
                    isOpen ||
                    anyMatchOfFilterString(dispute, filterString);
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

    @Override
    public void onSuspiciousDisputeDetected() {
        showAlertIcon();
    }

    private void showAlertIcon() {
        String accountsUsingMultipleNamesList = fraudDetection.getAccountsUsingMultipleNamesAsString();
        alertIconLabel.setVisible(true);
        alertIconLabel.setManaged(true);
        alertIconLabel.setTooltip(new Tooltip("You have disputes where user used different " +
                "real life names from the same application. Click for more details."));
        // Text below is for arbitrators only so no need to translate it
        alertIconLabel.setOnMouseClicked(e -> new Popup()
                .width(1100)
                .warning("You have dispute cases where traders used different account holder names.\n\n" +
                        "This might be not critical in case of small variations of the same name " +
                        "(e.g. first name and last name are swapped), " +
                        "but if the name is different you should request information from the trader why they " +
                        "used a different name and request proof that the person with the real name is aware " +
                        "of the trade. " +
                        "It can be that the trader uses the account of their wife/husband, but it also could " +
                        "be a case of a stolen bank account or money laundering.\n\n" +
                        "Please check below the list of the names which got detected. Search with the trade ID for " +
                        "the dispute case for evaluating if it might be a fraudulent account. If so, please notify the " +
                        "developers and provide the contract json data to them so they can ban those traders.\n\n" +
                        accountsUsingMultipleNamesList)
                .actionButtonText(Res.get("shared.copyToClipboard"))
                .onAction(() -> Utilities.copyToClipboard(accountsUsingMultipleNamesList))
                .show());
    }
}


