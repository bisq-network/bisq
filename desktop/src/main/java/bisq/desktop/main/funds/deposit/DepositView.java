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

package bisq.desktop.main.funds.deposit;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AddressTextField;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.QRCodeWindow;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.fxml.FXML;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.io.ByteArrayInputStream;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addAddressTextField;
import static bisq.desktop.util.FormBuilder.addButton;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class DepositView extends ActivatableView<VBox, Void> {

    @FXML
    GridPane gridPane;
    @FXML
    TableView<DepositListItem> tableView;
    @FXML
    TableColumn<DepositListItem, DepositListItem> selectColumn, addressColumn, balanceColumn, confirmationsColumn, usageColumn;
    private ImageView qrCodeImageView;
    private AddressTextField addressTextField;
    private Button generateNewAddressButton;
    private TitledGroupBg titledGroupBg;
    private InputTextField amountTextField;

    private final BtcWalletService walletService;
    private final Preferences preferences;
    private final BSFormatter formatter;
    private String paymentLabelString;
    private final ObservableList<DepositListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<DepositListItem> sortedList = new SortedList<>(observableList);
    private BalanceListener balanceListener;
    private Subscription amountTextFieldSubscription;
    private ChangeListener<DepositListItem> tableViewSelectionListener;
    private int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private DepositView(BtcWalletService walletService,
                        FeeService feeService,
                        Preferences preferences,
                        BSFormatter formatter) {
        this.walletService = walletService;
        this.preferences = preferences;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {

        paymentLabelString = Res.get("funds.deposit.fundBisqWallet");
        selectColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.select")));
        addressColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.address")));
        balanceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.balanceWithCur", Res.getBaseCurrencyCode())));
        confirmationsColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.confirmations")));
        usageColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.usage")));

        // trigger creation of at least 1 savings address
        walletService.getFreshAddressEntry();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("funds.deposit.noAddresses")));
        tableViewSelectionListener = (observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                fillForm(newValue.getAddressString());
                GUIUtil.requestFocus(amountTextField);
            }
        };

        setSelectColumnCellFactory();
        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();
        setUsageColumnCellFactory();
        setConfidenceColumnCellFactory();

        addressColumn.setComparator(Comparator.comparing(DepositListItem::getAddressString));
        balanceColumn.setComparator(Comparator.comparing(DepositListItem::getBalanceAsCoin));
        confirmationsColumn.setComparator(Comparator.comparingDouble(o -> o.getTxConfidenceIndicator().getProgress()));
        usageColumn.setComparator(Comparator.comparingInt(DepositListItem::getNumTxOutputs));
        tableView.getSortOrder().add(usageColumn);
        tableView.setItems(sortedList);

        titledGroupBg = addTitledGroupBg(gridPane, gridRow, 4, Res.get("funds.deposit.fundWallet"));
        titledGroupBg.getStyleClass().add("last");

        qrCodeImageView = new ImageView();
        qrCodeImageView.getStyleClass().add("qr-code");
        Tooltip.install(qrCodeImageView, new Tooltip(Res.get("shared.openLargeQRWindow")));
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setRowSpan(qrCodeImageView, 4);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 10));
        gridPane.getChildren().add(qrCodeImageView);

        addressTextField = addAddressTextField(gridPane, ++gridRow, Res.get("shared.address"), Layout.FIRST_ROW_DISTANCE);
        addressTextField.setPaymentLabel(paymentLabelString);


        amountTextField = addInputTextField(gridPane, ++gridRow, Res.get("funds.deposit.amount"));
        amountTextField.setMaxWidth(380);
        if (DevEnv.isDevMode())
            amountTextField.setText("10");

        titledGroupBg.setVisible(false);
        titledGroupBg.setManaged(false);
        qrCodeImageView.setVisible(false);
        qrCodeImageView.setManaged(false);
        addressTextField.setVisible(false);
        addressTextField.setManaged(false);
        amountTextField.setManaged(false);

        generateNewAddressButton = addButton(gridPane, ++gridRow, Res.get("funds.deposit.generateAddress"), -20);
        GridPane.setColumnIndex(generateNewAddressButton, 0);
        GridPane.setHalignment(generateNewAddressButton, HPos.LEFT);

        generateNewAddressButton.setOnAction(event -> {
            boolean hasUnUsedAddress = observableList.stream().anyMatch(e -> e.getNumTxOutputs() == 0);
            if (hasUnUsedAddress) {
                new Popup<>().warning(Res.get("funds.deposit.selectUnused")).show();
            } else {
                AddressEntry newSavingsAddressEntry = walletService.getFreshAddressEntry();
                updateList();
                observableList.stream()
                        .filter(depositListItem -> depositListItem.getAddressString().equals(newSavingsAddressEntry.getAddressString()))
                        .findAny()
                        .ifPresent(depositListItem -> tableView.getSelectionModel().select(depositListItem));
            }
        });

        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateList();
            }
        };

        GUIUtil.focusWhenAddedToScene(amountTextField);
    }

    @Override
    protected void activate() {
        tableView.getSelectionModel().selectedItemProperty().addListener(tableViewSelectionListener);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        updateList();

        walletService.addBalanceListener(balanceListener);
        amountTextFieldSubscription = EasyBind.subscribe(amountTextField.textProperty(), t -> {
            addressTextField.setAmountAsCoin(formatter.parseToCoin(t));
            updateQRCode();
        });

        if (tableView.getSelectionModel().getSelectedItem() == null && !sortedList.isEmpty())
            tableView.getSelectionModel().select(0);
    }

    @Override
    protected void deactivate() {
        tableView.getSelectionModel().selectedItemProperty().removeListener(tableViewSelectionListener);
        sortedList.comparatorProperty().unbind();
        observableList.forEach(DepositListItem::cleanup);
        walletService.removeBalanceListener(balanceListener);
        amountTextFieldSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void fillForm(String address) {
        titledGroupBg.setVisible(true);
        titledGroupBg.setManaged(true);
        qrCodeImageView.setVisible(true);
        qrCodeImageView.setManaged(true);
        addressTextField.setVisible(true);
        addressTextField.setManaged(true);
        amountTextField.setManaged(true);

        GridPane.setMargin(generateNewAddressButton, new Insets(15, 0, 0, 0));

        addressTextField.setAddress(address);

        updateQRCode();
    }

    private void updateQRCode() {
        if (addressTextField.getAddress() != null && !addressTextField.getAddress().isEmpty()) {
            final byte[] imageBytes = QRCode
                    .from(getBitcoinURI())
                    .withSize(150, 150) // code has 41 elements 8 px is border with 150 we get 3x scale and min. border
                    .to(ImageType.PNG)
                    .stream()
                    .toByteArray();
            Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
            qrCodeImageView.setImage(qrImage);
        }
    }

    private void openBlockExplorer(DepositListItem item) {
        if (item.getAddressString() != null)
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        observableList.forEach(DepositListItem::cleanup);
        observableList.clear();
        walletService.getAvailableAddressEntries()
                .forEach(e -> observableList.add(new DepositListItem(e, walletService, formatter)));
    }

    private Coin getAmountAsCoin() {
        return formatter.parseToCoin(amountTextField.getText());
    }

    @NotNull
    private String getBitcoinURI() {
        return GUIUtil.getBitcoinURI(addressTextField.getAddress(),
                getAmountAsCoin(),
                paymentLabelString);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setUsageColumnCellFactory() {
        usageColumn.getStyleClass().add("last-column");
        usageColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        usageColumn.setCellFactory(new Callback<>() {

            @Override
            public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                    DepositListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setGraphic(new AutoTooltipLabel(item.getUsage()));
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void setSelectColumnCellFactory() {
        selectColumn.getStyleClass().add("first-column");
        selectColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        selectColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                            DepositListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("shared.select"));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> tableView.getSelectionModel().select(item));
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
                new Callback<>() {

                    @Override
                    public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                            DepositListItem> column) {
                        return new TableCell<>() {

                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String address = item.getAddressString();
                                    field = new HyperlinkWithIcon(address, AwesomeIcon.EXTERNAL_LINK);
                                    field.setOnAction(event -> {
                                        openBlockExplorer(item);
                                        tableView.getSelectionModel().select(item);
                                    });
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", address)));
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

    private void setBalanceColumnCellFactory() {
        balanceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        balanceColumn.setCellFactory(new Callback<>() {

            @Override
            public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                    DepositListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            if (textProperty().isBound())
                                textProperty().unbind();

                            textProperty().bind(item.balanceProperty());
                        } else {
                            textProperty().unbind();
                            setText("");
                        }
                    }
                };
            }
        });
    }


    private void setConfidenceColumnCellFactory() {
        confirmationsColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        confirmationsColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<DepositListItem, DepositListItem> call(TableColumn<DepositListItem,
                            DepositListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final DepositListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getTxConfidenceIndicator());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }
}


