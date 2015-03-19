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

import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.gui.components.Popups;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OfferBookViewModel extends ActivatableWithDataModel<OfferBookDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(OfferBookViewModel.class);

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
    public OfferBookViewModel(OfferBookDataModel dataModel, OptionalFiatValidator optionalFiatValidator,
                              OptionalBtcValidator optionalBtcValidator, BSFormatter formatter) {
        super(dataModel);

        this.optionalFiatValidator = optionalFiatValidator;
        this.optionalBtcValidator = optionalBtcValidator;
        this.formatter = formatter;

        btcCode.bind(dataModel.btcCode);
        fiatCode.bind(dataModel.fiatCode);
        restrictionsInfo.bind(dataModel.restrictionsInfo);

        // Bidirectional bindings are used for all input fields: amount, price and volume
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();
            }
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();
            }
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setPriceToModel();
                setVolumeToModel();
                dataModel.calculateAmount();
            }
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoinProperty().addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin
                (newValue)));
        dataModel.priceAsFiatProperty().addListener((ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue)));
        dataModel.volumeAsFiatProperty().addListener((ov, oldValue, newValue) -> volume.set(formatter.formatFiat
                (newValue)));
    }

    void removeOpenOffer(Offer offer) {
        dataModel.removeOpenOffer(offer,
                () -> {
                    // visual feedback?
                    log.debug("Remove offer was successful");
                },
                (message) -> {
                    log.error(message);
                    Popups.openWarningPopup("Remove offer failed", message);
                }
        );
    }

    boolean isTradable(Offer offer) {
        return dataModel.isTradable(offer);
    }


    void setDirection(Direction direction) {
        dataModel.setDirection(direction);
    }


    SortedList<OfferBookListItem> getOfferList() {
        return dataModel.getOfferList();
    }

    boolean isRegistered() {
        return dataModel.isRegistered();
    }

    boolean isMyOffer(Offer offer) {
        return dataModel.isMyOffer(offer);
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
        return (item != null) ? BSResources.get(item.getOffer().getFiatAccountType().toString()) : "";
    }

    String getDirectionLabel(Offer offer) {
        return formatter.formatDirection(offer.getMirroredDirection());
    }

    Direction getDirection() {
        return dataModel.getDirection();
    }

    Coin getAmountAsCoin() {
        return dataModel.getAmountAsCoin();
    }

    Fiat getPriceAsCoin() {
        return dataModel.getPriceAsFiat();
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return optionalBtcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {
        return optionalFiatValidator.validate(input);
    }

    private void setAmountToModel() {
        dataModel.setAmount(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void setPriceToModel() {
        dataModel.setPrice(formatter.parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        dataModel.setVolume(formatter.parseToFiatWith2Decimals(volume.get()));
    }

}
