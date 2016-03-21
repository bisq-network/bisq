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

package io.bitsquare.gui.main.funds.deposit;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.QRCodeWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.uri.BitcoinURI;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class DepositView extends ActivatableView<VBox, Void> {

    @FXML
    GridPane gridPane;

    @FXML
    TableView<DepositListItem> table;
    @FXML
    TableColumn<DepositListItem, DepositListItem> selectColumn, addressColumn, balanceColumn, confidenceColumn, statusColumn;
    private ImageView qrCodeImageView;
    private int gridRow = 0;
    private AddressTextField addressTextField;
    Button generateNewAddressButton;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final OpenOfferManager openOfferManager;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final BtcAddressValidator btcAddressValidator;
    private final WalletPasswordWindow walletPasswordWindow;
    private final OfferDetailsWindow offerDetailsWindow;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final ObservableList<DepositListItem> depositAddresses = FXCollections.observableArrayList();
    private BalanceListener balanceListener;
    private TitledGroupBg titledGroupBg;
    private Label addressLabel;
    private Label qrCodeLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private DepositView(WalletService walletService, TradeManager tradeManager,
                        ClosedTradableManager closedTradableManager,
                        FailedTradesManager failedTradesManager, OpenOfferManager openOfferManager,
                        BSFormatter formatter, Preferences preferences,
                        BtcAddressValidator btcAddressValidator, WalletPasswordWindow walletPasswordWindow,
                        OfferDetailsWindow offerDetailsWindow, TradeDetailsWindow tradeDetailsWindow) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.openOfferManager = openOfferManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.btcAddressValidator = btcAddressValidator;
        this.walletPasswordWindow = walletPasswordWindow;
        this.offerDetailsWindow = offerDetailsWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
    }

    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No deposit addresses are generated yet"));

        setSelectColumnCellFactory();
        setAddressColumnCellFactory();
        setStatusColumnCellFactory();
        setConfidenceColumnCellFactory();

        titledGroupBg = addTitledGroupBg(gridPane, gridRow, 2, "Fund your wallet");

        qrCodeLabel = addLabel(gridPane, gridRow, "QR-Code:", 0);
        //GridPane.setMargin(qrCodeLabel, new Insets(Layout.FIRST_ROW_DISTANCE - 9, 0, 0, 5));

        qrCodeImageView = new ImageView();
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip("Open large QR-Code window"));
        qrCodeImageView.setOnMouseClicked(e -> new QRCodeWindow(getBitcoinURI()).show());
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(qrCodeImageView);

        Tuple2<Label, AddressTextField> addressTuple = addLabelAddressTextField(gridPane, ++gridRow, "Address:");
        addressLabel = addressTuple.first;
        GridPane.setValignment(addressLabel, VPos.TOP);
        GridPane.setMargin(addressLabel, new Insets(3, 0, 0, 0));
        addressTextField = addressTuple.second;

        titledGroupBg.setVisible(false);
        titledGroupBg.setManaged(false);
        qrCodeLabel.setVisible(false);
        qrCodeLabel.setManaged(false);
        qrCodeImageView.setVisible(false);
        qrCodeImageView.setManaged(false);
        addressLabel.setVisible(false);
        addressLabel.setManaged(false);
        addressTextField.setVisible(false);
        addressTextField.setManaged(false);

        generateNewAddressButton = addButton(gridPane, ++gridRow, "Generate new address", -20);

        generateNewAddressButton.setOnAction(event -> {
            boolean hasUnUsedAddress = walletService.getSavingsAddressEntryList().stream()
                    .filter(addressEntry -> walletService.getBalanceForAddress(addressEntry.getAddress()).isZero())
                    .findAny().isPresent();
            if (hasUnUsedAddress) {
                new Popup().warning("You have already addresses generated which are still not used.\n" +
                        "Please select in the address table an unused address.").show();
            } else {
                AddressEntry newSavingsAddressEntry = walletService.getNewSavingsAddressEntry();
                fillForm(newSavingsAddressEntry.getAddressString());
                updateList();
            }
        });

        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateList();
            }
        };
    }

    @NotNull
    private String getBitcoinURI() {
        return BitcoinURI.convertToBitcoinURI(addressTextField.getAddress(),
                null,
                null,
                null);
    }

    @Override
    protected void activate() {
        updateList();

        walletService.addBalanceListener(balanceListener);
    }

    @Override
    protected void deactivate() {
        depositAddresses.forEach(DepositListItem::cleanup);
        walletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void fillForm(String address) {
        titledGroupBg.setVisible(true);
        titledGroupBg.setManaged(true);
        qrCodeLabel.setVisible(true);
        qrCodeLabel.setManaged(true);
        qrCodeImageView.setVisible(true);
        qrCodeImageView.setManaged(true);
        addressLabel.setVisible(true);
        addressLabel.setManaged(true);
        addressTextField.setVisible(true);
        addressTextField.setManaged(true);

        GridPane.setMargin(generateNewAddressButton, new Insets(15, 0, 0, 0));

        addressTextField.setAddress(address);

        final byte[] imageBytes = QRCode
                .from(getBitcoinURI())
                .withSize(150, 150) // code has 41 elements 8 px is border with 150 we get 3x scale and min. border
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView.setImage(qrImage);

    }

    private void openBlockExplorer(DepositListItem item) {
        if (item.getAddressString() != null) {
            try {
                Utilities.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
            } catch (Exception e) {
                log.error(e.getMessage());
                new Popup().warning("Opening browser failed. Please check your internet " +
                        "connection.").show();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        depositAddresses.clear();
        walletService.getSavingsAddressEntryList().stream()
                .forEach(e -> depositAddresses.add(new DepositListItem(e, walletService, formatter)));
        table.setItems(depositAddresses);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setStatusColumnCellFactory() {
        statusColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        statusColumn.setCellFactory(new Callback<TableColumn<DepositListItem, DepositListItem>,
                TableCell<DepositListItem, DepositListItem>>() {

            @Override
            public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                    DepositListItem> column) {
                return new TableCell<DepositListItem, DepositListItem>() {

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setGraphic(new Label(item.getStatus()));
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void setSelectColumnCellFactory() {
        selectColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        selectColumn.setCellFactory(
                new Callback<TableColumn<DepositListItem, DepositListItem>, TableCell<DepositListItem,
                        DepositListItem>>() {

                    @Override
                    public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                            DepositListItem> column) {
                        return new TableCell<DepositListItem, DepositListItem>() {

                            Button button;

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new Button("Select");
                                        button.setOnAction(e -> fillForm(item.getAddressString()));
                                        setGraphic(button);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        addressColumn.setCellFactory(
                new Callback<TableColumn<DepositListItem, DepositListItem>, TableCell<DepositListItem,
                        DepositListItem>>() {

                    @Override
                    public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                            DepositListItem> column) {
                        return new TableCell<DepositListItem, DepositListItem>() {

                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String addressString = item.getAddressString();
                                    field = new HyperlinkWithIcon(addressString, AwesomeIcon.EXTERNAL_LINK);
                                    field.setOnAction(event -> openBlockExplorer(item));
                                    field.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "address: " + addressString));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<TableColumn<DepositListItem, DepositListItem>, TableCell<DepositListItem,
                        DepositListItem>>() {

                    @Override
                    public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                            DepositListItem> column) {
                        return new TableCell<DepositListItem, DepositListItem>() {

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getProgressIndicator());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }
}


