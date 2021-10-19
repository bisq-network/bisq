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

import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.ContractWindow;
import bisq.desktop.main.overlays.windows.DisputeSummaryWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.support.dispute.DisputeView;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.agent.MultipleHolderNameDetection;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Utilities;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ListChangeListener;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import static bisq.core.trade.bisq_v1.TradeDataValidation.ValidationException;
import static bisq.desktop.util.FormBuilder.getIconForLabel;

public abstract class DisputeAgentView extends DisputeView implements MultipleHolderNameDetection.Listener {

    private final MultipleHolderNameDetection multipleHolderNameDetection;
    private ListChangeListener<ValidationException> validationExceptionListener;

    public DisputeAgentView(DisputeManager<? extends DisputeList<Dispute>> disputeManager,
                            KeyRing keyRing,
                            P2PService p2PService,
                            TradeManager tradeManager,
                            CoinFormatter formatter,
                            Preferences preferences,
                            DisputeSummaryWindow disputeSummaryWindow,
                            PrivateNotificationManager privateNotificationManager,
                            ContractWindow contractWindow,
                            TradeDetailsWindow tradeDetailsWindow,
                            AccountAgeWitnessService accountAgeWitnessService,
                            DaoFacade daoFacade,
                            MediatorManager mediatorManager,
                            RefundAgentManager refundAgentManager,
                            boolean useDevPrivilegeKeys) {
        super(disputeManager,
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
                mediatorManager,
                refundAgentManager,
                daoFacade,
                useDevPrivilegeKeys);

        multipleHolderNameDetection = new MultipleHolderNameDetection(disputeManager);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Life cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        multipleHolderNameDetection.detectMultipleHolderNames();

        validationExceptionListener = c -> {
            c.next();
            if (c.wasAdded()) {
                showWarningForValidationExceptions(c.getAddedSubList());
            }
        };
    }

    protected void showWarningForValidationExceptions(List<? extends ValidationException> exceptions) {
        exceptions.stream()
                .filter(ex -> ex.getDispute() != null)
                .filter(ex -> !ex.getDispute().isClosed()) // we show warnings only for open cases
                .filter(ex -> DontShowAgainLookup.showAgain(getKey(ex)))
                .forEach(ex -> new Popup().width(900).warning(getValidationExceptionMessage(ex)).dontShowAgainId(getKey(ex)).show());
    }

    private String getKey(ValidationException exception) {
        Dispute dispute = exception.getDispute();
        if (dispute != null) {
            return "ValExcPopup-" + dispute.getTradeId() + "-" + dispute.getTraderId();
        }
        return "ValExcPopup-" + exception.toString();
    }

    private String getValidationExceptionMessage(ValidationException exception) {
        Dispute dispute = exception.getDispute();
        if (dispute != null && exception instanceof TradeDataValidation.AddressException) {
            return getAddressExceptionMessage(dispute);
        } else if (exception.getMessage() != null && !exception.getMessage().isEmpty()) {
            return exception.getMessage();
        } else {
            return exception.toString();
        }
    }

    @NotNull
    private String getAddressExceptionMessage(Dispute dispute) {
        return Res.get("support.warning.disputesWithInvalidDonationAddress",
                dispute.getDonationAddressOfDelayedPayoutTx(),
                daoFacade.getAllDonationAddresses(),
                dispute.getTradeId(),
                "");
    }

    @Override
    protected void activate() {
        super.activate();

        multipleHolderNameDetection.addListener(this);
        if (multipleHolderNameDetection.hasSuspiciousDisputesDetected()) {
            suspiciousDisputeDetected();
        }

        disputeManager.getValidationExceptions().addListener(validationExceptionListener);
        showWarningForValidationExceptions(disputeManager.getValidationExceptions());
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        multipleHolderNameDetection.removeListener(this);

        disputeManager.getValidationExceptions().removeListener(validationExceptionListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MultipleHolderNamesDetection.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSuspiciousDisputeDetected() {
        suspiciousDisputeDetected();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DisputeView
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DisputeView.FilterResult getFilterResult(Dispute dispute, String filterString) {
        // If in arbitrator view we must only display disputes where we are selected as arbitrator (must not receive others anyway)
        if (!dispute.getAgentPubKeyRing().equals(keyRing.getPubKeyRing())) {
            return FilterResult.NO_MATCH;
        }

        return super.getFilterResult(dispute, filterString);
    }

    @Override
    protected void handleOnProcessDispute(Dispute dispute) {
        onCloseDispute(dispute);
    }

    @Override
    protected void setupTable() {
        super.setupTable();

        stateColumn.getStyleClass().remove("last-column");
        tableView.getColumns().add(getAlertColumn());
    }

    protected abstract void onCloseDispute(Dispute dispute);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void suspiciousDisputeDetected() {
        alertIconLabel.setVisible(true);
        alertIconLabel.setManaged(true);
        alertIconLabel.setTooltip(new Tooltip("You have suspicious disputes where the same trader used different " +
                "account holder names.\nClick for more information."));
        // Text below is for arbitrators only so no need to translate it
        alertIconLabel.setOnMouseClicked(e -> {
            String reportForAllDisputes = multipleHolderNameDetection.getReportForAllDisputes();
            new Popup()
                    .width(1100)
                    .warning(getReportMessage(reportForAllDisputes, "traders"))
                    .actionButtonText(Res.get("shared.copyToClipboard"))
                    .onAction(() -> Utilities.copyToClipboard(reportForAllDisputes))
                    .show();
        });
    }


    private TableColumn<Dispute, Dispute> getAlertColumn() {
        TableColumn<Dispute, Dispute> column = new AutoTooltipTableColumn<>("Alert") {
            {
                setMinWidth(50);
            }
        };
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((dispute) -> new ReadOnlyObjectWrapper<>(dispute.getValue()));
        column.setCellFactory(
                c -> new TableCell<>() {
                    Label alertIconLabel;

                    @Override
                    public void updateItem(Dispute dispute, boolean empty) {
                        if (dispute != null && !empty) {
                            if (!showAlertAtDispute(dispute)) {
                                setGraphic(null);
                                if (alertIconLabel != null) {
                                    alertIconLabel.setOnMouseClicked(null);
                                }
                                return;
                            }

                            if (alertIconLabel != null) {
                                alertIconLabel.setOnMouseClicked(null);
                            }

                            alertIconLabel = new Label();
                            Text icon = getIconForLabel(MaterialDesignIcon.ALERT_CIRCLE_OUTLINE, "1.5em", alertIconLabel);
                            icon.getStyleClass().add("alert-icon");
                            HBox.setMargin(alertIconLabel, new Insets(4, 0, 0, 10));
                            alertIconLabel.setMouseTransparent(false);
                            setGraphic(alertIconLabel);

                            alertIconLabel.setOnMouseClicked(e -> {
                                List<Dispute> realNameAccountInfoList = multipleHolderNameDetection.getDisputesForTrader(dispute);
                                String reportForDisputeOfTrader = multipleHolderNameDetection.getReportForDisputeOfTrader(realNameAccountInfoList);
                                String key = MultipleHolderNameDetection.getAckKey(dispute);
                                new Popup()
                                        .width(1100)
                                        .warning(getReportMessage(reportForDisputeOfTrader, "this trader"))
                                        .actionButtonText(Res.get("shared.copyToClipboard"))
                                        .onAction(() -> {
                                            Utilities.copyToClipboard(reportForDisputeOfTrader);
                                            if (!DontShowAgainLookup.showAgain(key)) {
                                                setGraphic(null);
                                            }
                                        })
                                        .dontShowAgainId(key)
                                        .dontShowAgainText("Is not suspicious")
                                        .onClose(() -> {
                                            if (!DontShowAgainLookup.showAgain(key)) {
                                                setGraphic(null);
                                            }
                                        })
                                        .show();
                            });
                        } else {
                            setGraphic(null);
                            if (alertIconLabel != null) {
                                alertIconLabel.setOnMouseClicked(null);
                            }
                        }
                    }
                });

        column.setComparator((o1, o2) -> Boolean.compare(showAlertAtDispute(o1), showAlertAtDispute(o2)));
        column.setSortable(true);
        return column;
    }

    private boolean showAlertAtDispute(Dispute dispute) {
        return DontShowAgainLookup.showAgain(MultipleHolderNameDetection.getAckKey(dispute)) &&
                !multipleHolderNameDetection.getDisputesForTrader(dispute).isEmpty();
    }

    private String getReportMessage(String report, String subString) {
        return "You have dispute cases where " + subString + " used different account holder names.\n\n" +
                "This might be not critical in case of small variations of the same name " +
                "(e.g. first name and last name are swapped), " +
                "but if the name is completely different you should request information from the trader why they " +
                "used a different name and request proof that the person with the real name is aware " +
                "of the trade. " +
                "It can be that the trader uses the account of their wife/husband, but it also could " +
                "be a case of a stolen bank account or money laundering.\n\n" +
                "Please check below the list of the names which have been detected. " +
                "Search with the trade ID for the dispute case or check out the alert icon at each dispute in " +
                "the list (you might need to remove the 'open' filter) and evaluate " +
                "if it might be a fraudulent account (buyer role is more likely to be fraudulent). " +
                "If you find suspicious disputes, please notify the developers and provide the contract json data " +
                "to them so they can ban those traders.\n\n" +
                Utilities.toTruncatedString(report, 700, false);
    }

    @Override
    protected void maybeAddProcessColumnsForAgent() {
        tableView.getColumns().add(getProcessColumn());
        tableView.getColumns().add(getChatColumn());
    }

    @Override
    protected boolean senderFlag() {
        return true;
    }
}


