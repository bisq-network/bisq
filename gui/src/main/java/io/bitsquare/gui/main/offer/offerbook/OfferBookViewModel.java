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
import io.bitsquare.btc.pricefeed.PriceFeed;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.main.settings.preferences.PreferencesView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.*;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.*;
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
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

class OfferBookViewModel extends ActivatableViewModel {
    protected final static Logger log = LoggerFactory.getLogger(OfferBookViewModel.class);

    final static String SHOW_ALL_FLAG = "SHOW_ALL_FLAG";
    final static String EDIT_FLAG = "EDIT_FLAG";

    private final OpenOfferManager openOfferManager;
    private final User user;
    private final OfferBook offerBook;
    final Preferences preferences;
    private final P2PService p2PService;
    final PriceFeed priceFeed;
    private ClosedTradableManager closedTradableManager;
    private FilterManager filterManager;
    private Navigation navigation;
    final BSFormatter formatter;

    private final FilteredList<OfferBookListItem> filteredItems;
    private final SortedList<OfferBookListItem> sortedItems;
    private final ListChangeListener<TradeCurrency> tradeCurrencyListChangeListener;
    private TradeCurrency selectedTradeCurrency;
    private final ObservableList<TradeCurrency> allTradeCurrencies = FXCollections.observableArrayList();

    private Offer.Direction direction;

    private final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    // If id is empty string we ignore filter (display all methods)

    PaymentMethod selectedPaymentMethod = new PaymentMethod(SHOW_ALL_FLAG, 0, 0, null);
    private CryptoCurrency showAllCurrenciesItem = new CryptoCurrency(SHOW_ALL_FLAG, SHOW_ALL_FLAG);

    private final ObservableList<OfferBookListItem> offerBookListItems;
    private boolean isTabSelected;
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(true);
    boolean showAllPaymentMethods = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookViewModel(User user, OpenOfferManager openOfferManager, OfferBook offerBook,
                              Preferences preferences, P2PService p2PService, PriceFeed priceFeed,
                              ClosedTradableManager closedTradableManager, FilterManager filterManager,
                              Navigation navigation, BSFormatter formatter) {
        super();

        this.openOfferManager = openOfferManager;
        this.user = user;
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.priceFeed = priceFeed;
        this.closedTradableManager = closedTradableManager;
        this.filterManager = filterManager;
        this.navigation = navigation;
        this.formatter = formatter;

        offerBookListItems = offerBook.getOfferBookListItems();

        this.filteredItems = new FilteredList<>(offerBookListItems);
        this.sortedItems = new SortedList<>(filteredItems);

        tradeCurrencyListChangeListener = c -> fillAllTradeCurrencies();
    }

    @Override
    protected void activate() {
        fillAllTradeCurrencies();
        btcCode.bind(preferences.btcDenominationProperty());
        preferences.getTradeCurrenciesAsObservable().addListener(tradeCurrencyListChangeListener);
        offerBook.fillOfferBookListItems();
        applyFilterPredicate();
        setMarketPriceFeedCurrency();
    }


    @Override
    protected void deactivate() {
        btcCode.unbind();
        preferences.getTradeCurrenciesAsObservable().removeListener(tradeCurrencyListChangeListener);
    }

    private void fillAllTradeCurrencies() {
        allTradeCurrencies.clear();
        // Used for ignoring filter (show all)
        allTradeCurrencies.add(showAllCurrenciesItem);
        allTradeCurrencies.addAll(preferences.getTradeCurrenciesAsObservable());
        allTradeCurrencies.add(new CryptoCurrency(EDIT_FLAG, EDIT_FLAG));
    }

    private void setMarketPriceFeedCurrency() {
        if (!preferences.getUseStickyMarketPrice() && isTabSelected) {
            if (showAllTradeCurrenciesProperty.get())
                priceFeed.setCurrencyCode(CurrencyUtil.getDefaultTradeCurrency().getCode());
            else
                priceFeed.setCurrencyCode(tradeCurrencyCode.get());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithDirection(Offer.Direction direction) {
        this.direction = direction;

        String code = direction == Offer.Direction.BUY ? preferences.getBuyScreenCurrencyCode() : preferences.getSellScreenCurrencyCode();
        if (code != null && !code.isEmpty() && CurrencyUtil.getTradeCurrency(code).isPresent()) {
            showAllTradeCurrenciesProperty.set(false);
            selectedTradeCurrency = CurrencyUtil.getTradeCurrency(code).get();
        } else {
            showAllTradeCurrenciesProperty.set(true);
            selectedTradeCurrency = CurrencyUtil.getDefaultTradeCurrency();
        }
        tradeCurrencyCode.set(selectedTradeCurrency.getCode());
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        setMarketPriceFeedCurrency();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
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

            setMarketPriceFeedCurrency();

            applyFilterPredicate();

            if (direction == Offer.Direction.BUY)
                preferences.setBuyScreenCurrencyCode(code);
            else
                preferences.setSellScreenCurrencyCode(code);
        }
    }

    public void onSetPaymentMethod(PaymentMethod paymentMethod) {
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

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        return allTradeCurrencies;
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }

    public TradeCurrency getSelectedTradeCurrency() {
        return selectedTradeCurrency;
    }

    public ObservableList<PaymentMethod> getPaymentMethods() {
        ObservableList<PaymentMethod> list = FXCollections.observableArrayList(PaymentMethod.ALL_VALUES);
        list.add(0, new PaymentMethod(SHOW_ALL_FLAG, 0, 0, null));
        return list;
    }


    String getAmount(OfferBookListItem item) {
        return (item != null) ? formatter.formatCoin(item.getOffer().getAmount()) +
                " (" + formatter.formatCoin(item.getOffer().getMinAmount()) + ")" : "";
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
                return formatter.formatPriceWithCode(price) + postFix;
            else
                return formatter.formatFiat(price) + postFix;
        } else {
            return "N/A";
        }
    }

    String getVolume(OfferBookListItem item) {
        Fiat offerVolume = item.getOffer().getOfferVolume();
        Fiat minOfferVolume = item.getOffer().getMinOfferVolume();
        if (offerVolume != null && minOfferVolume != null) {
            if (showAllTradeCurrenciesProperty.get())
                return formatter.formatFiatWithCode(offerVolume) + " (" + formatter.formatFiatWithCode(minOfferVolume) + ")";
            else
                return formatter.formatFiat(offerVolume) + " (" + formatter.formatFiat(minOfferVolume) + ")";
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
            String method = BSResources.get(offer.getPaymentMethod().getId());
            String methodCountryCode = offer.getCountryCode();

            if (methodCountryCode != null)
                result = method + "\n\nOfferers seat of bank country:\n" + CountryUtil.getNameByCode(methodCountryCode);
            else
                result = method;

            List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
            List<String> acceptedBanks = offer.getAcceptedBankIds();
            if (acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty()) {
                if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes))
                    result += "\n\nAccepted takers seat of bank countries:\nAll Euro countries";
                else
                    result += "\n\nAccepted taker seat of bank countries:\n" + CountryUtil.getNamesByCodesString(acceptedCountryCodes);
            } else if (acceptedBanks != null && !acceptedBanks.isEmpty()) {
                if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK))
                    result += "\n\nBank name: " + acceptedBanks.get(0);
                else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS))
                    result += "\n\nAccepted banks: " + Joiner.on(", ").join(acceptedBanks);
            }
        }
        return result;
    }

    String getDirectionLabel(Offer offer) {
        return formatter.getDirection(offer.getMirroredDirection());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Checks
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean hasPaymentAccount() {
        return user.currentPaymentAccountProperty().get() != null;
    }

    boolean isAnyPaymentAccountValidForOffer(Offer offer) {
        return isAnyPaymentAccountValidForOffer(offer, user.getPaymentAccounts());
    }

    static boolean isAnyPaymentAccountValidForOffer(Offer offer, Collection<PaymentAccount> paymentAccounts) {
        for (PaymentAccount paymentAccount : paymentAccounts) {
            if (isPaymentAccountValidForOffer(offer, paymentAccount))
                return true;
        }
        return false;
    }

    //TODO not tested with all combinations yet....
    static boolean isPaymentAccountValidForOffer(Offer offer, PaymentAccount paymentAccount) {
        // check if we have  a matching currency
        Set<String> paymentAccountCurrencyCodes = paymentAccount.getTradeCurrencies().stream().map(TradeCurrency::getCode).collect(Collectors.toSet());
        boolean matchesCurrencyCode = paymentAccountCurrencyCodes.contains(offer.getCurrencyCode());
        if (!matchesCurrencyCode)
            return false;

        // check if we have a matching payment method or if its a bank account payment method which is treated special
        if (paymentAccount instanceof CountryBasedPaymentAccount) {
            CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) paymentAccount;

            // check if we have a matching country
            boolean matchesCountryCodes = offer.getAcceptedCountryCodes() != null && countryBasedPaymentAccount.getCountry() != null &&
                    offer.getAcceptedCountryCodes().contains(countryBasedPaymentAccount.getCountry().code);
            if (!matchesCountryCodes)
                return false;

            if (paymentAccount instanceof SepaAccount || offer.getPaymentMethod().equals(PaymentMethod.SEPA)) {
                boolean samePaymentMethod = paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod());
                return samePaymentMethod;
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK) ||
                    offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {

                checkNotNull(offer.getAcceptedBankIds(), "offer.getAcceptedBankIds() must not be null");
                if (paymentAccount instanceof SpecificBanksAccount) {
                    // check if we have a matching bank
                    boolean offerSideMatchesBank = offer.getAcceptedBankIds().contains(((BankAccount) paymentAccount).getBankId());
                    boolean paymentAccountSideMatchesBank = ((SpecificBanksAccount) paymentAccount).getAcceptedBanks().contains(offer.getBankId());
                    return offerSideMatchesBank && paymentAccountSideMatchesBank;
                } else {
                    // national or same bank
                    boolean matchesBank = offer.getAcceptedBankIds().contains(((BankAccount) paymentAccount).getBankId());
                    return matchesBank;
                }
            } else {
                if (paymentAccount instanceof SpecificBanksAccount) {
                    // check if we have a matching bank
                    boolean paymentAccountSideMatchesBank = ((SpecificBanksAccount) paymentAccount).getAcceptedBanks().contains(offer.getBankId());
                    return paymentAccountSideMatchesBank;
                } else if (paymentAccount instanceof SameBankAccount) {
                    // check if we have a matching bank
                    boolean paymentAccountSideMatchesBank = ((SameBankAccount) paymentAccount).getBankId().equals(offer.getBankId());
                    return paymentAccountSideMatchesBank;
                } else {
                    // national
                    return true;
                }
            }

        } else {
            return paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod());
        }
    }

    public boolean hasPaymentAccountForCurrency() {
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
            boolean currencyResult = showAllTradeCurrenciesProperty.get() ||
                    offer.getCurrencyCode().equals(selectedTradeCurrency.getCode());
            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            boolean notMyOfferOrShowMyOffersActivated = !isMyOffer(offerBookListItem.getOffer()) || preferences.getShowOwnOffersInOfferBook();
            return directionResult && currencyResult && paymentMethodResult && notMyOfferOrShowMyOffersActivated;
        });
    }

    public boolean hasMatchingArbitrator(Offer offer) {
        for (NodeAddress offerArbitratorNodeAddress : offer.getArbitratorNodeAddresses()) {
            for (NodeAddress acceptedArbitratorNodeAddress : user.getAcceptedArbitratorAddresses()) {
                if (offerArbitratorNodeAddress.equals(acceptedArbitratorNodeAddress))
                    return true;
            }
        }
        return false;
    }

    boolean isIgnored(Offer offer) {
        return preferences.getIgnoreTradersList().stream().filter(i -> i.equals(offer.getOffererNodeAddress().hostName)).findAny().isPresent();
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
                        .filter(e -> e.equals(offer.getOffererNodeAddress().hostName))
                        .findAny()
                        .isPresent();
    }

    boolean hasSameProtocolVersion(Offer offer) {
        return offer.getProtocolVersion() == Version.TRADE_PROTOCOL_VERSION;
    }

    private boolean isShowAllEntry(String id) {
        return id.equals(SHOW_ALL_FLAG);
    }

    private boolean isEditEntry(String id) {
        return id.equals(EDIT_FLAG);
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
