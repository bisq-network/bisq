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
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.Country;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesViewCB extends CachedViewCB<PendingTradesPM> {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewCB.class);
    public TitledGroupBg summaryGroupBg;
    public Label btcLabel;
    public TextField btcTextField;
    public Label fiatLabel;
    public TextField fiatTextField;
    public Label feesLabel;
    public TextField feesTextField;

    public Label collateralLabel;
    public TextField collateralTextField;
    public InfoDisplay summaryInfoDisplay;

    @FXML TitledGroupBg titledGroupBg, paymentsGroupBg;
    @FXML ProcessStepBar processBar;
    @FXML Label statusLabel, txIdLabel, paymentMethodLabel, holderNameLabel, primaryIdLabel, secondaryIdLabel;
    @FXML TextField statusTextField, paymentMethodTextField;
    @FXML TxIdTextField txIdTextField;
    @FXML InfoDisplay infoDisplay, paymentsInfoDisplay;
    @FXML Button confirmPaymentReceiptButton, paymentsButton;
    @FXML TextFieldWithCopyIcon holderNameTextField, secondaryIdTextField, primaryIdTextField;
    @FXML TableView<PendingTradesListItem> table;
    @FXML TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, countryColumn, bankAccountTypeColumn, selectColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewCB(PendingTradesPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();
        setSelectColumnCellFactory();

        table.setItems(presentationModel.getPendingTrades());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getSelectionModel().selectedItemProperty().
                addListener((obsValue, oldValue, newValue) -> {
                    if (oldValue != newValue) {
                        if (oldValue != null && newValue != null)
                            presentationModel.selectPendingTrade(newValue);
                        else if (newValue == null)
                            table.getSelectionModel().clearSelection();
                    }
                    else {
                        log.error("####### should not happen!");
                    }
                });

        // need runLater to avoid conflict with user initiated selection
        presentationModel.selectedIndex.addListener((ov, oldValue, newValue) ->
                Platform.runLater(() -> table.getSelectionModel().select((int) newValue)));

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        if (!presentationModel.getPendingTrades().isEmpty()) {
            if (presentationModel.isOfferer())
                setupScreenForOfferer();
            else
                setupScreenForTaker();
        }
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
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
        presentationModel.paymentStarted();
    }

    @FXML
    void onConfirmPaymentReceipt() {
        presentationModel.paymentReceived();
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


    private void setupScreenForOfferer() {
        log.debug("setupScreenForOfferer");

        titledGroupBg.setVisible(true);
        processBar.setVisible(true);
        statusLabel.setVisible(true);
        statusTextField.setVisible(true);
        txIdLabel.setVisible(true);
        txIdTextField.setVisible(true);
        infoDisplay.setVisible(true);

        log.debug("setupScreenForTaker");
        if (processBar.getProcessStepItems() == null) {
            List<ProcessStepItem> items = new ArrayList<>();
            items.add(new ProcessStepItem("Wait for block chain confirmation"));
            items.add(new ProcessStepItem("Start payment"));
            items.add(new ProcessStepItem("Wait for payment confirmation"));
            items.add(new ProcessStepItem("Trade successful completed"));
            processBar.setProcessStepItems(items);
        }

        txIdTextField.setup(presentationModel.getWalletFacade(), presentationModel.getTxID());

        presentationModel.state.addListener((ov, oldValue, newValue) -> applyOffererState(newValue));
        applyOffererState(presentationModel.state.get());
    }

    private void applyOffererState(PendingTradesPM.State state) {
        if (state != null) {
            paymentsGroupBg.setVisible(false);
            paymentMethodLabel.setVisible(false);
            holderNameLabel.setVisible(false);
            primaryIdLabel.setVisible(false);
            secondaryIdLabel.setVisible(false);
            paymentMethodTextField.setVisible(false);
            paymentsInfoDisplay.setVisible(false);
            paymentsButton.setVisible(false);
            holderNameTextField.setVisible(false);
            primaryIdTextField.setVisible(false);
            secondaryIdTextField.setVisible(false);

            summaryGroupBg.setVisible(false);
            btcLabel.setVisible(false);
            btcTextField.setVisible(false);
            fiatLabel.setVisible(false);
            fiatTextField.setVisible(false);
            feesLabel.setVisible(false);
            feesTextField.setVisible(false);
            collateralLabel.setVisible(false);
            collateralTextField.setVisible(false);
            summaryInfoDisplay.setVisible(false);


            switch (state) {
                case OFFERER_BUYER_WAIT_TX_CONF:
                    processBar.setSelectedIndex(0);
                    statusTextField.setText("Deposit transaction is published. Waiting " +
                            "for at least 1 confirmation");
                    infoDisplay.setText("Deposit transaction has bee published. You need to wait for at least one " +
                            "block " +
                            "chain confirmation. After that you need to make the payments transfer.");
                    break;
                case OFFERER_BUYER_START_PAYMENT:
                    processBar.setSelectedIndex(1);

                    statusTextField.setText("Deposit transaction has at least 1 confirmation. Start payment.");
                    infoDisplay.setText("Deposit transaction has at least one blockchain confirmation. You need to " +
                            "start " +
                            "the payment.");

                    paymentsGroupBg.setVisible(true);
                    paymentMethodLabel.setVisible(true);
                    holderNameLabel.setVisible(true);
                    primaryIdLabel.setVisible(true);
                    secondaryIdLabel.setVisible(true);
                    paymentMethodTextField.setVisible(true);
                    paymentsInfoDisplay.setVisible(true);
                    holderNameTextField.setVisible(true);
                    primaryIdTextField.setVisible(true);
                    secondaryIdTextField.setVisible(true);
                    paymentsButton.setVisible(true);

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

                    statusTextField.setText("Trade has successfully completed.");
                    infoDisplay.setText("Trade has successfully completed. You can find the details to that trade" +
                            " in the closed trades section.");

                    summaryGroupBg.setVisible(true);
                    btcLabel.setVisible(true);
                    btcTextField.setVisible(true);
                    fiatLabel.setVisible(true);
                    fiatTextField.setVisible(true);
                    feesLabel.setVisible(true);
                    feesTextField.setVisible(true);
                    collateralLabel.setVisible(true);
                    collateralTextField.setVisible(true);
                    summaryInfoDisplay.setVisible(true);

                    btcTextField.setText(presentationModel.getTradeVolume());
                    fiatTextField.setText(presentationModel.getFiatVolume());
                    feesTextField.setText(presentationModel.getTotalFees());
                    collateralTextField.setText(presentationModel.getCollateral());
                    summaryInfoDisplay.setText("You can open that summary any time in the closed orders section.");
                    break;
            }
        }
    }

    private void setupScreenForTaker() {
        titledGroupBg.setVisible(true);
        processBar.setVisible(true);
        statusLabel.setVisible(true);
        statusTextField.setVisible(true);
        txIdLabel.setVisible(true);
        txIdTextField.setVisible(true);
        infoDisplay.setVisible(true);

        summaryGroupBg.setVisible(false);
        btcLabel.setVisible(false);
        btcTextField.setVisible(false);
        fiatLabel.setVisible(false);
        fiatTextField.setVisible(false);
        feesLabel.setVisible(false);
        feesTextField.setVisible(false);
        collateralLabel.setVisible(false);
        collateralTextField.setVisible(false);
        summaryInfoDisplay.setVisible(false);

        log.debug("setupScreenForTaker");
        if (processBar.getProcessStepItems() == null) {
            List<ProcessStepItem> items = new ArrayList<>();
            items.add(new ProcessStepItem("Wait for block chain confirmation"));
            items.add(new ProcessStepItem("Wait for payment started"));
            items.add(new ProcessStepItem("Confirm  payment"));
            items.add(new ProcessStepItem("Trade successful completed"));
            processBar.setProcessStepItems(items);
        }

        txIdTextField.setup(presentationModel.getWalletFacade(), presentationModel.getTxID());

        presentationModel.state.addListener((ov, oldValue, newValue) -> applyTakerState(newValue));
        applyTakerState(presentationModel.state.get());
    }

    private void applyTakerState(PendingTradesPM.State state) {
        log.debug("#### state " + state);
        if (state != null) {
            confirmPaymentReceiptButton.setVisible(false);
            switch (state) {
                case TAKER_SELLER_WAIT_TX_CONF:
                    processBar.setSelectedIndex(0);
                    statusTextField.setText("Deposit transaction is published. Waiting for at least 1 confirmation");
                    infoDisplay.setText("Deposit transaction has bee published. He needs to wait for at least one " +
                            "blockchain " +
                            "confirmation.");
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
                    statusTextField.setText("Payment is on the way. Check your payments account and confirm when you " +
                            "have received the payment.");
                    infoDisplay.setText("The other trader has started the payment. You need to check your payments " +
                            "account and confirm the payment when the money has arrived there.");
                    confirmPaymentReceiptButton.setVisible(true);
                    break;
                case TAKER_SELLER_COMPLETED:
                    processBar.setSelectedIndex(3);

                    statusTextField.setText("Trade has successfully completed.");
                    infoDisplay.setText("Trade has successfully completed. You can find the details to that trade" +
                            " in the closed trades section.");

                    summaryGroupBg.setVisible(true);
                    btcLabel.setVisible(true);
                    btcTextField.setVisible(true);
                    fiatLabel.setVisible(true);
                    fiatTextField.setVisible(true);
                    feesLabel.setVisible(true);
                    feesTextField.setVisible(true);
                    collateralLabel.setVisible(true);
                    collateralTextField.setVisible(true);
                    summaryInfoDisplay.setVisible(true);

                    btcTextField.setText(presentationModel.getTradeVolume());
                    fiatTextField.setText(presentationModel.getFiatVolume());
                    feesTextField.setText(presentationModel.getTotalFees());
                    collateralTextField.setText(presentationModel.getCollateral());
                    summaryInfoDisplay.setText("You can open that summary any time in the closed orders section.");
                    break;
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    private void setCountryColumnCellFactory() {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        countryColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            final HBox hBox = new HBox();

                            {
                                hBox.setSpacing(3);
                                hBox.setAlignment(Pos.CENTER);
                                setGraphic(hBox);
                            }

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                hBox.getChildren().clear();
                                if (item != null) {
                                    Country country = item.getOffer().getBankAccountCountry();
                                    hBox.getChildren().add(ImageUtil.getCountryIconImageView(item
                                            .getOffer().getBankAccountCountry()));
                                    Tooltip.install(this, new Tooltip(country.getName()));
                                }
                            }
                        };
                    }
                });
    }

    private void setBankAccountTypeColumnCellFactory() {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getBankAccountType(item));
                            }
                        };
                    }
                });
    }

    private void setSelectColumnCellFactory() {
        selectColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
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
                presentationModel.selectPendingTrade(item);
            }
        });
    }


}

