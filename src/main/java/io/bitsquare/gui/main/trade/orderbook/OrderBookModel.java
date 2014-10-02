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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.UIModel;
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * It holds the scope specific domain data for either a buy or sell UI screen.
 */
class OrderBookModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(OrderBookModel.class);

    private final User user;
    private final OrderBook orderBook;
    private final Settings settings;
    private BSFormatter formatter;
    private final TradeManager tradeManager;

    private final FilteredList<OrderBookListItem> filteredItems;
    private final SortedList<OrderBookListItem> sortedItems;
    // private OrderBookInfo orderBookInfo;
    private ChangeListener<BankAccount> bankAccountChangeListener;

    private final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    private final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    private final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();

    final StringProperty restrictionsInfo = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final ObjectProperty<Country> bankAccountCountry = new SimpleObjectProperty<>();
    final ObjectProperty<Comparator<OrderBookListItem>> comparator = new SimpleObjectProperty<>();
    private Direction direction;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OrderBookModel(User user,
                   TradeManager tradeManager,
                   OrderBook orderBook,
                   Settings settings,
                   BSFormatter formatter) {
        this.tradeManager = tradeManager;
        this.user = user;
        this.orderBook = orderBook;
        this.settings = settings;
        this.formatter = formatter;

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
                amountAsCoin.set(formatter.reduceTo4Decimals(new ExchangeRate(priceAsFiat.get()).fiatToCoin
                        (volumeAsFiat.get())));
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
                    formatter.countryLocalesToString(offer.getAcceptedCountries()) +
                    "\n\nThe country of your payments account (" + user.getCurrentBankAccount().getCountry().getName() +
                    ") is not included in that list." +
                    "\n\n Do you want to edit your preferences now?");

        // TODO Not so clear how the restrictions will be handled
        // we might get rid of languages (handles viy arbitrators)
        /*
        // disjoint returns true if the two specified collections have no elements in common.
        boolean languageResult = !Collections.disjoint(settings.getAcceptedLanguageLocales(),
                offer.getAcceptedLanguageLocales());
        if (!languageResult)
            restrictionsInfo.set("This offer requires that the payments account resides in one of those languages:\n" +
                    BSFormatter.languageLocalesToString(offer.getAcceptedLanguageLocales()) +
                    "\n\nThe country of your payments account (" + user.getCurrentBankAccount().getCountry().getName() +
                    ") is not included in that list.");

        boolean arbitratorResult = !Collections.disjoint(settings.getAcceptedArbitrators(),
                offer.getArbitrators());*/

        return countryResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setDirection(Direction direction) {
        this.direction = direction;
    }

    void setAmount(Coin amount) {
        amountAsCoin.set(amount);
        applyFilter();
    }

    void setPrice(Fiat price) {
        priceAsFiat.set(price);
        applyFilter();
    }

    void setVolume(Fiat volume) {
        volumeAsFiat.set(volume);
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
        return offer.getMessagePublicKey() != null && offer.getMessagePublicKey().equals(user.getMessagePublicKey());
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

    Direction getDirection() {
        return direction;
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

    void applyFilter() {
        filteredItems.setPredicate(orderBookListItem -> {
            Offer offer = orderBookListItem.getOffer();

            boolean directionResult = offer.getDirection() != direction;

            boolean amountResult = true;
            if (amountAsCoin.get() != null && amountAsCoin.get().isPositive())
                amountResult = amountAsCoin.get().compareTo(offer.getAmount()) <= 0 &&
                        amountAsCoin.get().compareTo(offer.getMinAmount()) >= 0;

            boolean priceResult = true;
            if (priceAsFiat.get() != null && priceAsFiat.get().isPositive()) {
                if (offer.getDirection() == Direction.SELL)
                    priceResult = priceAsFiat.get().compareTo(offer.getPrice()) >= 0;
                else
                    priceResult = priceAsFiat.get().compareTo(offer.getPrice()) <= 0;
            }

            return directionResult && amountResult && priceResult;
        });
    }

}
