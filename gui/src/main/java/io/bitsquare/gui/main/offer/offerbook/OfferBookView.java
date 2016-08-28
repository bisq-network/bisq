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

import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.PeerInfoIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.content.fiataccounts.FiatAccountsView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.CurrencyListItem;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.bitcoinj.utils.Fiat;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javax.inject.Inject;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class OfferBookView extends ActivatableViewAndModel<GridPane, OfferBookViewModel> {

    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private BSFormatter formatter;
    private PrivateNotificationManager privateNotificationManager;

    private ComboBox<CurrencyListItem> currencyComboBox;
    private ComboBox<PaymentMethod> paymentMethodComboBox;
    private Button createOfferButton;
    private TableColumn<OfferBookListItem, OfferBookListItem> amountColumn, volumeColumn, marketColumn, priceColumn, paymentMethodColumn, avatarColumn;
    private TableView<OfferBookListItem> tableView;

    private OfferView.OfferActionHandler offerActionHandler;
    private int gridRow = 0;
    private TitledGroupBg offerBookTitle;
    private Label nrOfOffersLabel;
    private ListChangeListener<OfferBookListItem> offerListListener;
    private MonadicBinding<Void> currencySelectionBinding;
    private Subscription currencySelectionSubscriber;
    private ListChangeListener<CurrencyListItem> currencyListItemsListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBookView(OfferBookViewModel model, Navigation navigation, OfferDetailsWindow offerDetailsWindow, BSFormatter formatter, PrivateNotificationManager privateNotificationManager) {
        super(model);

        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
        this.formatter = formatter;
        this.privateNotificationManager = privateNotificationManager;
    }

    @Override
    public void initialize() {
        root.setPadding(new Insets(20, 25, 5, 25));

        offerBookTitle = addTitledGroupBg(root, gridRow, 3, "Available offers");

        currencyComboBox = addLabelComboBox(root, gridRow, "Filter by currency:", Layout.FIRST_ROW_DISTANCE).second;
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(GUIUtil.getCurrencyListItemConverter("offers", model.preferences));

        paymentMethodComboBox = addLabelComboBox(root, ++gridRow, "Filter by payment method:").second;
        paymentMethodComboBox.setPromptText("Select payment method");
        paymentMethodComboBox.setVisibleRowCount(20);
        paymentMethodComboBox.setConverter(new StringConverter<PaymentMethod>() {
            @Override
            public String toString(PaymentMethod paymentMethod) {
                String id = paymentMethod.getId();
                if (id.equals(GUIUtil.SHOW_ALL_FLAG))
                    return "▶ Show all";
                else if (paymentMethod.equals(PaymentMethod.BLOCK_CHAINS))
                    return "✦ " + BSResources.get(id);
                else
                    return "★ " + BSResources.get(id);
            }

            @Override
            public PaymentMethod fromString(String s) {
                return null;
            }
        });

        tableView = new TableView<>();

        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setColumnIndex(tableView, 0);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(10, -10, -10, -10));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        marketColumn = getMarketColumn();

        priceColumn = getPriceColumn();
        tableView.getColumns().add(priceColumn);
        amountColumn = getAmountColumn();
        tableView.getColumns().add(amountColumn);
        volumeColumn = getVolumeColumn();
        tableView.getColumns().add(volumeColumn);
        paymentMethodColumn = getPaymentMethodColumn();
        tableView.getColumns().add(paymentMethodColumn);
        tableView.getColumns().add(getActionColumn());
        avatarColumn = getAvatarColumn();
        tableView.getColumns().add(avatarColumn);

        tableView.getSortOrder().add(priceColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there are no offers available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        priceColumn.setComparator((o1, o2) -> {
            Fiat price1 = o1.getOffer().getPrice();
            Fiat price2 = o2.getOffer().getPrice();
            return price1 != null && price2 != null ? price1.compareTo(price2) : 0;
        });
        amountColumn.setComparator((o1, o2) -> o1.getOffer().getAmount().compareTo(o2.getOffer().getAmount()));
        volumeColumn.setComparator((o1, o2) -> {
            Fiat offerVolume1 = o1.getOffer().getOfferVolume();
            Fiat offerVolume2 = o2.getOffer().getOfferVolume();
            return offerVolume1 != null && offerVolume2 != null ? offerVolume1.compareTo(offerVolume2) : 0;
        });
        paymentMethodColumn.setComparator((o1, o2) -> o1.getOffer().getPaymentMethod().compareTo(o2.getOffer().getPaymentMethod()));
        avatarColumn.setComparator((o1, o2) -> o1.getOffer().getOwnerNodeAddress().hostName.compareTo(o2.getOffer().getOwnerNodeAddress().hostName));

        nrOfOffersLabel = new Label("");
        nrOfOffersLabel.setId("num-offers");
        GridPane.setHalignment(nrOfOffersLabel, HPos.LEFT);
        GridPane.setVgrow(nrOfOffersLabel, Priority.NEVER);
        GridPane.setValignment(nrOfOffersLabel, VPos.TOP);
        GridPane.setRowIndex(nrOfOffersLabel, ++gridRow);
        GridPane.setColumnIndex(nrOfOffersLabel, 0);
        GridPane.setMargin(nrOfOffersLabel, new Insets(10, 0, 0, -5));
        root.getChildren().add(nrOfOffersLabel);

        createOfferButton = addButton(root, gridRow, "");
        createOfferButton.setMinHeight(40);
        createOfferButton.setPadding(new Insets(0, 20, 0, 20));
        createOfferButton.setGraphicTextGap(10);
        GridPane.setMargin(createOfferButton, new Insets(15, 0, 0, 0));
        GridPane.setHalignment(createOfferButton, HPos.RIGHT);
        GridPane.setVgrow(createOfferButton, Priority.NEVER);
        GridPane.setValignment(createOfferButton, VPos.TOP);
        offerListListener = c -> nrOfOffersLabel.setText("Nr. of offers: " + model.getOfferList().size());
        currencyListItemsListener = c -> applyCurrencyComboBoxSelection();
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getCurrencyListItems());
        currencyComboBox.setVisibleRowCount(25);

        model.currencyListItems.addListener(currencyListItemsListener);

        applyCurrencyComboBoxSelection();

        currencyComboBox.setOnAction(e -> {
            CurrencyListItem selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();

            if (selectedItem != null)
                model.onSetTradeCurrency(selectedItem.tradeCurrency);
        });

        priceColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());
        volumeColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());

        paymentMethodComboBox.setItems(model.getPaymentMethods());
        paymentMethodComboBox.setOnAction(e -> model.onSetPaymentMethod(paymentMethodComboBox.getSelectionModel().getSelectedItem()));
        if (model.showAllPaymentMethods)
            paymentMethodComboBox.getSelectionModel().select(0);
        else
            paymentMethodComboBox.getSelectionModel().select(model.selectedPaymentMethod);

        createOfferButton.setOnAction(e -> onCreateOffer());

        currencySelectionBinding = EasyBind.combine(
                model.showAllTradeCurrenciesProperty, model.tradeCurrencyCode,
                (showAll, code) -> {
                    setDirectionTitles();
                    if (showAll) {
                        volumeColumn.setText("Amount (min - max)");
                        priceColumn.setText("Price");

                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(0, marketColumn);
                    } else {
                        volumeColumn.setText("Amount in " + code + " (min - max)");
                        priceColumn.setText(formatter.getPriceWithCurrencyCode(code));

                        if (tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().remove(marketColumn);
                    }

                    return null;
                });

        currencySelectionSubscriber = currencySelectionBinding.subscribe((observable, oldValue, newValue) -> {
        });

        model.getOfferList().comparatorProperty().bind(tableView.comparatorProperty());

        tableView.setItems(model.getOfferList());
        priceColumn.setSortType((model.getDirection() == Offer.Direction.BUY) ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);

        model.getOfferList().addListener(offerListListener);
        nrOfOffersLabel.setText("Nr. of offers: " + model.getOfferList().size());
    }

    @Override
    protected void deactivate() {
        currencyComboBox.setOnAction(null);
        paymentMethodComboBox.setOnAction(null);
        createOfferButton.setOnAction(null);
        model.getOfferList().comparatorProperty().unbind();

        priceColumn.sortableProperty().unbind();
        amountColumn.sortableProperty().unbind();
        model.getOfferList().removeListener(offerListListener);
        model.currencyListItems.removeListener(currencyListItemsListener);
        currencySelectionSubscriber.unsubscribe();
    }

    private void applyCurrencyComboBoxSelection() {
        Optional<CurrencyListItem> selectedCurrencyListItem = model.getSelectedCurrencyListItem();
        UserThread.execute(() -> {
            if (model.showAllTradeCurrenciesProperty.get() || !selectedCurrencyListItem.isPresent())
                currencyComboBox.getSelectionModel().select(model.getShowAllCurrencyListItem());
            else
                currencyComboBox.getSelectionModel().select(selectedCurrencyListItem.get());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void enableCreateOfferButton() {
        createOfferButton.setDisable(false);
    }

    public void setDirection(Offer.Direction direction) {
        model.initWithDirection(direction);
        ImageView iconView = new ImageView();

        createOfferButton.setGraphic(iconView);
        iconView.setId(direction == Offer.Direction.SELL ? "image-sell-white" : "image-buy-white");
        createOfferButton.setId(direction == Offer.Direction.SELL ? "sell-button-big" : "buy-button-big");

        setDirectionTitles();
    }

    private void setDirectionTitles() {
        TradeCurrency selectedTradeCurrency = model.getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            Offer.Direction direction = model.getDirection();
            String preFix = "Create new offer for ";
            String directionText = direction == Offer.Direction.BUY ? "buying" : "selling";
            String mirroredDirectionText = direction == Offer.Direction.SELL ? "buying" : "selling";
            String code = selectedTradeCurrency.getCode();
            if (model.showAllTradeCurrenciesProperty.get())
                createOfferButton.setText(preFix + directionText + " BTC");
            else if (selectedTradeCurrency instanceof FiatCurrency)
                createOfferButton.setText(preFix + directionText + " BTC" + (direction == Offer.Direction.BUY ? " with " : " for ") + code);
            else
                createOfferButton.setText(preFix + mirroredDirectionText + " " + code + " (" + directionText + " BTC)");
        }
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
            openPopupForMissingAccountSetup("You have not setup a payment account",
                    "You need to setup a national currency or altcoin account before you can create an offer.\n" +
                            "Do you want to setup an account?", FiatAccountsView.class, "\"Account\"");
        } else if (!model.hasPaymentAccountForCurrency()) {
            openPopupForMissingAccountSetup("No matching payment account",
                    "You don't have a payment account for the currency required for that offer.\n" +
                            "You need to setup a payment account for that currency to be able to take this offer.\n" +
                            "Do you want to do this now?", FiatAccountsView.class, "\"Account\"");
        } else if (!model.hasAcceptedArbitrators()) {
            openPopupForMissingAccountSetup("You don't have an arbitrator selected.",
                    "You need to setup at least one arbitrator to be able to trade.\n" +
                            "Do you want to do this now?", ArbitratorSelectionView.class, "\"Arbitrator selection\"");
        } else {
            createOfferButton.setDisable(true);
            offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
        }
    }

    private void onShowInfo(boolean isPaymentAccountValidForOffer, boolean hasMatchingArbitrator,
                            boolean hasSameProtocolVersion, boolean isIgnored,
                            boolean isOfferBanned, boolean isNodeBanned) {
        if (!hasMatchingArbitrator) {
            openPopupForMissingAccountSetup("You don't have an arbitrator selected.",
                    "You need to setup at least one arbitrator to be able to trade.\n" +
                            "Do you want to do this now?", ArbitratorSelectionView.class, "\"Arbitrator selection\"");
        } else if (!isPaymentAccountValidForOffer) {
            openPopupForMissingAccountSetup("No matching payment account",
                    "You don't have a payment account with the payment method required for that offer.\n" +
                            "You need to setup a payment account with that payment method if you want to take this offer.\n" +
                            "Do you want to do this now?", FiatAccountsView.class, "\"Account\"");
        } else if (!hasSameProtocolVersion) {
            new Popup().warning("That offer requires a different protocol version as the one used in your " +
                    "version of the software.\n\n" +
                    "Please check if you have the latest version installed, otherwise the user " +
                    "who created the offer has used an older version.\n\n" +
                    "Users cannot trade with an incompatible trade protocol version.")
                    .show();
        } else if (isIgnored) {
            new Popup().warning("You have added that users onion address to your ignore list.")
                    .show();
        } else if (isOfferBanned) {
            new Popup().warning("That offer was blocked by the Bitsquare developers.\n" +
                    "Probably there is an unhandled bug causing issues when taking that offer.")
                    .show();
        } else if (isNodeBanned) {
            new Popup().warning("The onion address of that trader was blocked by the Bitsquare developers.\n" +
                    "Probably there is an unhandled bug causing issues when taking offers from that trader.")
                    .show();
        }
    }

    private void onTakeOffer(Offer offer) {
        if (model.isBootstrapped())
            offerActionHandler.onTakeOffer(offer);
        else
            new Popup().information("You need to wait until you are fully connected to the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
    }

    private void onRemoveOpenOffer(Offer offer) {
        if (model.isBootstrapped()) {
            String key = "RemoveOfferWarning";
            if (model.preferences.showAgain(key))
                new Popup().warning("Are you sure you want to remove that offer?\n" +
                        "The offer fee you have paid will be lost if you remove that offer.")
                        .actionButtonText("Remove offer")
                        .onAction(() -> doRemoveOffer(offer))
                        .closeButtonText("Don't remove the offer")
                        .dontShowAgainId(key, model.preferences)
                        .show();
            else
                doRemoveOffer(offer);
        } else {
            new Popup().information("You need to wait until you are fully connected to the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void doRemoveOffer(Offer offer) {
        String key = "WithdrawFundsAfterRemoveOfferInfo";
        model.onRemoveOpenOffer(offer,
                () -> {
                    log.debug("Remove offer was successful");
                    if (model.preferences.showAgain(key))
                        new Popup().instruction("You can withdraw the funds you paid in from the \"Fund/Available for withdrawal\" screen.")
                                .actionButtonText("Go to \"Funds/Available for withdrawal\"")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key, model.preferences)
                                .show();
                },
                (message) -> {
                    log.error(message);
                    new Popup().warning("Remove offer failed:\n" + message).show();
                });
    }

    private void openPopupForMissingAccountSetup(String headLine, String message, Class target, String targetAsString) {
        new Popup().headLine(headLine)
                .instruction(message)
                .actionButtonText("Go to " + targetAsString)
                .onAction(() -> {
                    navigation.setReturnPath(navigation.getCurrentPath());
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, target);
                }).show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<OfferBookListItem, OfferBookListItem> getAmountColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("Amount in BTC (min - max)") {
            {
                setMinWidth(150);
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

    private TableColumn<OfferBookListItem, OfferBookListItem> getMarketColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("Market") {
            {
                setMinWidth(130);
                setMaxWidth(130);
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
                                    setText(formatter.getCurrencyPair(item.getOffer().getCurrencyCode()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getPriceColumn() {
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
                            private OfferBookListItem offerBookListItem;
                            ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offerBookListItem != null && offerBookListItem.getOffer().getPrice() != null) {
                                        setText(model.getPrice(offerBookListItem));
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (item.getOffer().getPrice() == null) {
                                        this.offerBookListItem = item;
                                        model.priceFeedService.currenciesUpdateFlagProperty().addListener(listener);
                                        setText("N/A");
                                    } else {
                                        setText(model.getPrice(item));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    this.offerBookListItem = null;
                                    setText("");
                                }
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
                            private OfferBookListItem offerBookListItem;
                            ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offerBookListItem != null && offerBookListItem.getOffer().getOfferVolume() != null) {
                                        setText(model.getVolume(offerBookListItem));
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (item.getOffer().getPrice() == null) {
                                        this.offerBookListItem = item;
                                        model.priceFeedService.currenciesUpdateFlagProperty().addListener(listener);
                                        setText("N/A");
                                    } else {
                                        setText(model.getVolume(item));
                                    }
                                } else {
                                    if (listener != null)
                                        model.priceFeedService.currenciesUpdateFlagProperty().removeListener(listener);
                                    this.offerBookListItem = null;
                                    setText("");
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getPaymentMethodColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("Payment method") {
            {
                setMinWidth(120);
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
                                    field.setOnAction(event -> offerDetailsWindow.show(item.getOffer()));
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
                            boolean isTradable, isPaymentAccountValidForOffer, hasMatchingArbitrator,
                                    hasSameProtocolVersion, isIgnored, isOfferBanned, isNodeBanned;

                            {
                                button.setGraphic(iconView);
                                button.setMinWidth(150);
                                button.setMaxWidth(150);
                                button.setGraphicTextGap(10);
                            }

                            @Override
                            public void updateItem(final OfferBookListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty) {
                                    final Offer offer = newItem.getOffer();
                                    boolean myOffer = model.isMyOffer(offer);
                                    TableRow tableRow = getTableRow();
                                    if (tableRow != null) {
                                        isPaymentAccountValidForOffer = model.isAnyPaymentAccountValidForOffer(offer);
                                        hasMatchingArbitrator = model.hasMatchingArbitrator(offer);
                                        hasSameProtocolVersion = model.hasSameProtocolVersion(offer);
                                        isIgnored = model.isIgnored(offer);
                                        isOfferBanned = model.isOfferBanned(offer);
                                        isNodeBanned = model.isNodeBanned(offer);
                                        isTradable = isPaymentAccountValidForOffer && hasMatchingArbitrator &&
                                                hasSameProtocolVersion &&
                                                !isIgnored &&
                                                !isOfferBanned &&
                                                !isNodeBanned;

                                        tableRow.setOpacity(isTradable || myOffer ? 1 : 0.4);

                                        if (isTradable) {
                                            // set first row button as default
                                            button.setDefaultButton(getIndex() == 0);
                                            tableRow.setOnMouseClicked(null);
                                        } else {
                                            button.setDefaultButton(false);
                                            tableRow.setOnMousePressed(e -> {
                                                // ugly hack to get the icon clickable when deactivated
                                                if (!(e.getTarget() instanceof ImageView || e.getTarget() instanceof Canvas))
                                                    onShowInfo(isPaymentAccountValidForOffer, hasMatchingArbitrator,
                                                            hasSameProtocolVersion, isIgnored, isOfferBanned, isNodeBanned);
                                            });

                                            //TODO
                                            //tableRow.setTooltip(new Tooltip(""));
                                        }
                                    }

                                    String title;
                                    if (myOffer) {
                                        iconView.setId("image-remove");
                                        title = "Remove";
                                        button.setId("cancel-button");
                                        button.setStyle("-fx-text-fill: #444;"); // does not take the font colors sometimes from the style
                                        button.setOnAction(e -> onRemoveOpenOffer(offer));
                                    } else {
                                        boolean isSellOffer = offer.getDirection() == Offer.Direction.SELL;
                                        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
                                        button.setId(isSellOffer ? "buy-button" : "sell-button");
                                        button.setStyle("-fx-text-fill: white;"); // does not take the font colors sometimes from the style
                                        title = model.getDirectionLabel(offer);
                                        button.setTooltip(new Tooltip("Take offer for " + model.getDirectionLabelTooltip(offer)));
                                        button.setOnAction(e -> onTakeOffer(offer));
                                    }

                                    if (!myOffer && !isTradable)
                                        button.setOnAction(e -> onShowInfo(isPaymentAccountValidForOffer,
                                                hasMatchingArbitrator, hasSameProtocolVersion,
                                                isIgnored, isOfferBanned, isNodeBanned));

                                    button.setText(title);
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
                                    if (button != null)
                                        button.setOnAction(null);
                                    TableRow tableRow = getTableRow();
                                    if (tableRow != null) {
                                        tableRow.setOpacity(1);
                                        tableRow.setOnMouseClicked(null);
                                    }
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getAvatarColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>("") {
            {
                setMinWidth(40);
                setMaxWidth(40);
                setSortable(true);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty) {
                                    String hostName = newItem.getOffer().getOwnerNodeAddress().hostName;
                                    int numPastTrades = model.getNumPastTrades(newItem.getOffer());
                                    boolean hasTraded = numPastTrades > 0;
                                    String tooltipText = hasTraded ? "Offerers onion address: " + hostName + "\n" +
                                            "You have already traded " + numPastTrades + " times with that offerer." : "Offerers onion address: " + hostName;
                                    Node identIcon = new PeerInfoIcon(hostName, tooltipText, numPastTrades, privateNotificationManager, newItem.getOffer());
                                    setPadding(new Insets(-2, 0, -2, 0));
                                    if (identIcon != null)
                                        setGraphic(identIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }
}

