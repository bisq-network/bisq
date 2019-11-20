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
import bisq.desktop.common.model.ActivatableViewModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.BankUtil;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.base.Joiner;

import javafx.scene.control.TableColumn;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.text.DecimalFormat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class OfferBookViewModel extends ActivatableViewModel {
    private final OpenOfferManager openOfferManager;
    private final User user;
    private final OfferBook offerBook;
    final Preferences preferences;
    private final P2PService p2PService;
    final PriceFeedService priceFeedService;
    private final ClosedTradableManager closedTradableManager;
    private final FilterManager filterManager;
    final AccountAgeWitnessService accountAgeWitnessService;
    private final Navigation navigation;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    final ObjectProperty<TableColumn.SortType> priceSortTypeProperty = new SimpleObjectProperty<>();


    private final FilteredList<OfferBookListItem> filteredItems;
    private final SortedList<OfferBookListItem> sortedItems;
    private final ListChangeListener<TradeCurrency> tradeCurrencyListChangeListener;
    private final ListChangeListener<OfferBookListItem> filterItemsListener;
    private TradeCurrency selectedTradeCurrency;
    private final ObservableList<TradeCurrency> allTradeCurrencies = FXCollections.observableArrayList();

    private OfferPayload.Direction direction;

    final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    // If id is empty string we ignore filter (display all methods)

    PaymentMethod selectedPaymentMethod = PaymentMethod.getDummyPaymentMethod(GUIUtil.SHOW_ALL_FLAG);

    private boolean isTabSelected;
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(true);
    final IntegerProperty maxPlacesForAmount = new SimpleIntegerProperty();
    final IntegerProperty maxPlacesForVolume = new SimpleIntegerProperty();
    final IntegerProperty maxPlacesForPrice = new SimpleIntegerProperty();
    final IntegerProperty maxPlacesForMarketPriceMargin = new SimpleIntegerProperty();
    boolean showAllPaymentMethods = true;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookViewModel(User user,
                              OpenOfferManager openOfferManager,
                              OfferBook offerBook,
                              Preferences preferences,
                              P2PService p2PService,
                              PriceFeedService priceFeedService,
                              ClosedTradableManager closedTradableManager,
                              FilterManager filterManager,
                              AccountAgeWitnessService accountAgeWitnessService,
                              Navigation navigation,
                              @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                              BsqFormatter bsqFormatter) {
        super();

        this.openOfferManager = openOfferManager;
        this.user = user;
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.closedTradableManager = closedTradableManager;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.navigation = navigation;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;

        this.filteredItems = new FilteredList<>(offerBook.getOfferBookListItems());
        this.sortedItems = new SortedList<>(filteredItems);

        tradeCurrencyListChangeListener = c -> {
            fillAllTradeCurrencies();
        };

        filterItemsListener = c -> {
            final Optional<OfferBookListItem> highestAmountOffer = filteredItems.stream()
                    .max(Comparator.comparingLong(o -> o.getOffer().getAmount().getValue()));

            final boolean containsRangeAmount = filteredItems.stream().anyMatch(o -> o.getOffer().isRange());

            if (highestAmountOffer.isPresent()) {
                final OfferBookListItem item = highestAmountOffer.get();
                if (!item.getOffer().isRange() && containsRangeAmount) {
                    maxPlacesForAmount.set(formatAmount(item.getOffer(), false)
                            .length() * 2 + FormattingUtils.RANGE_SEPARATOR.length());
                    maxPlacesForVolume.set(formatVolume(item.getOffer(), false)
                            .length() * 2 + FormattingUtils.RANGE_SEPARATOR.length());
                } else {
                    maxPlacesForAmount.set(formatAmount(item.getOffer(), false).length());
                    maxPlacesForVolume.set(formatVolume(item.getOffer(), false).length());
                }

            }

            final Optional<OfferBookListItem> highestPriceOffer = filteredItems.stream()
                    .filter(o -> o.getOffer().getPrice() != null)
                    .max(Comparator.comparingLong(o -> o.getOffer().getPrice() != null ? o.getOffer().getPrice().getValue() : 0));

            highestPriceOffer.ifPresent(offerBookListItem -> maxPlacesForPrice.set(formatPrice(offerBookListItem.getOffer(), false).length()));

            final Optional<OfferBookListItem> highestMarketPriceMarginOffer = filteredItems.stream()
                    .filter(o -> o.getOffer().isUseMarketBasedPrice())
                    .max(Comparator.comparing(o -> new DecimalFormat("#0.00").format(o.getOffer().getMarketPriceMargin() * 100).length()));

            highestMarketPriceMarginOffer.ifPresent(offerBookListItem -> maxPlacesForMarketPriceMargin.set(formatMarketPriceMargin(offerBookListItem.getOffer(), false).length()));
        };
    }

    @Override
    protected void activate() {

        filteredItems.addListener(filterItemsListener);

        String code = direction == OfferPayload.Direction.BUY ? preferences.getBuyScreenCurrencyCode() : preferences.getSellScreenCurrencyCode();
        if (code != null && !code.equals(GUIUtil.SHOW_ALL_FLAG) && !code.isEmpty() &&
                CurrencyUtil.getTradeCurrency(code).isPresent()) {
            showAllTradeCurrenciesProperty.set(false);
            selectedTradeCurrency = CurrencyUtil.getTradeCurrency(code).get();
        } else {
            showAllTradeCurrenciesProperty.set(true);
            selectedTradeCurrency = GlobalSettings.getDefaultTradeCurrency();
        }
        tradeCurrencyCode.set(selectedTradeCurrency.getCode());

        applyPriceSortTypeProperty(code);

        fillAllTradeCurrencies();
        preferences.getTradeCurrenciesAsObservable().addListener(tradeCurrencyListChangeListener);
        offerBook.fillOfferBookListItems();
        applyFilterPredicate();
        setMarketPriceFeedCurrency();
    }

    @Override
    protected void deactivate() {
        filteredItems.removeListener(filterItemsListener);
        preferences.getTradeCurrenciesAsObservable().removeListener(tradeCurrencyListChangeListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithDirection(OfferPayload.Direction direction) {
        this.direction = direction;
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        setMarketPriceFeedCurrency();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            String code = tradeCurrency.getCode();
            boolean showAllEntry = isShowAllEntry(code);
            showAllTradeCurrenciesProperty.set(showAllEntry);
            if (isEditEntry(code))
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            else if (!showAllEntry) {
                this.selectedTradeCurrency = tradeCurrency;
                tradeCurrencyCode.set(code);
            }
            applyPriceSortTypeProperty(code);

            setMarketPriceFeedCurrency();
            applyFilterPredicate();

            if (direction == OfferPayload.Direction.BUY)
                preferences.setBuyScreenCurrencyCode(code);
            else
                preferences.setSellScreenCurrencyCode(code);
        }
    }

    private void applyPriceSortTypeProperty(String code) {
        final OfferPayload.Direction compareDirection = CurrencyUtil.isCryptoCurrency(code) ?
                OfferPayload.Direction.SELL :
                OfferPayload.Direction.BUY;
        priceSortTypeProperty.set(getDirection() == compareDirection ?
                TableColumn.SortType.ASCENDING :
                TableColumn.SortType.DESCENDING);
    }

    void onSetPaymentMethod(PaymentMethod paymentMethod) {
        if (paymentMethod == null)
            return;

        showAllPaymentMethods = isShowAllEntry(paymentMethod.getId());
        if (!showAllPaymentMethods)
            this.selectedPaymentMethod = paymentMethod;
        else
            this.selectedPaymentMethod = PaymentMethod.getDummyPaymentMethod(GUIUtil.SHOW_ALL_FLAG);

        applyFilterPredicate();
    }

    void onRemoveOpenOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        openOfferManager.removeOffer(offer, resultHandler, errorMessageHandler);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    SortedList<OfferBookListItem> getOfferList() {
        return sortedItems;
    }

    Map<String, Integer> getBuyOfferCounts() {
        return offerBook.getBuyOfferCountMap();
    }

    Map<String, Integer> getSellOfferCounts() {
        return offerBook.getSellOfferCountMap();
    }

    boolean isMyOffer(Offer offer) {
        return openOfferManager.isMyOffer(offer);
    }

    OfferPayload.Direction getDirection() {
        return direction;
    }

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        return allTradeCurrencies;
    }

    boolean isBootstrappedOrShowPopup() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }

    TradeCurrency getSelectedTradeCurrency() {
        return selectedTradeCurrency;
    }

    ObservableList<PaymentMethod> getPaymentMethods() {
        ObservableList<PaymentMethod> list = FXCollections.observableArrayList(PaymentMethod.getPaymentMethods());
        list.add(0, PaymentMethod.getDummyPaymentMethod(GUIUtil.SHOW_ALL_FLAG));
        return list;
    }

    String getAmount(OfferBookListItem item) {
        return formatAmount(item.getOffer(), true);
    }

    private String formatAmount(Offer offer, boolean decimalAligned) {
        return DisplayUtils.formatAmount(offer, GUIUtil.AMOUNT_DECIMALS, decimalAligned, maxPlacesForAmount.get(), btcFormatter);
    }


    String getPrice(OfferBookListItem item) {
        if ((item == null))
            return "";

        final Offer offer = item.getOffer();
        final Price price = offer.getPrice();
        if (price != null) {
            return formatPrice(offer, true) + formatMarketPriceMargin(offer, true);
        } else {
            return Res.get("shared.na");
        }
    }

    String getAbsolutePriceMargin(Offer offer) {
        return FormattingUtils.formatPercentagePrice(Math.abs(offer.getMarketPriceMargin()));
    }

    private String formatPrice(Offer offer, boolean decimalAligned) {
        return DisplayUtils.formatPrice(offer.getPrice(), decimalAligned, maxPlacesForPrice.get());
    }

    private String formatMarketPriceMargin(Offer offer, boolean decimalAligned) {
        String postFix = "";
        if (offer.isUseMarketBasedPrice()) {
            postFix = " (" + FormattingUtils.formatPercentagePrice(offer.getMarketPriceMargin()) + ")";

        }

        if (decimalAligned) {
            postFix = FormattingUtils.fillUpPlacesWithEmptyStrings(postFix, maxPlacesForMarketPriceMargin.get());
        }

        return postFix;
    }

    String getVolume(OfferBookListItem item) {
        return formatVolume(item.getOffer(), true);
    }

    private String formatVolume(Offer offer, boolean decimalAligned) {
        Volume offerVolume = offer.getVolume();
        Volume minOfferVolume = offer.getMinVolume();
        if (offerVolume != null && minOfferVolume != null) {
            String postFix = showAllTradeCurrenciesProperty.get() ? " " + offer.getCurrencyCode() : "";
            decimalAligned = decimalAligned && !showAllTradeCurrenciesProperty.get();
            return DisplayUtils.formatVolume(offer, decimalAligned, maxPlacesForVolume.get()) + postFix;
        } else {
            return Res.get("shared.na");
        }
    }

    int getNumberOfDecimalsForVolume(OfferBookListItem item) {
        return CurrencyUtil.isFiatCurrency(item.getOffer().getCurrencyCode()) ? GUIUtil.FIAT_DECIMALS_WITH_ZEROS : GUIUtil.ALTCOINS_DECIMALS_WITH_ZEROS;
    }

    String getPaymentMethod(OfferBookListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            String method = Res.get(offer.getPaymentMethod().getId() + "_SHORT");
            String methodCountryCode = offer.getCountryCode();
            if (isF2F(offer)) {
                result = method + " (" + methodCountryCode + ", " + offer.getF2FCity() + ")";
            } else {
                if (methodCountryCode != null)
                    result = method + " (" + methodCountryCode + ")";
                else
                    result = method;
            }

        }
        return result;
    }

    String getPaymentMethodToolTip(OfferBookListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            result = Res.getWithCol("shared.paymentMethod") + " " + Res.get(offer.getPaymentMethod().getId());
            result += "\n" + Res.getWithCol("shared.currency") + " " + CurrencyUtil.getNameAndCode(offer.getCurrencyCode());

            String countryCode = offer.getCountryCode();
            if (isF2F(offer)) {
                if (countryCode != null) {
                    result += "\n" + Res.get("payment.f2f.offerbook.tooltip.countryAndCity",
                            CountryUtil.getNameByCode(countryCode), offer.getF2FCity());

                    result += "\n" + Res.get("payment.f2f.offerbook.tooltip.extra", offer.getF2FExtraInfo());
                }
            } else {
                if (countryCode != null) {
                    String bankId = offer.getBankId();
                    if (bankId != null && !bankId.equals("null")) {
                        if (BankUtil.isBankIdRequired(countryCode))
                            result += "\n" + Res.get("offerbook.offerersBankId", bankId);
                        else if (BankUtil.isBankNameRequired(countryCode))
                            result += "\n" + Res.get("offerbook.offerersBankName", bankId);
                    }
                }

                if (countryCode != null)
                    result += "\n" + Res.get("offerbook.offerersBankSeat", CountryUtil.getNameByCode(countryCode));

                List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
                List<String> acceptedBanks = offer.getAcceptedBankIds();
                if (acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty()) {
                    if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes))
                        result += "\n" + Res.get("offerbook.offerersAcceptedBankSeatsEuro");
                    else
                        result += "\n" + Res.get("offerbook.offerersAcceptedBankSeats", CountryUtil.getNamesByCodesString(acceptedCountryCodes));
                } else if (acceptedBanks != null && !acceptedBanks.isEmpty()) {
                    if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK))
                        result += "\n" + Res.getWithCol("shared.bankName") + " " + acceptedBanks.get(0);
                    else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS))
                        result += "\n" + Res.getWithCol("shared.acceptedBanks") + " " + Joiner.on(", ").join(acceptedBanks);
                }
            }
        }
        return result;
    }

    private boolean isF2F(Offer offer) {
        return offer.getPaymentMethod().equals(PaymentMethod.F2F);
    }

    String getDirectionLabelTooltip(Offer offer) {
        return getDirectionWithCodeDetailed(offer.getMirroredDirection(), offer.getCurrencyCode());
    }

    Optional<PaymentAccount> getMostMaturePaymentAccountForOffer(Offer offer) {
        return PaymentAccountUtil.getMostMaturePaymentAccountForOffer(offer, user.getPaymentAccounts(), accountAgeWitnessService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setMarketPriceFeedCurrency() {
        if (isTabSelected) {
            if (showAllTradeCurrenciesProperty.get())
                priceFeedService.setCurrencyCode(GlobalSettings.getDefaultTradeCurrency().getCode());
            else
                priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
        }
    }

    private void fillAllTradeCurrencies() {
        allTradeCurrencies.clear();
        // Used for ignoring filter (show all)
        allTradeCurrencies.add(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, ""));
        allTradeCurrencies.addAll(preferences.getTradeCurrenciesAsObservable());
        allTradeCurrencies.add(new CryptoCurrency(GUIUtil.EDIT_FLAG, ""));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Checks
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isAnyPaymentAccountValidForOffer(Offer offer) {
        return user.getPaymentAccounts() != null &&
                PaymentAccountUtil.isAnyTakerPaymentAccountValidForOffer(offer, user.getPaymentAccounts());
    }

    boolean hasPaymentAccountForCurrency() {
        return (showAllTradeCurrenciesProperty.get() &&
                user.getPaymentAccounts() != null &&
                !user.getPaymentAccounts().isEmpty()) ||
                user.hasPaymentAccountForCurrency(selectedTradeCurrency);
    }

    boolean canCreateOrTakeOffer() {
        return GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation) &&
                GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Filters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyFilterPredicate() {
        filteredItems.setPredicate(offerBookListItem -> {
            Offer offer = offerBookListItem.getOffer();
            boolean directionResult = offer.getDirection() != direction;
            boolean currencyResult = (showAllTradeCurrenciesProperty.get()) ||
                    offer.getCurrencyCode().equals(selectedTradeCurrency.getCode());
            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            boolean notMyOfferOrShowMyOffersActivated = !isMyOffer(offerBookListItem.getOffer()) || preferences.isShowOwnOffersInOfferBook();
            return directionResult && currencyResult && paymentMethodResult && notMyOfferOrShowMyOffersActivated;
        });
    }

    boolean isIgnored(Offer offer) {
        return preferences.getIgnoreTradersList().stream()
                .anyMatch(i -> i.equals(offer.getMakerNodeAddress().getFullAddress()));
    }

    boolean isOfferBanned(Offer offer) {
        return filterManager.isOfferIdBanned(offer.getId());
    }

    boolean isCurrencyBanned(Offer offer) {
        return filterManager.isCurrencyBanned(offer.getCurrencyCode());
    }

    boolean isPaymentMethodBanned(Offer offer) {
        return filterManager.isPaymentMethodBanned(offer.getPaymentMethod());
    }

    boolean isNodeAddressBanned(Offer offer) {
        return filterManager.isNodeAddressBanned(offer.getMakerNodeAddress());
    }

    boolean requireUpdateToNewVersion() {
        return filterManager.requireUpdateToNewVersionForTrading();
    }

    boolean isInsufficientCounterpartyTradeLimit(Offer offer) {
        return CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) &&
                !accountAgeWitnessService.verifyPeersTradeAmount(offer, offer.getAmount(), errorMessage -> {
                });
    }

    boolean isMyInsufficientTradeLimit(Offer offer) {
        Optional<PaymentAccount> accountOptional = getMostMaturePaymentAccountForOffer(offer);
        final long myTradeLimit = accountOptional
                .map(paymentAccount -> accountAgeWitnessService.getMyTradeLimit(paymentAccount,
                        offer.getCurrencyCode(), offer.getMirroredDirection()))
                .orElse(0L);
        final long offerMinAmount = offer.getMinAmount().value;
        log.debug("isInsufficientTradeLimit accountOptional={}, myTradeLimit={}, offerMinAmount={}, ",
                accountOptional.isPresent() ? accountOptional.get().getAccountName() : "null",
                Coin.valueOf(myTradeLimit).toFriendlyString(),
                Coin.valueOf(offerMinAmount).toFriendlyString());
        return CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) &&
                accountOptional.isPresent() &&
                myTradeLimit < offerMinAmount;
    }

    boolean hasSameProtocolVersion(Offer offer) {
        return offer.getProtocolVersion() == Version.TRADE_PROTOCOL_VERSION;
    }

    private boolean isShowAllEntry(String id) {
        return id.equals(GUIUtil.SHOW_ALL_FLAG);
    }

    private boolean isEditEntry(String id) {
        return id.equals(GUIUtil.EDIT_FLAG);
    }

    int getNumTrades(Offer offer) {
        return closedTradableManager.getClosedTradables().stream()
                .filter(e -> {
                    final NodeAddress tradingPeerNodeAddress = e instanceof Trade ? ((Trade) e).getTradingPeerNodeAddress() : null;
                    return tradingPeerNodeAddress != null &&
                            tradingPeerNodeAddress.getFullAddress().equals(offer.getMakerNodeAddress().getFullAddress());
                })
                .collect(Collectors.toSet())
                .size();
    }

    public boolean hasSelectionAccountSigning() {
        if (showAllTradeCurrenciesProperty.get()) {
            if (!selectedPaymentMethod.getId().equals(GUIUtil.SHOW_ALL_FLAG)) {
                return PaymentMethod.hasChargebackRisk(selectedPaymentMethod);
            }
        } else {
            if (selectedPaymentMethod.getId().equals(GUIUtil.SHOW_ALL_FLAG))
                return CurrencyUtil.getMatureMarketCurrencies().stream()
                        .anyMatch(c -> c.getCode().equals(selectedTradeCurrency.getCode()));
            else
                return PaymentMethod.hasChargebackRisk(selectedPaymentMethod, tradeCurrencyCode.get());
        }
        return true;
    }

    public String getMakerFeeAsString(Offer offer) {
        return offer.isCurrencyForMakerFeeBtc() ?
                btcFormatter.formatCoinWithCode(offer.getMakerFee()) :
                bsqFormatter.formatCoinWithCode(offer.getMakerFee());
    }

    private static String getDirectionWithCodeDetailed(OfferPayload.Direction direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return (direction == OfferPayload.Direction.BUY) ? Res.get("shared.buyingBTCWith", currencyCode) : Res.get("shared.sellingBTCFor", currencyCode);
        else
            return (direction == OfferPayload.Direction.SELL) ? Res.get("shared.buyingCurrency", currencyCode) : Res.get("shared.sellingCurrency", currencyCode);
    }
}
