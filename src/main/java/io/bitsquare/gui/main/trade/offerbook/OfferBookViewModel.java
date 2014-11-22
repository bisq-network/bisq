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

package io.bitsquare.gui.main.trade.offerbook;

import io.bitsquare.gui.ActivatableWithDelegate;
import io.bitsquare.gui.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.OptionalBtcValidator;
import io.bitsquare.gui.util.validation.OptionalFiatValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.transformation.SortedList;

class OfferBookViewModel extends ActivatableWithDelegate<OfferBookModel> implements ViewModel {

    private final OptionalBtcValidator optionalBtcValidator;
    private final BSFormatter formatter;
    private final OptionalFiatValidator optionalFiatValidator;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty restrictionsInfo = new SimpleStringProperty();


    @Inject
    public OfferBookViewModel(OfferBookModel delegate, OptionalFiatValidator optionalFiatValidator,
                              OptionalBtcValidator optionalBtcValidator, BSFormatter formatter) {
        super(delegate);

        this.optionalFiatValidator = optionalFiatValidator;
        this.optionalBtcValidator = optionalBtcValidator;
        this.formatter = formatter;

        btcCode.bind(delegate.btcCode);
        fiatCode.bind(delegate.fiatCode);
        restrictionsInfo.bind(delegate.restrictionsInfo);

        // Bidirectional bindings are used for all input fields: amount, price and volume
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                setPriceToModel();
                delegate.calculateVolume();
            }
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setAmountToModel();
                setPriceToModel();
                delegate.calculateVolume();
            }
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setPriceToModel();
                setVolumeToModel();
                delegate.calculateAmount();
            }
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        delegate.amountAsCoinProperty().addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin
                (newValue)));
        delegate.priceAsFiatProperty().addListener((ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue)));
        delegate.volumeAsFiatProperty().addListener((ov, oldValue, newValue) -> volume.set(formatter.formatFiat
                (newValue)));
    }

    void removeOffer(Offer offer) {
        delegate.removeOffer(offer);
    }

    boolean isTradable(Offer offer) {
        return delegate.isTradable(offer);
    }


    void setDirection(Direction direction) {
        delegate.setDirection(direction);
    }


    SortedList<OfferBookListItem> getOfferList() {
        return delegate.getOfferList();
    }

    boolean isRegistered() {
        return delegate.isRegistered();
    }

    boolean isMyOffer(Offer offer) {
        return delegate.isMyOffer(offer);
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

    String getBankAccountType(OfferBookListItem item) {
        return (item != null) ? BSResources.get(item.getOffer().getBankAccountType().toString()) : "";
    }

    String getDirectionLabel(Offer offer) {
        return formatter.formatDirection(offer.getMirroredDirection());
    }

    Direction getDirection() {
        return delegate.getDirection();
    }

    Coin getAmountAsCoin() {
        return delegate.getAmountAsCoin();
    }

    Fiat getPriceAsCoin() {
        return delegate.getPriceAsFiat();
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return optionalBtcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {
        return optionalFiatValidator.validate(input);
    }

    private void setAmountToModel() {
        delegate.setAmount(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void setPriceToModel() {
        delegate.setPrice(formatter.parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        delegate.setVolume(formatter.parseToFiatWith2Decimals(volume.get()));
    }

}
