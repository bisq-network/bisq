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

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import io.bitsquare.app.Version;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.main.settings.preferences.PreferencesView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.CurrencyListItem;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.locale.*;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccountUtil;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class OfferBookViewModel extends ActivatableViewModel {
    protected final static Logger log = LoggerFactory.getLogger(OfferBookViewModel.class);

    private final OpenOfferManager openOfferManager;
    private final User user;
    private final OfferBook offerBook;
    final Preferences preferences;
    private final P2PService p2PService;
    final PriceFeedService priceFeedService;
    private Set<String> tradeCurrencyCodes = new HashSet<>();
    private ClosedTradableManager closedTradableManager;
    private FilterManager filterManager;
    private Navigation navigation;
    final BSFormatter formatter;

    private final FilteredList<OfferBookListItem> filteredItems;
    private final SortedList<OfferBookListItem> sortedItems;
    private TradeCurrency selectedTradeCurrency;
    private final ListChangeListener<OfferBookListItem> offerBookListItemsListener;
    final ObservableList<CurrencyListItem> currencyListItems = FXCollections.observableArrayList();
    private CurrencyListItem showAllCurrencyListItem = new CurrencyListItem(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, GUIUtil.SHOW_ALL_FLAG), -1);
    private Offer.Direction direction;

    private final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    // If id is empty string we ignore filter (display all methods)

    PaymentMethod selectedPaymentMethod = new PaymentMethod(GUIUtil.SHOW_ALL_FLAG, 0, 0, null);

    private final ObservableList<OfferBookListItem> offerBookListItems;
    private boolean isTabSelected;
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(true);
    boolean showAllPaymentMethods = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookViewModel(User user, OpenOfferManager openOfferManager, OfferBook offerBook,
                              Preferences preferences, P2PService p2PService, PriceFeedService priceFeedService,
                              ClosedTradableManager closedTradableManager, FilterManager filterManager,
                              Navigation navigation, BSFormatter formatter) {
        super();

        this.openOfferManager = openOfferManager;
        this.user = user;
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.closedTradableManager = closedTradableManager;
        this.filterManager = filterManager;
        this.navigation = navigation;
        this.formatter = formatter;

        offerBookListItems = offerBook.getOfferBookListItems();
        offerBookListItemsListener = c -> fillTradeCurrencies();

        this.filteredItems = new FilteredList<>(offerBookListItems);
        this.sortedItems = new SortedList<>(filteredItems);
    }

    @Override
    protected void activate() {
        fillTradeCurrencies();
        offerBookListItems.addListener(offerBookListItemsListener);

        String code = direction == Offer.Direction.BUY ? preferences.getBuyScreenCurrencyCode() : preferences.getSellScreenCurrencyCode();
        if (code != null && !code.equals("SHOW_ALL_FLAG") && !code.isEmpty() && CurrencyUtil.getTradeCurrency(code).isPresent()) {
            showAllTradeCurrenciesProperty.set(false);
            selectedTradeCurrency = CurrencyUtil.getTradeCurrency(code).get();
        } else {
            showAllTradeCurrenciesProperty.set(true);
            selectedTradeCurrency = CurrencyUtil.getDefaultTradeCurrency();
        }
        tradeCurrencyCode.set(selectedTradeCurrency.getCode());

        setPriceFeedType();

        btcCode.bind(preferences.btcDenominationProperty());
        offerBook.fillOfferBookListItems();
        applyFilterPredicate();
        setMarketPriceFeedCurrency();
    }

    @Override
    protected void deactivate() {
        btcCode.unbind();
        offerBookListItems.removeListener(offerBookListItemsListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithDirection(Offer.Direction direction) {
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

            setPriceFeedType();
            setMarketPriceFeedCurrency();
            applyFilterPredicate();

            if (direction == Offer.Direction.BUY)
                preferences.setBuyScreenCurrencyCode(code);
            else
                preferences.setSellScreenCurrencyCode(code);
        }
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

    Offer.Direction getDirection() {
        return direction;
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }

    TradeCurrency getSelectedTradeCurrency() {
        return selectedTradeCurrency;
    }

    ObservableList<PaymentMethod> getPaymentMethods() {
        ObservableList<PaymentMethod> list = FXCollections.observableArrayList(PaymentMethod.ALL_VALUES);
        list.add(0, new PaymentMethod(GUIUtil.SHOW_ALL_FLAG, 0, 0, null));
        return list;
    }

    ObservableList<CurrencyListItem> getCurrencyListItems() {
        return currencyListItems;
    }

    Optional<CurrencyListItem> getSelectedCurrencyListItem() {
        return currencyListItems.stream().filter(e -> tradeCurrencyCode.get() != null && e.tradeCurrency.getCode().equals(tradeCurrencyCode.get())).findAny();
    }

    CurrencyListItem getShowAllCurrencyListItem() {
        return showAllCurrencyListItem;
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
        Fiat price = offer.getPrice();
        if (price != null) {
            String postFix = "";
            if (offer.getUseMarketBasedPrice()) {
                postFix = " (" + formatter.formatPercentagePrice(offer.getMarketPriceMargin()) + ")";
            }
            if (showAllTradeCurrenciesProperty.get())
                return formatter.formatPrice(price) + postFix;
            else
                return formatter.formatPrice(price) + postFix;
        } else {
            return "N/A";
        }
    }

    String getVolume(OfferBookListItem item) {
        Offer offer = item.getOffer();
        Fiat offerVolume = offer.getOfferVolume();
        Fiat minOfferVolume = offer.getMinOfferVolume();
        if (offerVolume != null && minOfferVolume != null) {
            String postFix = showAllTradeCurrenciesProperty.get() ? " " + offer.getCurrencyCode() : "";
            if (offerVolume.equals(minOfferVolume))
                return formatter.formatVolume(offerVolume) + postFix;
            else
                return formatter.formatMinVolumeAndVolume(offer) + postFix;
        } else {
            return "N/A";
        }
    }

    String getPaymentMethod(OfferBookListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            String method = BSResources.get(offer.getPaymentMethod().getId() + "_SHORT");
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
            result = "Payment method: " + BSResources.get(offer.getPaymentMethod().getId());
            result += "\nCurrency: " + CurrencyUtil.getNameAndCode(offer.getCurrencyCode());

            String methodCountryCode = offer.getCountryCode();
            if (methodCountryCode != null) {
                String bankId = offer.getBankId();
                if (bankId != null && !bankId.equals("null")) {
                    if (BankUtil.isBankIdRequired(methodCountryCode))
                        result += "\nOfferers bank ID: " + bankId;
                    else if (BankUtil.isBankNameRequired(methodCountryCode))
                        result += "\nOfferers bank name: " + bankId;
                }
            }

            if (methodCountryCode != null)
                result += "\nOfferers seat of bank country: " + CountryUtil.getNameByCode(methodCountryCode);

            List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
            List<String> acceptedBanks = offer.getAcceptedBankIds();
            if (acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty()) {
                if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes))
                    result += "\nAccepted seat of bank countries (taker): All Euro countries";
                else
                    result += "\nAccepted seat of bank countries (taker):\n" + CountryUtil.getNamesByCodesString(acceptedCountryCodes);
            } else if (acceptedBanks != null && !acceptedBanks.isEmpty()) {
                if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK))
                    result += "\nBank name: " + acceptedBanks.get(0);
                else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS))
                    result += "\nAccepted banks: " + Joiner.on(", ").join(acceptedBanks);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setMarketPriceFeedCurrency() {
        if (!preferences.getUseStickyMarketPrice() && isTabSelected) {
            if (showAllTradeCurrenciesProperty.get())
                priceFeedService.setCurrencyCode(CurrencyUtil.getDefaultTradeCurrency().getCode());
            else
                priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
        }
    }

    private void setPriceFeedType() {
        if (CurrencyUtil.isCryptoCurrency(tradeCurrencyCode.get()))
            priceFeedService.setType(direction == Offer.Direction.SELL ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID);
        else
            priceFeedService.setType(direction == Offer.Direction.BUY ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID);
    }

    private void fillTradeCurrencies() {
        // Don't use a set as we need all entries
        Offer.Direction mirroredDirection = direction == Offer.Direction.BUY ? Offer.Direction.SELL : Offer.Direction.BUY;
        List<TradeCurrency> tradeCurrencyList = offerBookListItems.stream()
                .filter(e -> e.getOffer().getDirection() == mirroredDirection)
                .map(e -> {
                    Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(e.getOffer().getCurrencyCode());
                    if (tradeCurrencyOptional.isPresent())
                        return tradeCurrencyOptional.get();
                    else
                        return null;

                })
                .filter(e -> e != null)
                .collect(Collectors.toList());

        GUIUtil.fillCurrencyListItems(tradeCurrencyList, currencyListItems, showAllCurrencyListItem, preferences);
        tradeCurrencyCodes = currencyListItems.stream().map(e -> e.tradeCurrency.getCode()).collect(Collectors.toSet());
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
        return (showAllTradeCurrenciesProperty.get() && !user.getPaymentAccounts().isEmpty()) || user.hasPaymentAccountForCurrency(selectedTradeCurrency);
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
            boolean currencyResult;
            final String currencyCode = offer.getCurrencyCode();
            if (showAllTradeCurrenciesProperty.get()) {
                currencyResult = tradeCurrencyCodes.contains(currencyCode);
            } else
                currencyResult = currencyCode.equals(selectedTradeCurrency.getCode());

            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            boolean notMyOfferOrShowMyOffersActivated = !isMyOffer(offerBookListItem.getOffer()) || preferences.getShowOwnOffersInOfferBook();
            return directionResult && currencyResult && paymentMethodResult && notMyOfferOrShowMyOffersActivated;
        });
    }

    boolean hasMatchingArbitrator(Offer offer) {
        for (NodeAddress offerArbitratorNodeAddress : offer.getArbitratorNodeAddresses()) {
            for (NodeAddress acceptedArbitratorNodeAddress : user.getAcceptedArbitratorAddresses()) {
                if (offerArbitratorNodeAddress.equals(acceptedArbitratorNodeAddress))
                    return true;
            }
        }
        return false;
    }

    boolean isIgnored(Offer offer) {
        return preferences.getIgnoreTradersList().stream().filter(i -> i.equals(offer.getOffererNodeAddress().getHostNameWithoutPostFix())).findAny().isPresent();
    }

    boolean isOfferBanned(Offer offer) {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().bannedOfferIds.stream()
                        .filter(e -> e.equals(offer.getId()))
                        .findAny()
                        .isPresent();
    }

    boolean isNodeBanned(Offer offer) {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().bannedNodeAddress.stream()
                        .filter(e -> e.equals(offer.getOffererNodeAddress().getHostNameWithoutPostFix()))
                        .findAny()
                        .isPresent();
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

    int getNumPastTrades(Offer offer) {
        return closedTradableManager.getClosedTrades().stream()
                .filter(e -> {
                    final NodeAddress tradingPeerNodeAddress = e instanceof Trade ? ((Trade) e).getTradingPeerNodeAddress() : null;
                    return tradingPeerNodeAddress != null &&
                            tradingPeerNodeAddress.hostName.equals(offer.getOffererNodeAddress().hostName);
                })
                .collect(Collectors.toSet())
                .size();
    }
}
