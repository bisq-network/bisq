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

import com.google.inject.Inject;
import io.bitsquare.app.Version;
import io.bitsquare.btc.pricefeed.PriceFeed;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.*;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.payment.SepaAccount;
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

import java.util.List;
import java.util.Optional;

class OfferBookViewModel extends ActivatableViewModel {
    final static String SHOW_ALL_FLAG = "XXX";

    private final OpenOfferManager openOfferManager;
    private final User user;
    private final OfferBook offerBook;
    private final Preferences preferences;
    private final P2PService p2PService;
    private final PriceFeed priceFeed;
    final BSFormatter formatter;

    private final FilteredList<OfferBookListItem> filteredItems;
    private final SortedList<OfferBookListItem> sortedItems;
    private TradeCurrency selectedTradeCurrency;
    private final ObservableList<TradeCurrency> allTradeCurrencies = FXCollections.observableArrayList();

    private Offer.Direction direction;

    private final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    // If id is empty string we ignore filter (display all methods)

    private PaymentMethod selectedPaymentMethod = new PaymentMethod(SHOW_ALL_FLAG, 0, 0);

    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    private boolean isTabSelected;
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty();
    private boolean showAllPaymentMethods = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookViewModel(User user, OpenOfferManager openOfferManager, OfferBook offerBook,
                              Preferences preferences, P2PService p2PService, PriceFeed priceFeed,
                              BSFormatter formatter) {
        super();

        this.openOfferManager = openOfferManager;
        this.user = user;
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.priceFeed = priceFeed;
        this.formatter = formatter;

        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> filterList();

        this.filteredItems = new FilteredList<>(offerBookListItems);
        this.sortedItems = new SortedList<>(filteredItems);

        selectedTradeCurrency = CurrencyUtil.getDefaultTradeCurrency();
        tradeCurrencyCode.set(selectedTradeCurrency.getCode());

        preferences.getTradeCurrenciesAsObservable().addListener(new ListChangeListener<TradeCurrency>() {
            @Override
            public void onChanged(Change<? extends TradeCurrency> c) {
                fillAllTradeCurrencies();
            }
        });

    }

    @Override
    protected void activate() {
        fillAllTradeCurrencies();
        btcCode.bind(preferences.btcDenominationProperty());
        offerBookListItems.addListener(listChangeListener);
        offerBook.fillOfferBookListItems();
        filterList();
        setMarketPriceFeedCurrency();
    }


    @Override
    protected void deactivate() {
        btcCode.unbind();
        offerBookListItems.removeListener(listChangeListener);
    }

    private void fillAllTradeCurrencies() {
        allTradeCurrencies.clear();
        // Used for ignoring filter (show all)
        TradeCurrency dummy = new FiatCurrency(SHOW_ALL_FLAG);
        allTradeCurrencies.add(dummy);
        allTradeCurrencies.addAll(preferences.getTradeCurrenciesAsObservable());
    }

    private void setMarketPriceFeedCurrency() {
        if (isTabSelected) {
            if (showAllTradeCurrenciesProperty.get())
                priceFeed.setCurrencyCode(CurrencyUtil.getDefaultTradeCurrency().getCode());
            else
                priceFeed.setCurrencyCode(tradeCurrencyCode.get());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setDirection(Offer.Direction direction) {
        this.direction = direction;
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        setMarketPriceFeedCurrency();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        String code = tradeCurrency.getCode();
        showAllTradeCurrenciesProperty.set(isShowAllEntry(code));
        if (!showAllTradeCurrenciesProperty.get()) {
            this.selectedTradeCurrency = tradeCurrency;
            tradeCurrencyCode.set(code);
        }

        setMarketPriceFeedCurrency();

        filterList();
    }

    public void onSetPaymentMethod(PaymentMethod paymentMethod) {
        showAllPaymentMethods = isShowAllEntry(paymentMethod.getId());
        if (!showAllPaymentMethods)
            this.selectedPaymentMethod = paymentMethod;

        filterList();
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
        list.add(0, selectedPaymentMethod);
        return list;
    }


    String getAmount(OfferBookListItem item) {
        return (item != null) ? formatter.formatCoin(item.getOffer().getAmount()) +
                " (" + formatter.formatCoin(item.getOffer().getMinAmount()) + ")" : "";
    }

    String getPrice(OfferBookListItem item) {
        if (showAllTradeCurrenciesProperty.get())
            return (item != null) ? formatter.formatFiatWithCode(item.getOffer().getPrice()) : "";
        else
            return (item != null) ? formatter.formatFiat(item.getOffer().getPrice()) : "";
    }

    String getVolume(OfferBookListItem item) {
        Fiat offerVolume = item.getOffer().getOfferVolume();
        Fiat minOfferVolume = item.getOffer().getMinOfferVolume();
        if (showAllTradeCurrenciesProperty.get())
            return (item != null) ? formatter.formatFiatWithCode(offerVolume) +
                    " (" + formatter.formatFiatWithCode(minOfferVolume) + ")" : "";
        else
            return (item != null) ? formatter.formatFiat(offerVolume) +
                    " (" + formatter.formatFiat(minOfferVolume) + ")" : "";
    }

    String getPaymentMethod(OfferBookListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            String method = BSResources.get(offer.getPaymentMethod().getId());
            String methodCountryCode = offer.getPaymentMethodCountryCode();

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
            String methodCountryCode = offer.getPaymentMethodCountryCode();

            if (methodCountryCode != null)
                result = method + "\n\nOfferers seat of bank country:\n" + CountryUtil.getNameByCode(methodCountryCode);
            else
                result = method;

            List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
            if (acceptedCountryCodes != null && acceptedCountryCodes.size() > 0) {
                if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes))
                    result += "\n\nAccepted takers seat of bank countries:\nAll Euro countries";
                else
                    result += "\n\nAccepted taker seat of bank countries:\n" + CountryUtil.getNamesByCodesString(acceptedCountryCodes);
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

    boolean isPaymentAccountValidForOffer(Offer offer) {

        Optional<TradeCurrency> result1 = user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod()))
                .filter(paymentAccount -> {
                    List<String> offerAcceptedCountryCodes = offer.getAcceptedCountryCodes();
                    if (offerAcceptedCountryCodes != null && paymentAccount instanceof SepaAccount) {
                        return ((SepaAccount) paymentAccount).getAcceptedCountryCodes().stream()
                                .filter(offerAcceptedCountryCodes::contains)
                                .findAny().isPresent();
                    } else {
                        return true;
                    }
                })
                .flatMap(paymentAccount -> paymentAccount.getTradeCurrencies().stream())
                .filter(currency -> currency.getCode().equals(offer.getCurrencyCode())).findAny();
        return result1.isPresent();
    }

    public boolean hasPaymentAccountForCurrency() {
        return user.hasPaymentAccountForCurrency(selectedTradeCurrency);
    }

    boolean hasAcceptedArbitrators() {
        return user.getAcceptedArbitrators() != null && !user.getAcceptedArbitrators().isEmpty();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Filters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void filterList() {
        filteredItems.setPredicate(offerBookListItem -> {
            Offer offer = offerBookListItem.getOffer();
            boolean directionResult = offer.getDirection() != direction;
            boolean currencyResult = showAllTradeCurrenciesProperty.get() ||
                    offer.getCurrencyCode().equals(selectedTradeCurrency.getCode());
            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            return directionResult && currencyResult && paymentMethodResult;
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

    public boolean hasSameProtocolVersion(Offer offer) {
        return offer.getProtocolVersion() == Version.TRADE_PROTOCOL_VERSION;
    }

    private boolean isShowAllEntry(String id) {
        return id.equals(SHOW_ALL_FLAG);
    }
}
