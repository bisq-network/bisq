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

package io.bitsquare.gui.main.trade.orderbook;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.UIModel;
import io.bitsquare.gui.main.trade.OrderBookInfo;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.ExchangeRate;
import com.google.bitcoin.utils.Fiat;

import com.google.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.BSFormatter.reduceTo4Decimals;

public class OrderBookModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(OrderBookModel.class);

    private final User user;
    private final OrderBook orderBook;
    private final Settings settings;
    private final TradeManager tradeManager;

    private final FilteredList<OrderBookListItem> filteredItems;
    private final SortedList<OrderBookListItem> sortedItems;
    private OrderBookInfo orderBookInfo;
    private ChangeListener<BankAccount> bankAccountChangeListener;

    private final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    private final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    private final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();

    final StringProperty restrictionsInfo = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final ObjectProperty<Country> bankAccountCountry = new SimpleObjectProperty<>();
    final ObjectProperty<Comparator<OrderBookListItem>> comparator = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OrderBookModel(User user,
                          TradeManager tradeManager,
                          OrderBook orderBook,
                          Settings settings) {
        this.tradeManager = tradeManager;
        this.user = user;
        this.orderBook = orderBook;
        this.settings = settings;

        filteredItems = new FilteredList<>(orderBook.getOrderBookListItems());
        sortedItems = new SortedList<>(filteredItems);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        bankAccountChangeListener = (observableValue, oldValue, newValue) -> setBankAccount(newValue);

        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        orderBook.addClient();
        user.currentBankAccountProperty().addListener(bankAccountChangeListener);
        btcCode.bind(settings.btcDenominationProperty());

        setBankAccount(user.getCurrentBankAccount());
        applyFilter();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        orderBook.removeClient();
        user.currentBankAccountProperty().removeListener(bankAccountChangeListener);
        btcCode.unbind();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setOrderBookInfo(OrderBookInfo orderBookInfo) {
        this.orderBookInfo = orderBookInfo;
    }

    void removeOffer(Offer offer) {
        tradeManager.removeOffer(offer);
    }

    void calculateVolume() {
        try {
            if (priceAsFiat.get() != null &&
                    amountAsCoin.get() != null &&
                    !amountAsCoin.get().isZero() &&
                    !priceAsFiat.get().isZero()) {
                volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateAmount() {
        try {
            if (volumeAsFiat.get() != null &&
                    priceAsFiat.get() != null &&
                    !volumeAsFiat.get().isZero() &&
                    !priceAsFiat.get().isZero()) {
                // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
                amountAsCoin.set(reduceTo4Decimals(new ExchangeRate(priceAsFiat.get()).fiatToCoin(volumeAsFiat.get())));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    boolean isTradable(Offer offer) {
        // if user has not registered yet we display all
        if (user.getCurrentBankAccount() == null)
            return true;


        boolean countryResult = offer.getAcceptedCountries().contains(user.getCurrentBankAccount().getCountry());
        if (!countryResult)
            restrictionsInfo.set("This offer requires that the payments account resides in one of those countries:\n" +
                    BSFormatter.countryLocalesToString(offer.getAcceptedCountries()) +
                    "\n\nThe country of your payments account (" + user.getCurrentBankAccount().getCountry().getName() +
                    ") is not included in that list.");
        //TODO

        // One of the supported languages from the settings must match one of the offer languages (n to n)
      /*  boolean languageResult =
                languagesInList(settings.getAcceptedLanguageLocales(), offer.getAcceptedLanguageLocales());

        // Apply applyFilter only if there is a valid value set
        // The requested amount must be lower or equal then the offer amount
        boolean amountResult = true;
        if (orderBookInfo.getAmount() != null && orderBookInfo.getAmount().isPositive())
            amountResult = orderBookInfo.getAmount().compareTo(offer.getAmount()) <= 0;


        // Apply applyFilter only if there is a valid value set
        boolean priceResult = true;
        if (orderBookInfo.getPrice() != null && orderBookInfo.getPrice().isPositive()) {
            if (offer.getDirection() == Direction.SELL)
                priceResult = orderBookInfo.getPrice().compareTo(offer.getPrice()) >= 0;
            else
                priceResult = orderBookInfo.getPrice().compareTo(offer.getPrice()) <= 0;
        }
    
        // The arbitrator defined in the offer must match one of the accepted arbitrators defined in the settings
        // (1 to n)
        boolean arbitratorResult = arbitratorsInList(offer.getArbitrators(), settings.getAcceptedArbitrators());

        boolean result = countryResult && languageResult && amountResult && priceResult && arbitratorResult;
       
        log.debug("getPrice " + orderBookInfo.getPrice());
        log.debug("getAmount " + orderBookInfo.getAmount());
        log.debug("countryResult " + countryResult);
        log.debug("languageResult " + languageResult);
        log.debug("amountResult " + amountResult);
        log.debug("priceResult " + priceResult);
        log.debug("arbitratorResult " + arbitratorResult);
        log.debug("Offer filter result " + result);*/

        return countryResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setAmount(Coin amount) {
        amountAsCoin.set(amount);
        orderBookInfo.setAmount(amount);
        applyFilter();
    }

    void setPrice(Fiat price) {
        priceAsFiat.set(price);
        orderBookInfo.setPrice(price);
        applyFilter();
    }

    void setVolume(Fiat volume) {
        volumeAsFiat.set(volume);
        orderBookInfo.setVolume(volume);
        applyFilter();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    SortedList<OrderBookListItem> getOfferList() {
        return sortedItems;
    }

    boolean isRegistered() {
        return user.getAccountId() != null;
    }

    boolean isMyOffer(Offer offer) {
        return offer.getMessagePublicKey() != null ?
                offer.getMessagePublicKey().equals(user.getMessagePublicKey()) : false;
    }

    Coin getAmountAsCoin() {
        return amountAsCoin.get();
    }

    ObjectProperty<Coin> amountAsCoinProperty() {
        return amountAsCoin;
    }

    Fiat getPriceAsFiat() {
        return priceAsFiat.get();
    }

    ObjectProperty<Fiat> priceAsFiatProperty() {
        return priceAsFiat;
    }

    Fiat getVolumeAsFiat() {
        return volumeAsFiat.get();
    }

    ObjectProperty<Fiat> volumeAsFiatProperty() {
        return volumeAsFiat;
    }

    OrderBookInfo getOrderBookInfo() {
        return orderBookInfo;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setBankAccount(BankAccount bankAccount) {
        if (bankAccount != null) {
            fiatCode.set(bankAccount.getCurrency().getCurrencyCode());
            bankAccountCountry.set(bankAccount.getCountry());
            sortedItems.stream().forEach(e -> e.setBankAccountCountry(bankAccount.getCountry()));
        }
        else {
            fiatCode.set(CurrencyUtil.getDefaultCurrency().getCurrencyCode());
        }
    }

    private boolean languagesInList(List<Locale> list1, List<Locale> list2) {
        for (Locale locale1 : list2) {
            for (Locale locale2 : list1) {
                if (locale1.getLanguage().equals(locale2.getLanguage())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean arbitratorsInList(List<Arbitrator> list1, List<Arbitrator> list2) {
        for (Arbitrator arbitrator1 : list2) {
            for (Arbitrator arbitrator2 : list1) {
                if (arbitrator1.getId().equals(arbitrator2.getId())) {
                    return true;
                }
            }
        }
        return false;
    }


    void applyFilter() {
        filteredItems.setPredicate(orderBookListItem -> {
            Offer offer = orderBookListItem.getOffer();

            boolean directionResult = offer.getDirection() != orderBookInfo.getDirection();

            boolean amountResult = true;
            if (orderBookInfo.getAmount() != null && orderBookInfo.getAmount().isPositive())
                amountResult = orderBookInfo.getAmount().compareTo(offer.getAmount()) <= 0;

            boolean priceResult = true;
            if (orderBookInfo.getPrice() != null && orderBookInfo.getPrice().isPositive()) {
                if (offer.getDirection() == Direction.SELL)
                    priceResult = orderBookInfo.getPrice().compareTo(offer.getPrice()) >= 0;
                else
                    priceResult = orderBookInfo.getPrice().compareTo(offer.getPrice()) <= 0;
            }


            //TODO

            return directionResult && amountResult && priceResult;

        });
    }

}
