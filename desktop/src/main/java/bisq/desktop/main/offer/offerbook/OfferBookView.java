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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InfoAutoTooltipLabel;
import bisq.desktop.components.PeerInfoIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.offer.OfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.ListChangeListener;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class OfferBookView extends ActivatableViewAndModel<GridPane, OfferBookViewModel> {

    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private final CoinFormatter formatter;
    private final PrivateNotificationManager privateNotificationManager;
    private final boolean useDevPrivilegeKeys;
    private final AccountAgeWitnessService accountAgeWitnessService;

    private AutocompleteComboBox<TradeCurrency> currencyComboBox;
    private AutocompleteComboBox<PaymentMethod> paymentMethodComboBox;
    private AutoTooltipButton createOfferButton;
    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> amountColumn, volumeColumn, marketColumn,
            priceColumn, paymentMethodColumn, signingStateColumn, avatarColumn;
    private TableView<OfferBookListItem> tableView;

    private OfferView.OfferActionHandler offerActionHandler;
    private int gridRow = 0;
    private Label nrOfOffersLabel;
    private ListChangeListener<OfferBookListItem> offerListListener;
    private ChangeListener<Number> priceFeedUpdateCounterListener;
    private Subscription currencySelectionSubscriber;
    private static final int SHOW_ALL = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBookView(OfferBookViewModel model,
                  Navigation navigation,
                  OfferDetailsWindow offerDetailsWindow,
                  @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                  PrivateNotificationManager privateNotificationManager,
                  @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys,
                  AccountAgeWitnessService accountAgeWitnessService) {
        super(model);

        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
        this.formatter = formatter;
        this.privateNotificationManager = privateNotificationManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    @Override
    public void initialize() {
        root.setPadding(new Insets(15, 15, 5, 15));

        final TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 2, Res.get("offerbook.availableOffers"));
        titledGroupBg.getStyleClass().add("last");

        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setSpacing(35);
        hBox.setPadding(new Insets(10, 0, 0, 0));

        final Tuple3<VBox, Label, AutocompleteComboBox<TradeCurrency>> currencyBoxTuple = FormBuilder.addTopLabelAutocompleteComboBox(
                Res.get("offerbook.filterByCurrency"));
        final Tuple3<VBox, Label, AutocompleteComboBox<PaymentMethod>> paymentBoxTuple = FormBuilder.addTopLabelAutocompleteComboBox(
                Res.get("offerbook.filterByPaymentMethod"));

        createOfferButton = new AutoTooltipButton();
        createOfferButton.setMinHeight(40);
        createOfferButton.setGraphicTextGap(10);
        AnchorPane.setRightAnchor(createOfferButton, 0d);
        AnchorPane.setBottomAnchor(createOfferButton, 0d);

        hBox.getChildren().addAll(currencyBoxTuple.first, paymentBoxTuple.first, createOfferButton);
        AnchorPane.setLeftAnchor(hBox, 0d);
        AnchorPane.setTopAnchor(hBox, 0d);
        AnchorPane.setBottomAnchor(hBox, 0d);

        AnchorPane anchorPane = new AnchorPane();
        anchorPane.getChildren().addAll(hBox, createOfferButton);

        GridPane.setHgrow(anchorPane, Priority.ALWAYS);
        GridPane.setRowIndex(anchorPane, gridRow);
        GridPane.setColumnSpan(anchorPane, 2);
        GridPane.setMargin(anchorPane, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        root.getChildren().add(anchorPane);

        currencyComboBox = currencyBoxTuple.third;

        paymentMethodComboBox = paymentBoxTuple.third;
        paymentMethodComboBox.setCellFactory(GUIUtil.getPaymentMethodCellFactory());

        tableView = new TableView<>();

        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setColumnIndex(tableView, 0);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(10, 0, -10, 0));
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
        signingStateColumn = getSigningStateColumn();
        tableView.getColumns().add(signingStateColumn);
        avatarColumn = getAvatarColumn();
        tableView.getColumns().add(getActionColumn());
        tableView.getColumns().add(avatarColumn);

        tableView.getSortOrder().add(priceColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.multipleOffers")));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        marketColumn.setComparator(Comparator.comparing(
                o -> CurrencyUtil.getCurrencyPair(o.getOffer().getCurrencyCode()),
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));
        priceColumn.setComparator(Comparator.comparing(o -> o.getOffer().getPrice(), Comparator.nullsFirst(Comparator.naturalOrder())));
        amountColumn.setComparator(Comparator.comparing(o -> o.getOffer().getAmount()));
        volumeColumn.setComparator(Comparator.comparing(o -> o.getOffer().getVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
        paymentMethodColumn.setComparator(Comparator.comparing(o -> o.getOffer().getPaymentMethod()));
        avatarColumn.setComparator(Comparator.comparing(o -> o.getOffer().getOwnerNodeAddress().getFullAddress()));

        nrOfOffersLabel = new AutoTooltipLabel("");
        nrOfOffersLabel.setId("num-offers");
        GridPane.setHalignment(nrOfOffersLabel, HPos.LEFT);
        GridPane.setVgrow(nrOfOffersLabel, Priority.NEVER);
        GridPane.setValignment(nrOfOffersLabel, VPos.TOP);
        GridPane.setRowIndex(nrOfOffersLabel, ++gridRow);
        GridPane.setColumnIndex(nrOfOffersLabel, 0);
        GridPane.setMargin(nrOfOffersLabel, new Insets(10, 0, 0, 0));
        root.getChildren().add(nrOfOffersLabel);

        offerListListener = c -> nrOfOffersLabel.setText(Res.get("offerbook.nrOffers", model.getOfferList().size()));

        // Fixes incorrect ordering of Available offers:
        // https://github.com/bisq-network/bisq-desktop/issues/588
        priceFeedUpdateCounterListener = (observable, oldValue, newValue) -> tableView.sort();
    }

    @Override
    protected void activate() {
        currencyComboBox.setCellFactory(GUIUtil.getTradeCurrencyCellFactory(Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"),
                (model.getDirection() == OfferPayload.Direction.BUY ? model.getSellOfferCounts() : model.getBuyOfferCounts())));

        currencyComboBox.setConverter(new CurrencyStringConverter(currencyComboBox));
        currencyComboBox.getEditor().getStyleClass().add("combo-box-editor-bold");

        currencyComboBox.setAutocompleteItems(model.getTradeCurrencies());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 10));

        currencyComboBox.setOnChangeConfirmed(e -> {
            if (currencyComboBox.getEditor().getText().isEmpty())
                currencyComboBox.getSelectionModel().select(SHOW_ALL);
            model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
        });

        if (model.showAllTradeCurrenciesProperty.get())
            currencyComboBox.getSelectionModel().select(SHOW_ALL);
        else
            currencyComboBox.getSelectionModel().select(model.getSelectedTradeCurrency());
        currencyComboBox.getEditor().setText(new CurrencyStringConverter(currencyComboBox).toString(currencyComboBox.getSelectionModel().getSelectedItem()));

        volumeColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());
        priceColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());
        model.getOfferList().comparatorProperty().bind(tableView.comparatorProperty());
        model.priceSortTypeProperty.addListener((observable, oldValue, newValue) -> priceColumn.setSortType(newValue));
        priceColumn.setSortType(model.priceSortTypeProperty.get());

        paymentMethodComboBox.setConverter(new PaymentMethodStringConverter(paymentMethodComboBox));
        paymentMethodComboBox.getEditor().getStyleClass().add("combo-box-editor-bold");

        paymentMethodComboBox.setAutocompleteItems(model.getPaymentMethods());
        paymentMethodComboBox.setVisibleRowCount(Math.min(paymentMethodComboBox.getItems().size(), 10));

        paymentMethodComboBox.setOnChangeConfirmed(e -> {
            if (paymentMethodComboBox.getEditor().getText().isEmpty())
                paymentMethodComboBox.getSelectionModel().select(SHOW_ALL);
            model.onSetPaymentMethod(paymentMethodComboBox.getSelectionModel().getSelectedItem());
            updateSigningStateColumn();
        });

        if (model.showAllPaymentMethods)
            paymentMethodComboBox.getSelectionModel().select(SHOW_ALL);
        else
            paymentMethodComboBox.getSelectionModel().select(model.selectedPaymentMethod);
        paymentMethodComboBox.getEditor().setText(new PaymentMethodStringConverter(paymentMethodComboBox).toString(paymentMethodComboBox.getSelectionModel().getSelectedItem()));

        createOfferButton.setOnAction(e -> onCreateOffer());

        MonadicBinding<Void> currencySelectionBinding = EasyBind.combine(
                model.showAllTradeCurrenciesProperty, model.tradeCurrencyCode,
                (showAll, code) -> {
                    setDirectionTitles();
                    if (showAll) {
                        volumeColumn.setTitleWithHelpText(Res.get("shared.amountMinMax"), Res.get("shared.amountHelp"));
                        priceColumn.setTitle(Res.get("shared.price"));
                        priceColumn.getStyleClass().remove("first-column");

                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(0, marketColumn);
                    } else {
                        volumeColumn.setTitleWithHelpText(Res.get("offerbook.volume", code), Res.get("shared.amountHelp"));
                        priceColumn.setTitle(CurrencyUtil.getPriceWithCurrencyCode(code));
                        priceColumn.getStyleClass().add("first-column");

                        tableView.getColumns().remove(marketColumn);
                    }

                    updateSigningStateColumn();

                    return null;
                });

        currencySelectionSubscriber = currencySelectionBinding.subscribe((observable, oldValue, newValue) -> {
        });

        tableView.setItems(model.getOfferList());

        model.getOfferList().addListener(offerListListener);
        nrOfOffersLabel.setText(Res.get("offerbook.nrOffers", model.getOfferList().size()));

        model.priceFeedService.updateCounterProperty().addListener(priceFeedUpdateCounterListener);
    }

    private void updateSigningStateColumn() {
        if (model.hasSelectionAccountSigning()) {
            if (!tableView.getColumns().contains(signingStateColumn)) {
                tableView.getColumns().add(tableView.getColumns().indexOf(paymentMethodColumn) + 1, signingStateColumn);
            }
        } else {
            tableView.getColumns().remove(signingStateColumn);
        }
    }

    @Override
    protected void deactivate() {
        createOfferButton.setOnAction(null);
        model.getOfferList().comparatorProperty().unbind();

        volumeColumn.sortableProperty().unbind();
        priceColumn.sortableProperty().unbind();
        amountColumn.sortableProperty().unbind();
        model.getOfferList().comparatorProperty().unbind();

        model.getOfferList().removeListener(offerListListener);
        model.priceFeedService.updateCounterProperty().removeListener(priceFeedUpdateCounterListener);

        currencySelectionSubscriber.unsubscribe();
    }

    static class CurrencyStringConverter extends StringConverter<TradeCurrency> {
        private ComboBox<TradeCurrency> comboBox;

        CurrencyStringConverter(ComboBox<TradeCurrency> comboBox) {
            this.comboBox = comboBox;
        }

        @Override
        public String toString(TradeCurrency item) {
            return item != null ? asString(item) : "";
        }

        @Override
        public TradeCurrency fromString(String query) {
            if (comboBox.getItems().isEmpty())
                return null;
            if (query.isEmpty())
                return specialShowAllItem();
            return comboBox.getItems().stream().
                    filter(item -> asString(item).equals(query)).
                    findAny().orElse(null);
        }

        private String asString(TradeCurrency item) {
            if (isSpecialShowAllItem(item))
                return Res.get(GUIUtil.SHOW_ALL_FLAG);
            if (isSpecialEditItem(item))
                return Res.get(GUIUtil.EDIT_FLAG);
            return item.getCode() + "  -  " + item.getName();
        }

        private boolean isSpecialShowAllItem(TradeCurrency item) {
            return item.getCode().equals(GUIUtil.SHOW_ALL_FLAG);
        }

        private boolean isSpecialEditItem(TradeCurrency item) {
            return item.getCode().equals(GUIUtil.EDIT_FLAG);
        }

        private TradeCurrency specialShowAllItem() {
            return comboBox.getItems().get(SHOW_ALL);
        }
    }

    static class PaymentMethodStringConverter extends StringConverter<PaymentMethod> {
        private ComboBox<PaymentMethod> comboBox;

        PaymentMethodStringConverter(ComboBox<PaymentMethod> comboBox) {
            this.comboBox = comboBox;
        }

        @Override
        public String toString(PaymentMethod item) {
            return item != null ? asString(item) : "";
        }

        @Override
        public PaymentMethod fromString(String query) {
            if (comboBox.getItems().isEmpty())
                return null;
            if (query.isEmpty())
                return specialShowAllItem();
            return comboBox.getItems().stream().
                    filter(item -> asString(item).equals(query)).
                    findAny().orElse(null);
        }

        private String asString(PaymentMethod item) {
            if (isSpecialShowAllItem(item))
                return Res.get(GUIUtil.SHOW_ALL_FLAG);
            return Res.get(item.getId());
        }

        private boolean isSpecialShowAllItem(PaymentMethod item) {
            return item.getId().equals(GUIUtil.SHOW_ALL_FLAG);
        }

        private PaymentMethod specialShowAllItem() {
            return comboBox.getItems().get(SHOW_ALL);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void enableCreateOfferButton() {
        createOfferButton.setDisable(false);
    }

    public void setDirection(OfferPayload.Direction direction) {
        model.initWithDirection(direction);
        ImageView iconView = new ImageView();

        createOfferButton.setGraphic(iconView);
        iconView.setId(direction == OfferPayload.Direction.SELL ? "image-sell-white" : "image-buy-white");
        createOfferButton.setId(direction == OfferPayload.Direction.SELL ? "sell-button-big" : "buy-button-big");
        avatarColumn.setTitle(direction == OfferPayload.Direction.SELL ? Res.get("shared.buyerUpperCase") : Res.get("shared.sellerUpperCase"));
        setDirectionTitles();
    }

    private void setDirectionTitles() {
        TradeCurrency selectedTradeCurrency = model.getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            OfferPayload.Direction direction = model.getDirection();
            String offerButtonText;
            String code = selectedTradeCurrency.getCode();

            if (model.showAllTradeCurrenciesProperty.get()) {
                offerButtonText = direction == OfferPayload.Direction.BUY ?
                        Res.get("offerbook.createOfferToBuy",
                                Res.getBaseCurrencyCode()) :
                        Res.get("offerbook.createOfferToSell",
                                Res.getBaseCurrencyCode());
            } else if (selectedTradeCurrency instanceof FiatCurrency) {
                offerButtonText = direction == OfferPayload.Direction.BUY ?
                        Res.get("offerbook.createOfferToBuy.withFiat",
                                Res.getBaseCurrencyCode(), code) :
                        Res.get("offerbook.createOfferToSell.forFiat", Res.getBaseCurrencyCode(), code);

            } else {
                offerButtonText = direction == OfferPayload.Direction.BUY ?
                        Res.get("offerbook.createOfferToBuy.withCrypto",
                                code, Res.getBaseCurrencyCode()) :
                        Res.get("offerbook.createOfferToSell.forCrypto", code, Res.getBaseCurrencyCode());
            }
            createOfferButton.updateText(offerButtonText);
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
        if (model.canCreateOrTakeOffer()) {
            if (!model.hasPaymentAccountForCurrency()) {
                new Popup().headLine(Res.get("offerbook.warning.noTradingAccountForCurrency.headline"))
                        .instruction(Res.get("offerbook.warning.noTradingAccountForCurrency.msg"))
                        .actionButtonText(Res.get("offerbook.yesCreateOffer"))
                        .onAction(() -> {
                            createOfferButton.setDisable(true);
                            offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
                        })
                        .secondaryActionButtonText(Res.get("offerbook.setupNewAccount"))
                        .onSecondaryAction(() -> {
                            navigation.setReturnPath(navigation.getCurrentPath());
                            navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                        })
                        .width(725)
                        .show();
                return;
            }

            createOfferButton.setDisable(true);
            offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
        }
    }

    private void onShowInfo(Offer offer,
                            boolean isPaymentAccountValidForOffer,
                            boolean isInsufficientCounterpartyTradeLimit,
                            boolean hasSameProtocolVersion,
                            boolean isIgnored,
                            boolean isOfferBanned,
                            boolean isCurrencyBanned,
                            boolean isPaymentMethodBanned,
                            boolean isNodeAddressBanned,
                            boolean requireUpdateToNewVersion,
                            boolean isInsufficientTradeLimit) {
        if (!isPaymentAccountValidForOffer) {
            openPopupForMissingAccountSetup(Res.get("offerbook.warning.noMatchingAccount.headline"),
                    Res.get("offerbook.warning.noMatchingAccount.msg"),
                    FiatAccountsView.class,
                    "navigation.account");
        } else if (isInsufficientCounterpartyTradeLimit) {
            new Popup().warning(Res.get("offerbook.warning.counterpartyTradeRestrictions")).show();
        } else if (!hasSameProtocolVersion) {
            new Popup().warning(Res.get("offerbook.warning.wrongTradeProtocol")).show();
        } else if (isIgnored) {
            new Popup().warning(Res.get("offerbook.warning.userIgnored")).show();
        } else if (isOfferBanned) {
            new Popup().warning(Res.get("offerbook.warning.offerBlocked")).show();
        } else if (isCurrencyBanned) {
            new Popup().warning(Res.get("offerbook.warning.currencyBanned")).show();
        } else if (isPaymentMethodBanned) {
            new Popup().warning(Res.get("offerbook.warning.paymentMethodBanned")).show();
        } else if (isNodeAddressBanned) {
            new Popup().warning(Res.get("offerbook.warning.nodeBlocked")).show();
        } else if (requireUpdateToNewVersion) {
            new Popup().warning(Res.get("offerbook.warning.requireUpdateToNewVersion")).show();
        } else if (isInsufficientTradeLimit) {
            final Optional<PaymentAccount> account = model.getMostMaturePaymentAccountForOffer(offer);
            if (account.isPresent()) {
                final long tradeLimit = model.accountAgeWitnessService.getMyTradeLimit(account.get(),
                        offer.getCurrencyCode(), offer.getMirroredDirection());
                new Popup()
                        .warning(Res.get("offerbook.warning.tradeLimitNotMatching",
                                DisplayUtils.formatAccountAge(model.accountAgeWitnessService.getMyAccountAge(account.get().getPaymentAccountPayload())),
                                formatter.formatCoinWithCode(Coin.valueOf(tradeLimit)),
                                formatter.formatCoinWithCode(offer.getMinAmount())))
                        .show();
            } else {
                log.warn("We don't found a payment account but got called the isInsufficientTradeLimit case. That must not happen.");
            }
        }
    }

    private void onTakeOffer(Offer offer) {
        if (model.canCreateOrTakeOffer()) {
            if (offer.getDirection() == OfferPayload.Direction.SELL &&
                    offer.getPaymentMethod().getId().equals(PaymentMethod.CASH_DEPOSIT.getId())) {
                new Popup().confirmation(Res.get("popup.info.cashDepositInfo", offer.getBankId()))
                        .actionButtonText(Res.get("popup.info.cashDepositInfo.confirm"))
                        .onAction(() -> offerActionHandler.onTakeOffer(offer))
                        .show();
            } else {
                offerActionHandler.onTakeOffer(offer);
            }
        }
    }

    private void onRemoveOpenOffer(Offer offer) {
        if (model.isBootstrappedOrShowPopup()) {
            String key = "RemoveOfferWarning";
            if (DontShowAgainLookup.showAgain(key)) {
                new Popup().warning(Res.get("popup.warning.removeOffer", model.getMakerFeeAsString(offer)))
                        .actionButtonText(Res.get("shared.removeOffer"))
                        .onAction(() -> doRemoveOffer(offer))
                        .closeButtonText(Res.get("shared.dontRemoveOffer"))
                        .dontShowAgainId(key)
                        .show();
            } else {
                doRemoveOffer(offer);
            }
        }
    }

    private void doRemoveOffer(Offer offer) {
        String key = "WithdrawFundsAfterRemoveOfferInfo";
        model.onRemoveOpenOffer(offer,
                () -> {
                    log.debug(Res.get("offerbook.removeOffer.success"));
                    if (DontShowAgainLookup.showAgain(key))
                        new Popup().instruction(Res.get("offerbook.withdrawFundsHint", Res.get("navigation.funds.availableForWithdrawal")))
                                .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key)
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
                    navigation.navigateTo(MainView.class, AccountView.class, target);
                }).show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getAmountColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.BTCMinMax"), Res.get("shared.amountHelp"));
        column.setMinWidth(100);
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(model.getAmount(item), GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getMarketColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.market")) {
            {
                setMinWidth(40);
            }
        };
        column.getStyleClass().addAll("number-column", "first-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty)
                                    setText(CurrencyUtil.getCurrencyPair(item.getOffer().getCurrencyCode()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getPriceColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>("") {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            private OfferBookListItem offerBookListItem;
                            private ChangeListener<Number> priceChangedListener;
                            ChangeListener<Scene> sceneChangeListener;

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (getTableView().getScene() != null && sceneChangeListener == null) {
                                        sceneChangeListener = (observable, oldValue, newValue) -> {
                                            if (newValue == null) {
                                                if (priceChangedListener != null) {
                                                    model.priceFeedService.updateCounterProperty().removeListener(priceChangedListener);
                                                    priceChangedListener = null;
                                                }
                                                offerBookListItem = null;
                                                setGraphic(null);
                                                getTableView().sceneProperty().removeListener(sceneChangeListener);
                                                sceneChangeListener = null;
                                            }
                                        };
                                        getTableView().sceneProperty().addListener(sceneChangeListener);
                                    }

                                    this.offerBookListItem = item;

                                    if (priceChangedListener == null) {
                                        priceChangedListener = (observable, oldValue, newValue) -> {
                                            if (offerBookListItem != null && offerBookListItem.getOffer().getPrice() != null) {
                                                setGraphic(getPriceLabel(model.getPrice(offerBookListItem), offerBookListItem));
                                            }
                                        };
                                        model.priceFeedService.updateCounterProperty().addListener(priceChangedListener);
                                    }
                                    setGraphic(getPriceLabel(item.getOffer().getPrice() == null ? Res.get("shared.na") : model.getPrice(item), item));
                                } else {
                                    if (priceChangedListener != null) {
                                        model.priceFeedService.updateCounterProperty().removeListener(priceChangedListener);
                                        priceChangedListener = null;
                                    }
                                    if (sceneChangeListener != null) {
                                        getTableView().sceneProperty().removeListener(sceneChangeListener);
                                        sceneChangeListener = null;
                                    }
                                    this.offerBookListItem = null;
                                    setGraphic(null);
                                }
                            }

                            @NotNull
                            private AutoTooltipLabel getPriceLabel(String priceString, OfferBookListItem item) {
                                final Offer offer = item.getOffer();
                                final MaterialDesignIcon icon = offer.isUseMarketBasedPrice() ? MaterialDesignIcon.CHART_LINE : MaterialDesignIcon.LOCK;

                                String info;

                                if (offer.isUseMarketBasedPrice()) {
                                    if (offer.getMarketPriceMargin() == 0) {
                                        if (offer.isBuyOffer()) {
                                            info = Res.get("offerbook.info.sellAtMarketPrice");
                                        } else {
                                            info = Res.get("offerbook.info.buyAtMarketPrice");
                                        }
                                    } else if (offer.getMarketPriceMargin() > 0) {
                                        if (offer.isBuyOffer()) {
                                            info = Res.get("offerbook.info.sellBelowMarketPrice", model.getAbsolutePriceMargin(offer));
                                        } else {
                                            info = Res.get("offerbook.info.buyAboveMarketPrice", model.getAbsolutePriceMargin(offer));
                                        }
                                    } else {
                                        if (offer.isBuyOffer()) {
                                            info = Res.get("offerbook.info.sellAboveMarketPrice", model.getAbsolutePriceMargin(offer));
                                        } else {
                                            info = Res.get("offerbook.info.buyBelowMarketPrice", model.getAbsolutePriceMargin(offer));
                                        }
                                    }
                                } else {
                                    if (offer.isBuyOffer()) {
                                        info = Res.get("offerbook.info.sellAtFixedPrice");
                                    } else {
                                        info = Res.get("offerbook.info.buyAtFixedPrice");
                                    }
                                }

                                return new InfoAutoTooltipLabel(priceString, icon, ContentDisplay.RIGHT, info);
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getVolumeColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>("") {
            {
                setMinWidth(125);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            private OfferBookListItem offerBookListItem;
                            final ChangeListener<Number> listener = new ChangeListener<>() {
                                @Override
                                public void changed(ObservableValue<? extends Number> observable,
                                                    Number oldValue,
                                                    Number newValue) {
                                    if (offerBookListItem != null && offerBookListItem.getOffer().getVolume() != null) {
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getVolume(offerBookListItem),
                                                model.getNumberOfDecimalsForVolume(offerBookListItem)));
                                        model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    }
                                }
                            };

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (item.getOffer().getPrice() == null) {
                                        this.offerBookListItem = item;
                                        model.priceFeedService.updateCounterProperty().addListener(listener);
                                        setText(Res.get("shared.na"));
                                        setGraphic(null);
                                    } else {
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getVolume(item),
                                                model.getNumberOfDecimalsForVolume(item)));
                                    }
                                } else {
                                    model.priceFeedService.updateCounterProperty().removeListener(listener);
                                    this.offerBookListItem = null;
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getPaymentMethodColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.paymentMethod")) {
            {
                setMinWidth(80);
            }
        };

        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {

                                    if (model.isOfferBanned(item.getOffer())) {
                                        setGraphic(new AutoTooltipLabel(model.getPaymentMethod(item)));
                                    } else {
                                        field = new HyperlinkWithIcon(model.getPaymentMethod(item));
                                        field.setOnAction(event -> offerDetailsWindow.show(item.getOffer()));
                                        field.setTooltip(new Tooltip(model.getPaymentMethodToolTip(item)));
                                        setGraphic(field);
                                    }
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
        TableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.actions")) {
            {
                setMinWidth(200);
                setSortable(false);
            }
        };
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            final ImageView iconView = new ImageView();
                            final AutoTooltipButton button = new AutoTooltipButton();
                            boolean isTradable, isPaymentAccountValidForOffer,
                                    isInsufficientCounterpartyTradeLimit,
                                    hasSameProtocolVersion, isIgnored, isOfferBanned, isCurrencyBanned,
                                    isPaymentMethodBanned, isNodeAddressBanned, isMyInsufficientTradeLimit,
                                    requireUpdateToNewVersion;

                            {
                                button.setGraphic(iconView);
                                button.setMinWidth(200);
                                button.setMaxWidth(200);
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
                                        isInsufficientCounterpartyTradeLimit = model.isInsufficientCounterpartyTradeLimit(offer);
                                        hasSameProtocolVersion = model.hasSameProtocolVersion(offer);
                                        isIgnored = model.isIgnored(offer);
                                        isOfferBanned = model.isOfferBanned(offer);
                                        isCurrencyBanned = model.isCurrencyBanned(offer);
                                        isPaymentMethodBanned = model.isPaymentMethodBanned(offer);
                                        isNodeAddressBanned = model.isNodeAddressBanned(offer);
                                        requireUpdateToNewVersion = model.requireUpdateToNewVersion();
                                        isMyInsufficientTradeLimit = model.isMyInsufficientTradeLimit(offer);
                                        isTradable = isPaymentAccountValidForOffer &&
                                                !isInsufficientCounterpartyTradeLimit &&
                                                hasSameProtocolVersion &&
                                                !isIgnored &&
                                                !isOfferBanned &&
                                                !isCurrencyBanned &&
                                                !isPaymentMethodBanned &&
                                                !isNodeAddressBanned &&
                                                !requireUpdateToNewVersion &&
                                                !isMyInsufficientTradeLimit;

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
                                                    onShowInfo(offer,
                                                            isPaymentAccountValidForOffer,
                                                            isInsufficientCounterpartyTradeLimit,
                                                            hasSameProtocolVersion,
                                                            isIgnored,
                                                            isOfferBanned,
                                                            isCurrencyBanned,
                                                            isPaymentMethodBanned,
                                                            isNodeAddressBanned,
                                                            requireUpdateToNewVersion,
                                                            isMyInsufficientTradeLimit);
                                            });
                                        }
                                    }

                                    String title;
                                    if (myOffer) {
                                        iconView.setId("image-remove");
                                        title = Res.get("shared.remove");
                                        button.setId("cancel-button");
                                        button.setOnAction(e -> onRemoveOpenOffer(offer));
                                    } else {
                                        boolean isSellOffer = offer.getDirection() == OfferPayload.Direction.SELL;
                                        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
                                        button.setId(isSellOffer ? "buy-button" : "sell-button");
                                        if (isSellOffer) {
                                            title = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) ?
                                                    Res.get("offerbook.takeOfferToBuy", offer.getOfferPayload().getBaseCurrencyCode()) :
                                                    Res.get("offerbook.takeOfferToSell", offer.getCurrencyCode());
                                        } else {
                                            title = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) ?
                                                    Res.get("offerbook.takeOfferToSell", offer.getOfferPayload().getBaseCurrencyCode()) :
                                                    Res.get("offerbook.takeOfferToBuy", offer.getCurrencyCode());
                                        }
                                        button.setTooltip(new Tooltip(Res.get("offerbook.takeOfferButton.tooltip", model.getDirectionLabelTooltip(offer))));
                                        button.setOnAction(e -> onTakeOffer(offer));
                                    }

                                    if (!myOffer && !isTradable)
                                        button.setOnAction(e -> onShowInfo(offer,
                                                isPaymentAccountValidForOffer,
                                                isInsufficientCounterpartyTradeLimit,
                                                hasSameProtocolVersion,
                                                isIgnored,
                                                isOfferBanned,
                                                isCurrencyBanned,
                                                isPaymentMethodBanned,
                                                isNodeAddressBanned,
                                                requireUpdateToNewVersion,
                                                isMyInsufficientTradeLimit));

                                    button.updateText(title);
                                    setPadding(new Insets(0, 15, 0, 0));
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
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

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getSigningStateColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(
                Res.get("offerbook.timeSinceSigning"),
                Res.get("offerbook.timeSinceSigning.help",
                        SignedWitnessService.SIGNER_AGE_DAYS,
                        formatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT))) {
            {
                setMinWidth(60);
                setSortable(true);
            }
        };

        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                return new TableCell<>() {
                    private HyperlinkWithIcon field;

                    @Override
                    public void updateItem(final OfferBookListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {

                            GlyphIcons icon;
                            String info;
                            String timeSinceSigning;

                            if (accountAgeWitnessService.hasSignedWitness(item.getOffer())) {
                                AccountAgeWitnessService.SignState signState = accountAgeWitnessService.getSignState(item.getOffer());
                                icon = GUIUtil.getIconForSignState(signState);
                                info = Res.get("offerbook.timeSinceSigning.info",
                                        signState.getPresentation());
                                long daysSinceSigning = TimeUnit.MILLISECONDS.toDays(
                                        accountAgeWitnessService.getWitnessSignAge(item.getOffer(), new Date()));
                                timeSinceSigning = Res.get("offerbook.timeSinceSigning.daysSinceSigning",
                                        daysSinceSigning);
                            } else {
                                boolean needsSigning = PaymentMethod.hasChargebackRisk(
                                        item.getOffer().getPaymentMethod(), item.getOffer().getCurrencyCode());
                                if (needsSigning) {
                                    AccountAgeWitnessService.SignState signState = accountAgeWitnessService.getSignState(item.getOffer());

                                    icon = GUIUtil.getIconForSignState(signState);

                                    if (!signState.equals(AccountAgeWitnessService.SignState.UNSIGNED)) {
                                        info = Res.get("offerbook.timeSinceSigning.info", signState.getPresentation());
                                        long daysSinceSigning = TimeUnit.MILLISECONDS.toDays(
                                                accountAgeWitnessService.getWitnessSignAge(item.getOffer(), new Date()));
                                        timeSinceSigning = Res.get("offerbook.timeSinceSigning.daysSinceSigning",
                                                daysSinceSigning);
                                    } else {
                                        info = Res.get("shared.notSigned");
                                        timeSinceSigning = Res.get("offerbook.timeSinceSigning.notSigned");
                                    }
                                } else {
                                    icon = MaterialDesignIcon.INFORMATION_OUTLINE;
                                    info = Res.get("shared.notSigned.noNeed");
                                    timeSinceSigning = Res.get("offerbook.timeSinceSigning.notSigned.noNeed");
                                }
                            }

                            InfoAutoTooltipLabel label = new InfoAutoTooltipLabel(timeSinceSigning, icon, ContentDisplay.RIGHT, info);
                            setGraphic(label);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getAvatarColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("offerbook.trader")) {
            {
                setMinWidth(80);
                setMaxWidth(80);
                setSortable(true);
            }
        };
        column.getStyleClass().addAll("last-column", "avatar-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (newItem != null && !empty) {
                                    final Offer offer = newItem.getOffer();
                                    final NodeAddress makersNodeAddress = offer.getOwnerNodeAddress();
                                    String role = Res.get("peerInfoIcon.tooltip.maker");
                                    int numTrades = model.getNumTrades(offer);
                                    PeerInfoIcon peerInfoIcon = new PeerInfoIcon(makersNodeAddress,
                                            role,
                                            numTrades,
                                            privateNotificationManager,
                                            offer,
                                            model.preferences,
                                            model.accountAgeWitnessService,
                                            useDevPrivilegeKeys);
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
