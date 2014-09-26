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

import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.OptionalBtcValidator;
import io.bitsquare.gui.util.validation.OptionalFiatValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

import com.google.inject.Inject;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.transformation.SortedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OrderBookPM extends PresentationModel<OrderBookModel> {
    private static final Logger log = LoggerFactory.getLogger(OrderBookPM.class);

    private final OptionalBtcValidator optionalBtcValidator;
    private BSFormatter formatter;
    private final OptionalFiatValidator optionalFiatValidator;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty restrictionsInfo = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OrderBookPM(OrderBookModel model,
                OptionalFiatValidator optionalFiatValidator,
                OptionalBtcValidator optionalBtcValidator,
                BSFormatter formatter) {
        super(model);

        this.optionalFiatValidator = optionalFiatValidator;
        this.optionalBtcValidator = optionalBtcValidator;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        super.initialize();

        btcCode.bind(model.btcCode);
        fiatCode.bind(model.fiatCode);
        restrictionsInfo.bind(model.restrictionsInfo);

        // Bidirectional bindings are used for all input fields: amount, price and volume 
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                setPriceToModel();
                model.calculateVolume();
            }
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setAmountToModel();
                setPriceToModel();
                model.calculateVolume();
            }
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setPriceToModel();
                setVolumeToModel();
                model.calculateAmount();
            }
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        model.amountAsCoinProperty().addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin
                (newValue)));
        model.priceAsFiatProperty().addListener((ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue)));
        model.volumeAsFiatProperty().addListener((ov, oldValue, newValue) -> volume.set(formatter.formatFiat
                (newValue)));
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
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
        model.removeOffer(offer);
    }

    boolean isTradable(Offer offer) {
        return model.isTradable(offer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setDirection(Direction direction) {
        model.setDirection(direction);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    SortedList<OrderBookListItem> getOfferList() {
        return model.getOfferList();
    }

    StringProperty getAmount() {
        return amount;
    }

    StringProperty getPrice() {
        return price;
    }

    StringProperty getVolume() {
        return volume;
    }

    boolean isRegistered() {
        return model.isRegistered();
    }

    boolean isMyOffer(Offer offer) {
        return model.isMyOffer(offer);
    }

    String getAmount(OrderBookListItem item) {
        return (item != null) ? formatter.formatCoin(item.getOffer().getAmount()) +
                " (" + formatter.formatCoin(item.getOffer().getMinAmount()) + ")" : "";
    }

    String getPrice(OrderBookListItem item) {
        return (item != null) ? formatter.formatFiat(item.getOffer().getPrice()) : "";
    }

    String getVolume(OrderBookListItem item) {
        return (item != null) ? formatter.formatFiat(item.getOffer().getOfferVolume()) +
                " (" + formatter.formatFiat(item.getOffer().getMinOfferVolume()) + ")" : "";
    }

    String getBankAccountType(OrderBookListItem item) {
        return (item != null) ? BSResources.get(item.getOffer().getBankAccountType().toString()) : "";
    }

    String getDirectionLabel(Offer offer) {
        return formatter.formatDirection(offer.getMirroredDirection());
    }

    Direction getDirection() {
        return model.getDirection();
    }

    Coin getAmountAsCoin() {
        return model.getAmountAsCoin();
    }

    Fiat getPriceAsCoin() {
        return model.getPriceAsFiat();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return optionalBtcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {
        return optionalFiatValidator.validate(input);
    }

    private void setAmountToModel() {
        model.setAmount(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void setPriceToModel() {
        model.setPrice(formatter.parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        model.setVolume(formatter.parseToFiatWith2Decimals(volume.get()));
    }

}
