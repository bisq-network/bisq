/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.orders.pending;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.TextFieldWithCopyIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.locale.BSResources;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesViewCB extends CachedViewCB<PendingTradesPM> {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewCB.class);


    private ChangeListener<PendingTradesListItem> selectedItemChangeListener;
    private ListChangeListener<PendingTradesListItem> listChangeListener;
    private ChangeListener<String> txIdChangeListener;
    private ChangeListener<PendingTradesPM.State> offererStateChangeListener;
    private ChangeListener<PendingTradesPM.State> takerStateChangeListener;
    private ChangeListener<Throwable> faultChangeListener;
    private Navigation navigation;

    @FXML ScrollPane scrollPane;
    @FXML GridPane gridPane;
    @FXML TitledGroupBg titledGroupBg, paymentsGroupBg, summaryGroupBg, withdrawGroupBg;
    @FXML ProcessStepBar processBar;
    @FXML Label statusLabel, txIdLabel, paymentMethodLabel, fiatAmountLabel, holderNameLabel, primaryIdLabel,
            secondaryIdLabel, btcTradeAmountLabel, fiatTradeAmountLabel, feesLabel, collateralLabel,
            withdrawAmountLabel, withdrawAddressLabel;
    @FXML TextField statusTextField, paymentMethodTextField, btcTradeAmountTextField, fiatTradeAmountTextField,
            feesTextField, collateralTextField, withdrawAmountTextField;
    @FXML InputTextField withdrawAddressTextField;
    @FXML TxIdTextField txIdTextField;
    @FXML InfoDisplay infoDisplay, paymentsInfoDisplay, summaryInfoDisplay;
    @FXML Button confirmPaymentReceiptButton, paymentsButton, withdrawButton;
    @FXML TextFieldWithCopyIcon fiatAmountTextField, holderNameTextField, secondaryIdTextField, primaryIdTextField;
    @FXML TableView<PendingTradesListItem> table;
    @FXML TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, tradeIdColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PendingTradesViewCB(PendingTradesPM presentationModel, Navigation navigation) {
        super(presentationModel);

        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();

        scrollPane.vvalueProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("#### vvalueProperty " + newValue);
        });
        scrollPane.viewportBoundsProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("#### viewportBoundsProperty " + newValue);
        });

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No pending trades available"));

        txIdChangeListener = (ov, oldValue, newValue) ->
                txIdTextField.setup(presentationModel.getWalletFacade(), newValue);

        selectedItemChangeListener = (obsValue, oldValue, newValue) -> {
            if (oldValue != null && newValue != null)
                presentationModel.selectTrade(newValue);
            else if (newValue == null)
                table.getSelectionModel().clearSelection();
        };

        listChangeListener = change -> {
            change.next();
            if ((change.wasAdded() && change.getList().size() == 1) ||
                    (change.wasRemoved() && change.getList().size() == 0))
                updateScreen();
        };

        offererStateChangeListener = (ov, oldValue, newValue) -> applyOffererState(newValue);
        takerStateChangeListener = (ov, oldValue, newValue) -> applyTakerState(newValue);
        faultChangeListener = (ov, oldValue, newValue) -> onFault(newValue);

        withdrawAddressTextField.setValidator(presentationModel.getBtcAddressValidator());
        withdrawButton.disableProperty().bind(presentationModel.withdrawalButtonDisable);
        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        table.setItems(presentationModel.getList());

        presentationModel.getList().addListener(listChangeListener);
        presentationModel.txId.addListener(txIdChangeListener);
        presentationModel.fault.addListener(faultChangeListener);

        txIdTextField.setup(presentationModel.getWalletFacade(), presentationModel.txId.get());
        table.getSelectionModel().select(presentationModel.getSelectedItem());
        table.getSelectionModel().selectedItemProperty().addListener(selectedItemChangeListener);

        // TODO Set focus to row does not work yet...
       /* table.requestFocus();
        table.getFocusModel().focus( table.getSelectionModel().getSelectedIndex());*/

        withdrawAddressTextField.focusedProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue && !newValue)
                presentationModel.withdrawAddressFocusOut(withdrawAddressTextField.getText());
        });
        updateScreen();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        table.getSelectionModel().selectedItemProperty().removeListener(selectedItemChangeListener);
        presentationModel.getList().removeListener(listChangeListener);
        presentationModel.txId.removeListener(txIdChangeListener);
        presentationModel.fault.removeListener(faultChangeListener);

        presentationModel.state.removeListener(offererStateChangeListener);
        presentationModel.state.removeListener(takerStateChangeListener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onPaymentStarted() {
        presentationModel.fiatPaymentStarted();
    }

    @FXML
    void onConfirmPaymentReceipt() {
        presentationModel.fiatPaymentReceived();
    }

    @FXML
    public void onWithdraw() {
        setSummaryControlsVisible(false);
        presentationModel.removePendingTrade();
        presentationModel.withdraw(withdrawAddressTextField.getText());
        Platform.runLater(() ->
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.CLOSED_TRADES));
    }

    @FXML
    void onOpenHelp() {
        Help.openWindow(presentationModel.isOfferer() ? HelpId.PENDING_TRADE_OFFERER : HelpId.PENDING_TRADE_TAKER);
    }

    @FXML
    void onOpenPaymentsHelp() {
        Help.openWindow(HelpId.PENDING_TRADE_PAYMENT);
    }

    @FXML
    void onOpenSummaryHelp() {
        Help.openWindow(HelpId.PENDING_TRADE_SUMMARY);
    }

    private void openOfferDetails(PendingTradesListItem item) {
        // TODO Open popup with details view
        log.debug("Trade details " + item);
        Popups.openWarningPopup("Under construction", "This will open a " +
                "details popup but that is not implemented yet.");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateScreen() {
        boolean dataAvailable = !presentationModel.getList().isEmpty();
        titledGroupBg.setVisible(dataAvailable);
        processBar.setVisible(dataAvailable);
        statusLabel.setVisible(dataAvailable);
        statusTextField.setVisible(dataAvailable);
        txIdLabel.setVisible(dataAvailable);
        txIdTextField.setVisible(dataAvailable);
        infoDisplay.setVisible(dataAvailable);

        titledGroupBg.setManaged(dataAvailable);
        processBar.setManaged(dataAvailable);
        statusLabel.setManaged(dataAvailable);
        statusTextField.setManaged(dataAvailable);
        txIdLabel.setManaged(dataAvailable);
        txIdTextField.setManaged(dataAvailable);
        infoDisplay.setManaged(dataAvailable);

        if (dataAvailable) {
            if (presentationModel.isOfferer())
                setupScreenForOfferer();
            else
                setupScreenForTaker();
        }
    }

    private void setupScreenForOfferer() {
        if (processBar.getProcessStepItems() == null) {
            List<ProcessStepItem> items = new ArrayList<>();
            items.add(new ProcessStepItem("Wait for block chain confirmation"));
            items.add(new ProcessStepItem("Start payment"));
            items.add(new ProcessStepItem("Wait for payment confirmation"));
            items.add(new ProcessStepItem("Trade successful completed"));
            processBar.setProcessStepItems(items);
        }

        presentationModel.state.addListener(offererStateChangeListener);
        applyOffererState(presentationModel.state.get());
    }

    private void setupScreenForTaker() {
        log.debug("setupScreenForTaker");
        if (processBar.getProcessStepItems() == null) {
            List<ProcessStepItem> items = new ArrayList<>();
            items.add(new ProcessStepItem("Wait for block chain confirmation"));
            items.add(new ProcessStepItem("Wait for payment started"));
            items.add(new ProcessStepItem("Confirm  payment"));
            items.add(new ProcessStepItem("Trade successful completed"));
            processBar.setProcessStepItems(items);
        }

        presentationModel.state.addListener(takerStateChangeListener);
        applyTakerState(presentationModel.state.get());
    }

    private void applyOffererState(PendingTradesPM.State state) {
        setPaymentsControlsVisible(false);
        setSummaryControlsVisible(false);

        processBar.reset();

        if (state != null) {
            switch (state) {
                case OFFERER_BUYER_WAIT_TX_CONF:
                    processBar.setSelectedIndex(0);

                    statusTextField.setText("Deposit transaction has been published. You need to wait for at least " +
                            "one block chain confirmation.");
                    infoDisplay.setText("You need to wait for at least one block chain confirmation to" +
                            " be sure that the deposit funding has not been double spent. For higher trade volumes we" +
                            " recommend to wait up to 6 confirmations.");
                    break;
                case OFFERER_BUYER_START_PAYMENT:
                    processBar.setSelectedIndex(1);

                    setPaymentsControlsVisible(true);

                    statusTextField.setText("Deposit transaction has at least one block chain confirmation. " +
                            "Please start now the payment.");
                    infoDisplay.setText("You are now safe to start the payment. You can wait for up to 6 block chain " +
                            "confirmations if you want more security.");

                    paymentMethodTextField.setText(presentationModel.getPaymentMethod());
                    fiatAmountTextField.setText(presentationModel.getFiatAmount());
                    holderNameTextField.setText(presentationModel.getHolderName());
                    primaryIdTextField.setText(presentationModel.getPrimaryId());
                    secondaryIdTextField.setText(presentationModel.getSecondaryId());
                    paymentsInfoDisplay.setText(BSResources.get("Copy and paste the payment account data to your " +
                                    "internet banking web page and transfer the {0} amount to the other traders " +
                                    "payment account. When the transfer is completed inform the other trader by " +
                                    "clicking the button below.",
                            presentationModel.getCurrencyCode()));
                    break;
                case OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED:
                    processBar.setSelectedIndex(2);

                    statusTextField.setText(BSResources.get("Waiting for the Bitcoin sellers confirmation " +
                                    "that the {0} payment has arrived.",
                            presentationModel.getCurrencyCode()));
                    infoDisplay.setText(BSResources.get("When the confirmation that the {0} payment arrived at the " +
                                    "Bitcoin sellers payment account, the payout transaction will be published.",
                            presentationModel.getCurrencyCode()));
                    break;
                case OFFERER_BUYER_COMPLETED:
                    processBar.setSelectedIndex(3);
                    setSummaryControlsVisible(true);

                    statusTextField.setText("Congratulations! Trade has successfully completed.");
                    infoDisplay.setText("The trade is now completed and you can withdraw your Bitcoin to any external" +
                            "wallet. To protect your privacy you should take care that your trades are not merged " +
                            "in " +
                            "that external wallet. For more information about privacy see our help pages.");

                    btcTradeAmountLabel.setText("You have bought:");
                    fiatTradeAmountLabel.setText("You have paid:");
                    btcTradeAmountTextField.setText(presentationModel.getTradeVolume());
                    fiatTradeAmountTextField.setText(presentationModel.getFiatVolume());
                    feesTextField.setText(presentationModel.getTotalFees());
                    collateralTextField.setText(presentationModel.getCollateral());
                    summaryInfoDisplay.setText("Your security deposit has been refunded to you. " +
                            "You can review the details to that trade any time in the closed trades section.");

                    withdrawAmountTextField.setText(presentationModel.getAmountToWithdraw());
                    break;
            }
        }
    }


    private void applyTakerState(PendingTradesPM.State state) {
        confirmPaymentReceiptButton.setVisible(false);
        confirmPaymentReceiptButton.setManaged(false);

        setSummaryControlsVisible(false);

        processBar.reset();

        if (state != null) {
            switch (state) {
                case TAKER_SELLER_WAIT_TX_CONF:
                    processBar.setSelectedIndex(0);

                    statusTextField.setText("Deposit transaction has been published. " +
                            "The Bitcoin buyer need to wait for at least one block chain confirmation.");
                    infoDisplay.setText(BSResources.get("The Bitcoin buyer needs to wait for at least one " +
                                    "block chain confirmation before starting the {0} payment. " +
                                    "That is needed to assure that the deposit input funding has not been " +
                                    "double-spent. " +
                                    "For higher trade volumes it is recommended to wait up to 6 confirmations.",
                            presentationModel.getCurrencyCode()));
                    break;
                case TAKER_SELLER_WAIT_PAYMENT_STARTED:
                    processBar.setSelectedIndex(1);

                    statusTextField.setText(BSResources.get("Deposit transaction has at least one block chain " +
                                    "confirmation. " +
                                    "Waiting that other trader starts the {0} payment.",
                            presentationModel.getCurrencyCode()));
                    infoDisplay.setText(BSResources.get("You will get informed when the other trader has indicated " +
                                    "the {0} payment has been started.",
                            presentationModel.getCurrencyCode()));
                    break;
                case TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT:
                    processBar.setSelectedIndex(2);

                    confirmPaymentReceiptButton.setVisible(true);
                    confirmPaymentReceiptButton.setManaged(true);

                    statusTextField.setText(BSResources.get("The Bitcoin buyer has started the {0} payment." +
                                    "Check your payments account and confirm when you have received the payment.",
                            presentationModel.getCurrencyCode()));
                    infoDisplay.setText(BSResources.get("It is important that you confirm when you have received the " +
                                    "{0} payment as this will publish the payout transaction where you get returned " +
                                    "your security deposit and the Bitcoin buyer receive the Bitcoin amount you sold.",
                            presentationModel.getCurrencyCode()));

                    break;
                case TAKER_SELLER_COMPLETED:
                    processBar.setSelectedIndex(3);

                    setSummaryControlsVisible(true);

                    statusTextField.setText("Congratulations! Trade has successfully completed.");
                    infoDisplay.setText("The trade is now completed and you can withdraw the refunded Bitcoin from " +
                            "the security deposit to any external wallet. " +
                            "To protect your privacy you should take care that your coins are not merged in " +
                            "that external wallet. For more information about privacy see our help pages.");

                    btcTradeAmountLabel.setText("You have sold:");
                    fiatTradeAmountLabel.setText("You have received:");
                    btcTradeAmountTextField.setText(presentationModel.getTradeVolume());
                    fiatTradeAmountTextField.setText(presentationModel.getFiatVolume());
                    feesTextField.setText(presentationModel.getTotalFees());
                    collateralTextField.setText(presentationModel.getCollateral());
                    summaryInfoDisplay.setText("Your security deposit has been refunded to you. " +
                            "You can review the details to that trade any time in the closed trades section.");

                    withdrawAmountTextField.setText(presentationModel.getAmountToWithdraw());
                    break;
            }
        }
    }

    private void onFault(Throwable fault) {
        // TODO error handling not implemented yet
        if (fault != null)
            log.error(fault.toString());
    }

    private void setPaymentsControlsVisible(boolean visible) {
        paymentsGroupBg.setVisible(visible);
        paymentMethodLabel.setVisible(visible);
        fiatAmountLabel.setVisible(visible);
        holderNameLabel.setVisible(visible);
        primaryIdLabel.setVisible(visible);
        secondaryIdLabel.setVisible(visible);
        paymentMethodTextField.setVisible(visible);
        paymentsInfoDisplay.setVisible(visible);
        paymentsButton.setVisible(visible);
        fiatAmountTextField.setVisible(visible);
        holderNameTextField.setVisible(visible);
        primaryIdTextField.setVisible(visible);
        secondaryIdTextField.setVisible(visible);

        paymentsGroupBg.setManaged(visible);
        paymentMethodLabel.setManaged(visible);
        fiatAmountLabel.setManaged(visible);
        holderNameLabel.setManaged(visible);
        primaryIdLabel.setManaged(visible);
        secondaryIdLabel.setManaged(visible);
        paymentMethodTextField.setManaged(visible);
        paymentsInfoDisplay.setManaged(visible);
        paymentsButton.setManaged(visible);
        fiatAmountTextField.setManaged(visible);
        holderNameTextField.setManaged(visible);
        primaryIdTextField.setManaged(visible);
        secondaryIdTextField.setManaged(visible);

        if (visible)
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        else
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.layout();
    }

    private void setSummaryControlsVisible(boolean visible) {
        summaryGroupBg.setVisible(visible);
        btcTradeAmountLabel.setVisible(visible);
        btcTradeAmountTextField.setVisible(visible);
        fiatTradeAmountLabel.setVisible(visible);
        fiatTradeAmountTextField.setVisible(visible);
        feesLabel.setVisible(visible);
        feesTextField.setVisible(visible);
        collateralLabel.setVisible(visible);
        collateralTextField.setVisible(visible);
        summaryInfoDisplay.setVisible(visible);

        withdrawGroupBg.setVisible(visible);
        withdrawAmountLabel.setVisible(visible);
        withdrawAmountTextField.setVisible(visible);
        withdrawAddressLabel.setVisible(visible);
        withdrawAddressTextField.setVisible(visible);
        withdrawButton.setVisible(visible);

        summaryGroupBg.setManaged(visible);
        btcTradeAmountLabel.setManaged(visible);
        btcTradeAmountTextField.setManaged(visible);
        fiatTradeAmountLabel.setManaged(visible);
        fiatTradeAmountTextField.setManaged(visible);
        feesLabel.setManaged(visible);
        feesTextField.setManaged(visible);
        collateralLabel.setManaged(visible);
        collateralTextField.setManaged(visible);
        summaryInfoDisplay.setManaged(visible);

        withdrawGroupBg.setManaged(visible);
        withdrawAmountLabel.setManaged(visible);
        withdrawAmountTextField.setManaged(visible);
        withdrawAddressLabel.setManaged(visible);
        withdrawAddressTextField.setManaged(visible);
        withdrawButton.setManaged(visible);

        if (visible) {
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            withdrawAddressTextField.requestFocus();
            scrollPane.setVvalue(scrollPane.getVmax());
        }
        else {
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        }
        scrollPane.layout();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>,
                        TableCell<PendingTradesListItem, PendingTradesListItem>>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call
                            (TableColumn<PendingTradesListItem,
                                    PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(presentationModel.getTradeId(item));
                                    hyperlink.setId("id-link");
                                    Tooltip.install(hyperlink, new Tooltip(presentationModel.getTradeId(item)));
                                    hyperlink.setOnAction(event -> openOfferDetails(item));
                                    setGraphic(hyperlink);
                                }
                                else {
                                    setGraphic(null);
                                    setId(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(presentationModel.getDate(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(presentationModel.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }

}

