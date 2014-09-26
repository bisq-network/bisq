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
import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.TextFieldWithCopyIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesViewCB extends CachedViewCB<PendingTradesPM> {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewCB.class);

    @FXML TitledGroupBg titledGroupBg, paymentsGroupBg, summaryGroupBg;
    @FXML ProcessStepBar processBar;
    @FXML Label statusLabel, txIdLabel, paymentMethodLabel, holderNameLabel, primaryIdLabel, secondaryIdLabel,
            btcLabel, fiatLabel, feesLabel, collateralLabel;
    @FXML TextField statusTextField, paymentMethodTextField, btcTextField, fiatTextField, feesTextField,
            collateralTextField;
    @FXML TxIdTextField txIdTextField;
    @FXML InfoDisplay infoDisplay, paymentsInfoDisplay, summaryInfoDisplay;
    @FXML Button confirmPaymentReceiptButton, paymentsButton, closeSummaryButton;
    @FXML TextFieldWithCopyIcon holderNameTextField, secondaryIdTextField, primaryIdTextField;
    @FXML TableView<PendingTradesListItem> table;
    @FXML TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, tradeIdColumn, selectColumn;
    private ChangeListener<PendingTradesListItem> selectedItemChangeListener;
    private ListChangeListener<PendingTradesListItem> listChangeListener;
    private ChangeListener<String> txIdChangeListener;
    private ChangeListener<PendingTradesPM.State> offererStateChangeListener;
    private ChangeListener<PendingTradesPM.State> takerStateChangeListener;
    private ChangeListener<Throwable> faultChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PendingTradesViewCB(PendingTradesPM presentationModel) {
        super(presentationModel);
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
        setSelectColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        txIdChangeListener = (ov, oldValue, newValue) ->
                txIdTextField.setup(presentationModel.getWalletFacade(), newValue);

        selectedItemChangeListener = (obsValue, oldValue, newValue) -> {
            if (oldValue != newValue) {
                if (oldValue != null && newValue != null)
                    presentationModel.selectTrade(newValue);
                else if (newValue == null)
                    table.getSelectionModel().clearSelection();
            }
            else {
                log.warn("should never happen!");
            }
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
    void onCloseSummary() {
        presentationModel.closeSummary();
        setSummaryControlsVisible(false);
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
                    statusTextField.setText("Deposit transaction is published. Waiting " +
                            "for at least 1 confirmation");
                    infoDisplay.setText("Deposit transaction has bee published. You need to wait for at least one " +
                            "block chain confirmation. After that you need to make the payments transfer.");
                    break;
                case OFFERER_BUYER_START_PAYMENT:
                    processBar.setSelectedIndex(1);

                    setPaymentsControlsVisible(true);

                    statusTextField.setText("Deposit transaction has at least 1 confirmation. Start payment.");
                    infoDisplay.setText("Deposit transaction has at least one blockchain confirmation. You need to " +
                            "start the payment.");

                    paymentMethodTextField.setText(presentationModel.getPaymentMethod());
                    holderNameTextField.setText(presentationModel.getHolderName());
                    primaryIdTextField.setText(presentationModel.getPrimaryId());
                    secondaryIdTextField.setText(presentationModel.getSecondaryId());
                    paymentsInfoDisplay.setText("Copy and paste the payments accounts data to your payments " +
                            "accounts web page and transfer the payment to the other trader. When the transfer is " +
                            "done confirm it with the 'Payment started' button.");
                    break;
                case OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED:
                    processBar.setSelectedIndex(2);

                    statusTextField.setText("Waiting until the other trader has received your payment.");
                    infoDisplay.setText("Waiting until the other trader has confirmed that he has received your " +
                            "payment.");
                    break;
                case OFFERER_BUYER_COMPLETED:
                    processBar.setSelectedIndex(3);

                    setSummaryControlsVisible(true);

                    statusTextField.setText("Trade has successfully completed.");
                    infoDisplay.setText("Trade has successfully completed. You can find the details to that trade" +
                            " in the closed trades section.");

                    btcLabel.setText("You have bought:");
                    fiatLabel.setText("You have paid:");
                    btcTextField.setText(presentationModel.getTradeVolume());
                    fiatTextField.setText(presentationModel.getFiatVolume());
                    feesTextField.setText(presentationModel.getTotalFees());
                    collateralTextField.setText(presentationModel.getCollateral());
                    summaryInfoDisplay.setText("You can open that summary any time in the closed orders section.");
                    break;
            }
        }
    }


    private void applyTakerState(PendingTradesPM.State state) {
        confirmPaymentReceiptButton.setVisible(false);

        setSummaryControlsVisible(false);

        processBar.reset();

        if (state != null) {
            switch (state) {
                case TAKER_SELLER_WAIT_TX_CONF:
                    processBar.setSelectedIndex(0);

                    statusTextField.setText("Deposit transaction is published. Waiting for at least 1 confirmation");
                    infoDisplay.setText("Deposit transaction has bee published. He needs to wait for at least one " +
                            "blockchain confirmation.");
                    break;
                case TAKER_SELLER_WAIT_PAYMENT_STARTED:
                    processBar.setSelectedIndex(1);

                    statusTextField.setText("Deposit transaction has at least 1 confirmation. Waiting that other " +
                            "trader starts payment.");
                    infoDisplay.setText("Deposit transaction has at least one blockchain " +
                            "confirmation. The other trader need to start the payment. You will get informed when " +
                            "that been done.");
                    break;
                case TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT:
                    processBar.setSelectedIndex(2);

                    confirmPaymentReceiptButton.setVisible(true);

                    statusTextField.setText("Payment is on the way. Check your payments account and confirm when you " +
                            "have received the payment.");
                    infoDisplay.setText("The other trader has started the payment. You need to check your payments " +
                            "account and confirm the payment when the money has arrived there.");

                    break;
                case TAKER_SELLER_COMPLETED:
                    processBar.setSelectedIndex(3);

                    setSummaryControlsVisible(true);

                    statusTextField.setText("Trade has successfully completed.");
                    infoDisplay.setText("Trade has successfully completed. You can find the details to that trade" +
                            " in the closed trades section.");

                    btcLabel.setText("You have sold:");
                    fiatLabel.setText("You have received:");
                    btcTextField.setText(presentationModel.getTradeVolume());
                    fiatTextField.setText(presentationModel.getFiatVolume());
                    feesTextField.setText(presentationModel.getTotalFees());
                    collateralTextField.setText(presentationModel.getCollateral());
                    summaryInfoDisplay.setText("You can open that summary any time in the closed orders section.");
                    break;
            }
        }
    }

    private void onFault(Throwable fault) {
        // TODO error handling not implemented yet
        if (fault != null)
            log.error(fault.toString());
    }

    private void openOfferDetails(PendingTradesListItem item) {
        // TODO Open popup with details view
        log.debug("Trade details " + item);
    }

    private void setPaymentsControlsVisible(boolean visible) {
        paymentsGroupBg.setVisible(visible);
        paymentMethodLabel.setVisible(visible);
        holderNameLabel.setVisible(visible);
        primaryIdLabel.setVisible(visible);
        secondaryIdLabel.setVisible(visible);
        paymentMethodTextField.setVisible(visible);
        paymentsInfoDisplay.setVisible(visible);
        paymentsButton.setVisible(visible);
        holderNameTextField.setVisible(visible);
        primaryIdTextField.setVisible(visible);
        secondaryIdTextField.setVisible(visible);
    }

    private void setSummaryControlsVisible(boolean visible) {
        summaryGroupBg.setVisible(visible);
        btcLabel.setVisible(visible);
        btcTextField.setVisible(visible);
        fiatLabel.setVisible(visible);
        fiatTextField.setVisible(visible);
        feesLabel.setVisible(visible);
        feesTextField.setVisible(visible);
        collateralLabel.setVisible(visible);
        collateralTextField.setVisible(visible);
        summaryInfoDisplay.setVisible(visible);
        closeSummaryButton.setVisible(visible);
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
                            Hyperlink hyperlink;

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(presentationModel.getTradeId(item));
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

    private void setSelectColumnCellFactory() {
        selectColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        selectColumn.setCellFactory(new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>,
                TableCell<PendingTradesListItem, PendingTradesListItem>>() {
            @Override
            public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                    TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                    final Button button = new Button("Select");

                    @Override
                    public void updateItem(final PendingTradesListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null) {
                            button.setOnAction(event -> showTradeDetails(item));
                            setGraphic(button);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }

            private void showTradeDetails(PendingTradesListItem item) {
                table.getSelectionModel().select(item);
            }
        });
    }


}

