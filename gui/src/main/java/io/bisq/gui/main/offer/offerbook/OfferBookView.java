/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.offer.offerbook;

import io.bisq.common.UserThread;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.core.alert.PrivateNotificationManager;
import io.bisq.core.offer.Offer;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.components.PeerInfoIcon;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.account.AccountView;
import io.bisq.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bisq.gui.main.account.content.fiataccounts.FiatAccountsView;
import io.bisq.gui.main.account.settings.AccountSettingsView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.withdrawal.WithdrawalView;
import io.bisq.gui.main.offer.OfferView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.OfferDetailsWindow;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
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
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class OfferBookView extends ActivatableViewAndModel<GridPane, OfferBookViewModel> {

    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private final BSFormatter formatter;
    private final PrivateNotificationManager privateNotificationManager;

    private ComboBox<TradeCurrency> currencyComboBox;
    private ComboBox<PaymentMethod> paymentMethodComboBox;
    private Button createOfferButton;
    private TableColumn<OfferBookListItem, OfferBookListItem> amountColumn, volumeColumn, marketColumn, priceColumn, paymentMethodColumn, avatarColumn;
    private TableView<OfferBookListItem> tableView;

    private OfferView.OfferActionHandler offerActionHandler;
    private int gridRow = 0;
    private Label nrOfOffersLabel;
    private ListChangeListener<OfferBookListItem> offerListListener;
    private MonadicBinding<Void> currencySelectionBinding;
    private Subscription currencySelectionSubscriber;


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

        addTitledGroupBg(root, gridRow, 3, Res.get("offerbook.availableOffers"));

        currencyComboBox = addLabelComboBox(root, gridRow, Res.get("offerbook.filterByCurrency"), Layout.FIRST_ROW_DISTANCE).second;
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setConverter(GUIUtil.getTradeCurrencyConverter());

        paymentMethodComboBox = addLabelComboBox(root, ++gridRow, Res.get("offerbook.filterByPaymentMethod")).second;
        paymentMethodComboBox.setPromptText(Res.get("shared.selectPaymentMethod"));
        paymentMethodComboBox.setVisibleRowCount(20);
        paymentMethodComboBox.setConverter(new StringConverter<PaymentMethod>() {
            @Override
            public String toString(PaymentMethod paymentMethod) {
                String id = paymentMethod.getId();
                if (id.equals(GUIUtil.SHOW_ALL_FLAG))
                    return "▶ " + Res.get("list.currency.showAll");
                else if (paymentMethod.equals(PaymentMethod.BLOCK_CHAINS))
                    return "✦ " + Res.get(id);
                else
                    return "★ " + Res.get(id);
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
        Label placeholder = new Label(Res.get("table.placeholder.noItems", Res.get("shared.offers")));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        priceColumn.setComparator((o1, o2) -> {
            Price price1 = o1.getOffer().getPrice();
            Price price2 = o2.getOffer().getPrice();
            return price1 != null && price2 != null ? price1.compareTo(price2) : 0;
        });
        amountColumn.setComparator((o1, o2) -> o1.getOffer().getAmount().compareTo(o2.getOffer().getAmount()));
        volumeColumn.setComparator((o1, o2) -> {
            Volume offerVolume1 = o1.getOffer().getVolume();
            Volume offerVolume2 = o2.getOffer().getVolume();
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
        offerListListener = c -> nrOfOffersLabel.setText(Res.get("offerbook.nrOffers", model.getOfferList().size()));
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem()));

        if (model.showAllTradeCurrenciesProperty.get())
            currencyComboBox.getSelectionModel().select(0);
        else
            currencyComboBox.getSelectionModel().select(model.getSelectedTradeCurrency());

        volumeColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());
        priceColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());
        model.getOfferList().comparatorProperty().bind(tableView.comparatorProperty());
        // We dont get it sorted without the delay at startup
        UserThread.execute(() -> priceColumn.sortTypeProperty().bind(model.priceSortTypeProperty));
        
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
                        volumeColumn.setText(Res.get("shared.amountMinMax"));
                        priceColumn.setText(Res.get("shared.price"));

                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(0, marketColumn);
                    } else {
                        volumeColumn.setText(Res.get("offerbook.volume", code));
                        priceColumn.setText(formatter.getPriceWithCurrencyCode(code));

                        if (tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().remove(marketColumn);
                    }

                    return null;
                });
        currencySelectionSubscriber = currencySelectionBinding.subscribe((observable, oldValue, newValue) -> {
        });

        tableView.setItems(model.getOfferList());

        model.getOfferList().addListener(offerListListener);
        nrOfOffersLabel.setText(Res.get("offerbook.nrOffers", model.getOfferList().size()));
    }

    @Override
    protected void deactivate() {
        currencyComboBox.setOnAction(null);
        paymentMethodComboBox.setOnAction(null);
        createOfferButton.setOnAction(null);
        model.getOfferList().comparatorProperty().unbind();

        priceColumn.sortableProperty().unbind();
        priceColumn.sortTypeProperty().unbind();
        amountColumn.sortableProperty().unbind();
        model.getOfferList().removeListener(offerListListener);

        currencySelectionSubscriber.unsubscribe();
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
            String directionText = direction == Offer.Direction.BUY ? Res.get("shared.buy") : Res.get("shared.sell");
            String mirroredDirectionText = direction == Offer.Direction.SELL ? Res.get("shared.buy") : Res.get("shared.sell");
            String code = selectedTradeCurrency.getCode();
            if (model.showAllTradeCurrenciesProperty.get())
                createOfferButton.setText(Res.get("offerbook.createOfferTo", directionText, "BTC"));
            else if (selectedTradeCurrency instanceof FiatCurrency)
                createOfferButton.setText(Res.get("offerbook.createOfferTo", directionText, "BTC") +
                        (direction == Offer.Direction.BUY ?
                                Res.get("offerbook.buyWithOtherCurrency", code) :
                                Res.get("offerbook.sellForOtherCurrency", code)));
            else
                createOfferButton.setText(Res.get("offerbook.createOfferTo", mirroredDirectionText, code) + " (" + directionText + " BTC)");
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
            openPopupForMissingAccountSetup(Res.get("popup.warning.noTradingAccountSetup.headline"),
                    Res.get("popup.warning.noTradingAccountSetup.msg"),
                    FiatAccountsView.class,
                    "navigation.account");
        } else if (!model.hasPaymentAccountForCurrency()) {
            new Popup().headLine(Res.get("offerbook.warning.noTradingAccountForCurrency.headline"))
                    .instruction(Res.get("offerbook.warning.noTradingAccountForCurrency.msg"))
                    .actionButtonText(Res.get("offerbook.yesCreateOffer"))
                    .onAction(() -> {
                        createOfferButton.setDisable(true);
                        offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
                    })
                    .closeButtonText(Res.get("offerbook.setupNewAccount"))
                    .onClose(() -> {
                        navigation.setReturnPath(navigation.getCurrentPath());
                        navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, FiatAccountsView.class);
                    })
                    .show();
        } else if (!model.hasAcceptedArbitrators()) {
            openPopupForMissingAccountSetup(Res.get("popup.warning.noArbitratorSelected.headline"),
                    Res.get("popup.warning.noArbitratorSelected.msg"),
                    ArbitratorSelectionView.class,
                    "navigation.arbitratorSelection");
        } else {
            createOfferButton.setDisable(true);
            offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
        }
    }

    private void onShowInfo(boolean isPaymentAccountValidForOffer, boolean hasMatchingArbitrator,
                            boolean hasSameProtocolVersion, boolean isIgnored,
                            boolean isOfferBanned, boolean isNodeBanned) {
        if (!hasMatchingArbitrator) {
            openPopupForMissingAccountSetup(Res.get("popup.warning.noArbitratorSelected.headline"),
                    Res.get("popup.warning.noArbitratorSelected.msg"),
                    ArbitratorSelectionView.class,
                    "navigation.arbitratorSelection");
        } else if (!isPaymentAccountValidForOffer) {
            openPopupForMissingAccountSetup(Res.get("offerbook.warning.noMatchingAccount.headline"),
                    Res.get("offerbook.warning.noMatchingAccount.msg"),
                    FiatAccountsView.class,
                    "navigation.account");
        } else if (!hasSameProtocolVersion) {
            new Popup().warning(Res.get("offerbook.warning.wrongTradeProtocol")).show();
        } else if (isIgnored) {
            new Popup().warning(Res.get("offerbook.warning.userIgnored")).show();
        } else if (isOfferBanned) {
            new Popup().warning(Res.get("offerbook.warning.offerBlocked")).show();
        } else if (isNodeBanned) {
            new Popup().warning(Res.get("offerbook.warning.nodeBlocked")).show();
        }
    }

    private void onTakeOffer(Offer offer) {
        if (model.isBootstrapped())
            offerActionHandler.onTakeOffer(offer);
        else
            new Popup().information(Res.get("popup.warning.notFullyConnected")).show();
    }

    private void onRemoveOpenOffer(Offer offer) {
        if (model.isBootstrapped()) {
            String key = "RemoveOfferWarning";
            if (model.preferences.showAgain(key))
                new Popup().warning(Res.get("popup.warning.removeOffer", model.formatter.formatCoinWithCode(offer.getCreateOfferFee())))
                        .actionButtonText(Res.get("shared.removeOffer"))
                        .onAction(() -> doRemoveOffer(offer))
                        .closeButtonText(Res.get("shared.dontRemoveOffer"))
                        .dontShowAgainId(key, model.preferences)
                        .show();
            else
                doRemoveOffer(offer);
        } else {
            new Popup().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void doRemoveOffer(Offer offer) {
        String key = "WithdrawFundsAfterRemoveOfferInfo";
        model.onRemoveOpenOffer(offer,
                () -> {
                    log.debug(Res.get("offerbook.removeOffer.success"));
                    if (model.preferences.showAgain(key))
                        new Popup().instruction(Res.get("offerbook.withdrawFundsHint", Res.get("navigation.funds.availableForWithdrawal")))
                                .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key, model.preferences)
                                .show();
                },
                (message) -> {
                    log.error(message);
                    new Popup().warning(Res.get("offerbook.removeOffer.failed", message)).show();
                });
    }

    private void openPopupForMissingAccountSetup(String headLine, String message, Class target, String targetAsString) {
        new Popup().headLine(headLine)
                .instruction(message)
                .actionButtonTextWithGoTo(targetAsString)
                .onAction(() -> {
                    navigation.setReturnPath(navigation.getCurrentPath());
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, target);
                }).show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<OfferBookListItem, OfferBookListItem> getAmountColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>(Res.get("shared.BTCMinMax")) {
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
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>(Res.get("shared.market")) {
            {
                setMinWidth(120);
                // setMaxWidth(130);
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
                setMinWidth(120);
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
                            final ChangeListener<Number> listener = new ChangeListener<Number>() {
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
                                        setText(Res.get("shared.na"));
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
                setMinWidth(125);
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
                            final ChangeListener<Number> listener = new ChangeListener<Number>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                                    if (offerBookListItem != null && offerBookListItem.getOffer().getVolume() != null) {
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
                                        setText(Res.get("shared.na"));
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
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>(Res.get("shared.paymentMethod")) {
            {
                setMinWidth(125);
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
        TableColumn<OfferBookListItem, OfferBookListItem> column = new TableColumn<OfferBookListItem, OfferBookListItem>(Res.get("offerbook.wantTo")) {
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
                                button.setMinWidth(130);
                                button.setMaxWidth(130);
                                button.setGraphicTextGap(10);
                            }

                            @Override
                            public void updateItem(final OfferBookListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                TableRow tableRow = getTableRow();
                                if (newItem != null && !empty) {
                                    final Offer offer = newItem.getOffer();
                                    boolean myOffer = model.isMyOffer(offer);
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
                                            tableRow.setOnMousePressed(null);
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
                                        title = Res.get("shared.remove");
                                        button.setId("cancel-button");
                                        button.setStyle("-fx-text-fill: #444;"); // does not take the font colors sometimes from the style
                                        button.setOnAction(e -> onRemoveOpenOffer(offer));
                                    } else {
                                        boolean isSellOffer = offer.getDirection() == Offer.Direction.SELL;
                                        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
                                        button.setId(isSellOffer ? "buy-button" : "sell-button");
                                        button.setStyle("-fx-text-fill: white;"); // does not take the font colors sometimes from the style
                                        title = model.getDirectionLabel(offer);
                                        button.setTooltip(new Tooltip(Res.get("offerbook.takeOfferButton.tooltip", model.getDirectionLabelTooltip(offer))));
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
                                    if (tableRow != null) {
                                        tableRow.setOpacity(1);
                                        tableRow.setOnMousePressed(null);
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
                                    String tooltipText = hasTraded ?
                                            Res.get("peerInfoIcon.tooltip.offer.traded", hostName, numPastTrades) :
                                            Res.get("peerInfoIcon.tooltip.offer.notTraded", hostName);
                                    Node peerInfoIcon = new PeerInfoIcon(hostName, tooltipText, numPastTrades, privateNotificationManager, newItem.getOffer());
                                    setPadding(new Insets(-2, 0, -2, 0));
                                    setGraphic(peerInfoIcon);
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

