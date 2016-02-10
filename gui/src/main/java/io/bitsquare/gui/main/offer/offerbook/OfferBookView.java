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

package io.bitsquare.gui.main.offer.offerbook;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.content.paymentsaccount.PaymentAccountView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import javafx.util.StringConverter;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class OfferBookView extends ActivatableViewAndModel<GridPane, OfferBookViewModel> {

    private final Navigation navigation;
    private final OfferDetailsPopup offerDetailsPopup;

    private ComboBox<TradeCurrency> currencyComboBox;
    private ComboBox<PaymentMethod> paymentMethodComboBox;
    private Button createOfferButton;
    private TableColumn<OfferBookListItem, OfferBookListItem> amountColumn, volumeColumn, priceColumn, paymentMethodColumn;
    private TableView<OfferBookListItem> tableView;

    private OfferView.OfferActionHandler offerActionHandler;
    private int gridRow = 0;
    private TableGroupHeadline offerBookTitle;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBookView(OfferBookViewModel model, Navigation navigation, OfferDetailsPopup offerDetailsPopup) {
        super(model);

        this.navigation = navigation;
        this.offerDetailsPopup = offerDetailsPopup;
    }

    @Override
    public void initialize() {
        root.setPadding(new Insets(30, 25, -1, 25));
        addTitledGroupBg(root, gridRow, 2, "Filter offer book");

        currencyComboBox = addLabelComboBox(root, gridRow, "Filter by currency:", Layout.FIRST_ROW_DISTANCE).second;
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getCodeAndName();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });

        paymentMethodComboBox = addLabelComboBox(root, ++gridRow, "Filter by payment method:").second;
        paymentMethodComboBox.setPromptText("Select payment method");
        paymentMethodComboBox.setConverter(new StringConverter<PaymentMethod>() {
            @Override
            public String toString(PaymentMethod paymentMethod) {
                return BSResources.get(paymentMethod.getId());
            }

            @Override
            public PaymentMethod fromString(String s) {
                return null;
            }
        });


        // createOfferButton
        createOfferButton = addButtonAfterGroup(root, ++gridRow, "Create new offer");

        offerBookTitle = new TableGroupHeadline("");
        GridPane.setRowIndex(offerBookTitle, ++gridRow);
        GridPane.setColumnSpan(offerBookTitle, 2);
        GridPane.setMargin(offerBookTitle, new Insets(20, -10, -10, -10));
        root.getChildren().add(offerBookTitle);

        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(40, -10, -15, -10));
        root.getChildren().add(tableView);
        amountColumn = getAmountColumn();
        tableView.getColumns().add(amountColumn);
        priceColumn = getPriceColumn();
        tableView.getColumns().add(priceColumn);
        volumeColumn = getVolumeColumn();
        tableView.getColumns().add(volumeColumn);
        paymentMethodColumn = getPaymentMethodColumn();
        tableView.getColumns().add(paymentMethodColumn);
        tableView.getColumns().add(getActionColumn());

        tableView.getSortOrder().add(priceColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there are no offers available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        priceColumn.setComparator((o1, o2) -> o1.getOffer().getPrice().compareTo(o2.getOffer().getPrice()));
        amountColumn.setComparator((o1, o2) -> o1.getOffer().getAmount().compareTo(o2.getOffer().getAmount()));
        volumeColumn.setComparator((o1, o2) -> o1.getOffer().getOfferVolume().compareTo(o2.getOffer().getOfferVolume()));
        paymentMethodColumn.setComparator((o1, o2) -> o1.getOffer().getPaymentMethod().compareTo(o2.getOffer().getPaymentMethod()));
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        paymentMethodComboBox.setItems(model.getPaymentMethods());
        paymentMethodComboBox.getSelectionModel().select(0);

        currencyComboBox.setOnAction(e -> model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem()));
        paymentMethodComboBox.setOnAction(e -> model.onSetPaymentMethod(paymentMethodComboBox.getSelectionModel().getSelectedItem()));
        createOfferButton.setOnAction(e -> onCreateOffer());
        volumeColumn.textProperty().bind(createStringBinding(
                () -> BSResources.get("Amount in {0} (Min.)", model.tradeCurrencyCode.get()), model.tradeCurrencyCode));
        model.getOfferList().comparatorProperty().bind(tableView.comparatorProperty());


        tableView.setItems(model.getOfferList());
        priceColumn.setSortType((model.getDirection() == Offer.Direction.BUY) ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
        tableView.sort();
    }

    @Override
    protected void deactivate() {
        currencyComboBox.setOnAction(null);
        paymentMethodComboBox.setOnAction(null);
        createOfferButton.setOnAction(null);
        volumeColumn.textProperty().unbind();
        model.getOfferList().comparatorProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void enableCreateOfferButton() {
        createOfferButton.setDisable(false);
    }

    public void setDirection(Offer.Direction direction) {
        offerBookTitle.setText(direction == Offer.Direction.SELL ? "Offers for buying bitcoin " : "Offers for selling bitcoin ");
        model.setDirection(direction);
    }

    public void setOfferActionHandler(OfferView.OfferActionHandler offerActionHandler) {
        this.offerActionHandler = offerActionHandler;
    }

    public void onTabSelected(boolean isSelected) {
        model.onTabSelected(isSelected);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onCreateOffer() {
        if (!model.hasPaymentAccount()) {
            showWarning("You don't have setup a payment account yet.",
                    "You need to setup your payment account before you can trade.\nDo you want to do this now?", PaymentAccountView.class);
        } else if (!model.hasPaymentAccountForCurrency()) {
            showWarning("You don't have a payment account with that selected currency.",
                    "You need to setup a payment account for the selected currency to be able to trade in that currency.\n" +
                            "Do you want to do this now?", PaymentAccountView.class);
        } else if (!model.hasAcceptedArbitrators()) {
            showWarning("You don't have an arbitrator selected.",
                    "You need to setup at least one arbitrator to be able to trade.\n" +
                            "Do you want to do this now?", ArbitratorSelectionView.class);
        } else {
            createOfferButton.setDisable(true);
            offerActionHandler.onCreateOffer(model.getTradeCurrency());
        }
    }

    private void onShowInfo(boolean isPaymentAccountValidForOffer, boolean hasMatchingArbitrator, boolean hasSameProtocolVersion) {
        if (!hasMatchingArbitrator) {
            showWarning("You don't have an arbitrator selected.",
                    "You need to setup at least one arbitrator to be able to trade.\n" +
                            "Do you want to do this now?", ArbitratorSelectionView.class);
        } else if (!isPaymentAccountValidForOffer) {
            showWarning("You don't have a payment account with the payment method required for that offer.",
                    "You need to setup a payment account with that payment method if you want to take that offer.\n" +
                            "Do you want to do this now?", PaymentAccountView.class);
        } else if (!hasSameProtocolVersion) {
            new Popup().information("That offer requires a different protocol version as the one used in your " +
                    "version of the software." +
                    "\n\n" + "Please check if you have the latest version installed, otherwise the user " +
                    "who created the offer has used an older version.\n" +
                    "You cannot trade with an incompatible protocol version.")
                    .show();
        }
    }

    private void onTakeOffer(Offer offer) {
        if (model.isBootstrapped())
            offerActionHandler.onTakeOffer(offer);
        else
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
    }

    private void onRemoveOpenOffer(Offer offer) {
        if (model.isBootstrapped()) {
            new Popup().warning("Are you sure you want to remove that offer?\n" +
                    "The offer fee you have paid will be lost if you remove that offer.")
                    .actionButtonText("Remove offer")
                    .onAction(() -> doRemoveOffer(offer))
                    .closeButtonText("Don't remove the offer")
                    .show();
        } else {
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void doRemoveOffer(Offer offer) {
        model.onRemoveOpenOffer(offer,
                () -> {
                    log.debug("Remove offer was successful");
                    new Popup().information("You can withdraw the funds you paid in from the funds screens.").show();
                    navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                },
                (message) -> {
                    log.error(message);
                    new Popup().warning("Remove offer failed:\n" + message).show();
                });
    }

    private void showWarning(String masthead, String message, Class target) {
        new Popup().information(masthead + "\n\n" + message)
                .onAction(() -> {
                    navigation.setReturnPath(navigation.getCurrentPath());
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, target);
                }).show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<OfferBookListItem, OfferBookListItem> getAmountColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("Amount in BTC (Min.)") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getAmount(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getPriceColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("Price") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getPrice(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getVolumeColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>() {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getPaymentMethodColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("Payment method") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem, OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getPaymentMethod(item), true);
                                    field.setOnAction(event -> offerDetailsPopup.show(item.getOffer()));
                                    field.setTooltip(new Tooltip(model.getPaymentMethodToolTip(item)));
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
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getActionColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("I want to:") {
            {
                setMinWidth(80);
                setSortable(false);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            final ImageView iconView = new ImageView();
                            final Button button = new Button();
                            boolean isTradable;
                            boolean isPaymentAccountValidForOffer;
                            boolean hasMatchingArbitrator;
                            boolean hasSameProtocolVersion;

                            {
                                button.setGraphic(iconView);
                                button.setMinWidth(70);
                            }

                            @Override
                            public void updateItem(final OfferBookListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty) {
                                    final Offer offer = newItem.getOffer();

                                    TableRow tableRow = getTableRow();
                                    if (tableRow != null) {
                                        isPaymentAccountValidForOffer = model.isPaymentAccountValidForOffer(offer);
                                        hasMatchingArbitrator = model.hasMatchingArbitrator(offer);
                                        hasSameProtocolVersion = model.hasSameProtocolVersion(offer);
                                        isTradable = isPaymentAccountValidForOffer && hasMatchingArbitrator &&
                                                hasSameProtocolVersion;

                                        tableRow.setOpacity(isTradable ? 1 : 0.4);

                                        if (isTradable) {
                                            // set first row button as default
                                            button.setDefaultButton(getIndex() == 0);
                                            tableRow.setOnMouseClicked(null);
                                        } else {
                                            button.setDefaultButton(false);
                                            tableRow.setOnMouseClicked(e ->
                                                    onShowInfo(isPaymentAccountValidForOffer, hasMatchingArbitrator,
                                                            hasSameProtocolVersion));
                                        }
                                    }
                                    
                                    String title;
                                    if (isTradable) {
                                        if (model.isMyOffer(offer)) {
                                            iconView.setId("image-remove");
                                            title = "Remove";
                                            button.setOnAction(e -> onRemoveOpenOffer(offer));
                                        } else {
                                            iconView.setId(offer.getDirection() == Offer.Direction.SELL ? "image-buy" : "image-sell");
                                            title = model.getDirectionLabel(offer);
                                            button.setOnAction(e -> onTakeOffer(offer));
                                        }
                                    } else {
                                        title = "Not matching";
                                        iconView.setId(null);
                                        button.setOnAction(e -> onShowInfo(isPaymentAccountValidForOffer, hasMatchingArbitrator, hasSameProtocolVersion));
                                    }

                                    button.setText(title);
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
                                    if (button != null)
                                        button.setOnAction(null);
                                    TableRow tableRow = getTableRow();
                                    if (tableRow != null) tableRow.setOpacity(1);
                                }
                            }
                        };
                    }
                });
        return column;
    }
}

