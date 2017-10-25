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

package io.bisq.gui.main.offer.offerbook;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import io.bisq.common.GlobalSettings;
import io.bisq.common.app.Version;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.*;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PaymentAccountUtil;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.ActivatableViewModel;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.settings.SettingsView;
import io.bisq.gui.main.settings.preferences.PreferencesView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableColumn;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    final BSFormatter formatter;
    final ObjectProperty<TableColumn.SortType> priceSortTypeProperty = new SimpleObjectProperty<>();


    private final FilteredList<OfferBookListItem> filteredItems;
    private final SortedList<OfferBookListItem> sortedItems;
    private final ListChangeListener<TradeCurrency> tradeCurrencyListChangeListener;
    private TradeCurrency selectedTradeCurrency;
    private final ObservableList<TradeCurrency> allTradeCurrencies = FXCollections.observableArrayList();

    private OfferPayload.Direction direction;

    final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    // If id is empty string we ignore filter (display all methods)

    PaymentMethod selectedPaymentMethod = new PaymentMethod(GUIUtil.SHOW_ALL_FLAG);

    private boolean isTabSelected;
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(true);
    boolean showAllPaymentMethods = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public OfferBookViewModel(User user, OpenOfferManager openOfferManager, OfferBook offerBook,
                              Preferences preferences, P2PService p2PService, PriceFeedService priceFeedService,
                              ClosedTradableManager closedTradableManager, FilterManager filterManager,
                              AccountAgeWitnessService accountAgeWitnessService, Navigation navigation, BSFormatter formatter) {
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
        this.formatter = formatter;

        ObservableList<OfferBookListItem> offerBookListItems = offerBook.getOfferBookListItems();

        this.filteredItems = new FilteredList<>(offerBookListItems);
        this.sortedItems = new SortedList<>(filteredItems);

        tradeCurrencyListChangeListener = c -> {
            fillAllTradeCurrencies();
        };
    }

    @Override
    protected void activate() {
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
                //noinspection unchecked
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
        showAllPaymentMethods = isShowAllEntry(paymentMethod.getId());
        if (!showAllPaymentMethods)
            this.selectedPaymentMethod = paymentMethod;

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

    boolean isMyOffer(Offer offer) {
        return openOfferManager.isMyOffer(offer);
    }

    OfferPayload.Direction getDirection() {
        return direction;
    }

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        return allTradeCurrencies;
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }

    TradeCurrency getSelectedTradeCurrency() {
        return selectedTradeCurrency;
    }

    ObservableList<PaymentMethod> getPaymentMethods() {
        ObservableList<PaymentMethod> list = FXCollections.observableArrayList(PaymentMethod.getAllValues());
        list.add(0, new PaymentMethod(GUIUtil.SHOW_ALL_FLAG));
        return list;
    }

    String getAmount(OfferBookListItem item) {
        Offer offer = item.getOffer();
        Coin amount = offer.getAmount();
        Coin minAmount = offer.getMinAmount();
        if (amount.equals(minAmount))
            return formatter.formatAmount(offer);
        else
            return formatter.formatAmountWithMinAmount(offer);
    }

    String getPrice(OfferBookListItem item) {
        if ((item == null))
            return "";

        Offer offer = item.getOffer();
        Price price = offer.getPrice();
        if (price != null) {
            String postFix = "";
            if (offer.isUseMarketBasedPrice()) {
                postFix = " (" + formatter.formatPercentagePrice(offer.getMarketPriceMargin()) + ")";
            }
            if (showAllTradeCurrenciesProperty.get())
                return formatter.formatPrice(price) + postFix;
            else
                return formatter.formatPrice(price) + postFix;
        } else {
            return Res.get("shared.na");
        }
    }

    String getVolume(OfferBookListItem item) {
        Offer offer = item.getOffer();
        Volume offerVolume = offer.getVolume();
        Volume minOfferVolume = offer.getMinVolume();
        if (offerVolume != null && minOfferVolume != null) {
            String postFix = showAllTradeCurrenciesProperty.get() ? " " + offer.getCurrencyCode() : "";
            if (offerVolume.equals(minOfferVolume))
                return formatter.formatVolume(offerVolume) + postFix;
            else
                return formatter.formatMinVolumeAndVolume(offer) + postFix;
        } else {
            return Res.get("shared.na");
        }
    }

    String getPaymentMethod(OfferBookListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            String method = Res.get(offer.getPaymentMethod().getId() + "_SHORT");
            String methodCountryCode = offer.getCountryCode();

            if (methodCountryCode != null)
                result = method + " (" + methodCountryCode + ")";
            else
                result = method;
        }
        return result;
    }

    String getPaymentMethodToolTip(OfferBookListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            result = Res.getWithCol("shared.paymentMethod") + " " + Res.get(offer.getPaymentMethod().getId());
            result += "\n" + Res.getWithCol("shared.currency") + " " + CurrencyUtil.getNameAndCode(offer.getCurrencyCode());

            String methodCountryCode = offer.getCountryCode();
            if (methodCountryCode != null) {
                String bankId = offer.getBankId();
                if (bankId != null && !bankId.equals("null")) {
                    if (BankUtil.isBankIdRequired(methodCountryCode))
                        result += "\n" + Res.get("offerbook.offerersBankId", bankId);
                    else if (BankUtil.isBankNameRequired(methodCountryCode))
                        result += "\n" + Res.get("offerbook.offerersBankName", bankId);
                }
            }

            if (methodCountryCode != null)
                result += "\n" + Res.get("offerbook.offerersBankSeat", CountryUtil.getNameByCode(methodCountryCode));

            List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
            List<String> acceptedBanks = offer.getAcceptedBankIds();
            if (acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty()) {
                if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes))
                    result += "\n" +Res.get("offerbook.offerersAcceptedBankSeatsEuro");
                else
                    result += "\n" +Res.get("offerbook.offerersAcceptedBankSeats", CountryUtil.getNamesByCodesString(acceptedCountryCodes));
            } else if (acceptedBanks != null && !acceptedBanks.isEmpty()) {
                if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK))
                    result += "\n" + Res.getWithCol("shared.bankName") + " " + acceptedBanks.get(0);
                else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS))
                    result += "\n" + Res.getWithCol("shared.acceptedBanks") + " " + Joiner.on(", ").join(acceptedBanks);
            }
        }
        return result;
    }

    String getDirectionLabel(Offer offer) {
        return formatter.getDirectionWithCode(offer.getMirroredDirection(), offer.getCurrencyCode());
    }

    String getDirectionLabelTooltip(Offer offer) {
        return formatter.getDirectionWithCodeDetailed(offer.getMirroredDirection(), offer.getCurrencyCode());
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
        allTradeCurrencies.add(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, GUIUtil.SHOW_ALL_FLAG));
        allTradeCurrencies.addAll(preferences.getTradeCurrenciesAsObservable());
        allTradeCurrencies.add(new CryptoCurrency(GUIUtil.EDIT_FLAG, GUIUtil.EDIT_FLAG));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Checks
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean hasPaymentAccount() {
        return user.currentPaymentAccountProperty().get() != null;
    }

    boolean isAnyPaymentAccountValidForOffer(Offer offer) {
        return PaymentAccountUtil.isAnyPaymentAccountValidForOffer(offer, user.getPaymentAccounts());
    }

    boolean hasPaymentAccountForCurrency() {
        return (showAllTradeCurrenciesProperty.get() &&
                user.getPaymentAccounts() != null &&
                !user.getPaymentAccounts().isEmpty()) ||
                user.hasPaymentAccountForCurrency(selectedTradeCurrency);
    }

    boolean hasAcceptedArbitrators() {
        return user.getAcceptedArbitrators() != null && !user.getAcceptedArbitrators().isEmpty();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Filters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyFilterPredicate() {
        filteredItems.setPredicate(offerBookListItem -> {
            Offer offer = offerBookListItem.getOffer();
            boolean directionResult = offer.getDirection() != direction;
            boolean currencyResult = showAllTradeCurrenciesProperty.get() ||
                    offer.getCurrencyCode().equals(selectedTradeCurrency.getCode());
            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            boolean notMyOfferOrShowMyOffersActivated = !isMyOffer(offerBookListItem.getOffer()) || preferences.isShowOwnOffersInOfferBook();
            return directionResult && currencyResult && paymentMethodResult && notMyOfferOrShowMyOffersActivated;
        });
    }

    boolean hasMatchingArbitrator(Offer offer) {
        final List<NodeAddress> acceptedArbitratorAddresses = user.getAcceptedArbitratorAddresses();
        if (acceptedArbitratorAddresses != null) {
            for (NodeAddress offerArbitratorNodeAddress : offer.getArbitratorNodeAddresses()) {
                for (NodeAddress acceptedArbitratorNodeAddress : acceptedArbitratorAddresses) {
                    if (offerArbitratorNodeAddress.equals(acceptedArbitratorNodeAddress))
                        return true;
                }
            }
        }
        return false;
    }

    boolean isIgnored(Offer offer) {
        return preferences.getIgnoreTradersList().stream().filter(i -> i.equals(offer.getMakerNodeAddress().getHostNameWithoutPostFix())).findAny().isPresent();
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
        return filterManager.isNodeAddressBanned(offer.getMakerNodeAddress().getHostNameWithoutPostFix());
    }

    boolean isInsufficientTradeLimit(Offer offer) {
        Optional<PaymentAccount> accountOptional = getMostMaturePaymentAccountForOffer(offer);
        final long myTradeLimit = accountOptional.isPresent() ? accountAgeWitnessService.getMyTradeLimit(accountOptional.get(), offer.getCurrencyCode()) : 0L;
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
}
