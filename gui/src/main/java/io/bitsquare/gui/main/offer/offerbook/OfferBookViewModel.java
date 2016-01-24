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
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.payment.SepaAccount;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.List;
import java.util.Optional;

class OfferBookViewModel extends ActivatableViewModel {
    private final OpenOfferManager openOfferManager;
    private final User user;
    private final OfferBook offerBook;
    private final Preferences preferences;
    private final P2PService p2PService;
    private final BSFormatter formatter;

    private final FilteredList<OfferBookListItem> filteredItems;
    private final SortedList<OfferBookListItem> sortedItems;
    private TradeCurrency tradeCurrency;

    private Offer.Direction direction;

    private final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    private PaymentMethod paymentMethod = new AllPaymentMethodsEntry();
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookViewModel(User user, OpenOfferManager openOfferManager, OfferBook offerBook,
                              Preferences preferences, P2PService p2PService,
                              BSFormatter formatter) {
        super();

        this.openOfferManager = openOfferManager;
        this.user = user;
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.formatter = formatter;

        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> filterList();

        this.filteredItems = new FilteredList<>(offerBookListItems);
        this.sortedItems = new SortedList<>(filteredItems);

        tradeCurrency = CurrencyUtil.getDefaultTradeCurrency();
        tradeCurrencyCode.set(tradeCurrency.getCode());
    }

    @Override
    protected void activate() {
        btcCode.bind(preferences.btcDenominationProperty());
        offerBookListItems.addListener(listChangeListener);
        offerBook.fillOfferBookListItems();
        filterList();
    }

    @Override
    protected void deactivate() {
        btcCode.unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setDirection(Offer.Direction direction) {
        this.direction = direction;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        this.tradeCurrency = tradeCurrency;
        tradeCurrencyCode.set(tradeCurrency.getCode());
        filterList();
    }

    public void onSetPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        filterList();
    }

    void onRemoveOpenOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        openOfferManager.onRemoveOpenOffer(offer, resultHandler, errorMessageHandler);
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
        ObservableList<TradeCurrency> list = preferences.getTradeCurrenciesAsObservable();
       /* list.add(0, new AllTradeCurrenciesEntry());*/
        return list;
    }

    boolean isNetworkReady() {
        return p2PService.isNetworkReady();
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    public ObservableList<PaymentMethod> getPaymentMethods() {
        ObservableList<PaymentMethod> list = FXCollections.observableArrayList(PaymentMethod.ALL_VALUES);
        list.add(0, paymentMethod);
        return list;
    }


    String getAmount(OfferBookListItem item) {
        return (item != null) ? formatter.formatCoin(item.getOffer().getAmount()) +
                " (" + formatter.formatCoin(item.getOffer().getMinAmount()) + ")" : "";
    }

    String getPrice(OfferBookListItem item) {
        return (item != null) ? formatter.formatFiat(item.getOffer().getPrice()) : "";
    }

    String getVolume(OfferBookListItem item) {
        return (item != null) ? formatter.formatFiat(item.getOffer().getOfferVolume()) +
                " (" + formatter.formatFiat(item.getOffer().getMinOfferVolume()) + ")" : "";
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
                result = method + "\nOfferers country of bank: " + CountryUtil.getNameByCode(methodCountryCode);
            else
                result = method;

            List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
            if (acceptedCountryCodes != null && acceptedCountryCodes.size() > 0) {
                if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes))
                    result += "\nAccepted taker countries: All Euro countries";
                else
                    result += "\nAccepted taker countries: " + CountryUtil.getNamesByCodesString(acceptedCountryCodes);
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
        return user.hasPaymentAccountForCurrency(tradeCurrency);
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
            boolean currencyResult = offer.getCurrencyCode().equals(tradeCurrency.getCode());
            boolean paymentMethodResult = true;
            if (!(paymentMethod instanceof AllPaymentMethodsEntry))
                paymentMethodResult = offer.getPaymentMethod().equals(paymentMethod);

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
}
