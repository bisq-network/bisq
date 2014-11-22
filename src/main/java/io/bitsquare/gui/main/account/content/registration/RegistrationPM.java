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

package io.bitsquare.gui.main.account.content.registration;

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


class RegistrationPM extends PresentationModel<RegistrationModel> {

    final BooleanProperty isPayButtonDisabled = new SimpleBooleanProperty(true);
    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isPaymentSpinnerVisible = new SimpleBooleanProperty(false);

    // That is needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();
    private final BSFormatter formatter;


    @Inject
    public RegistrationPM(RegistrationModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;

        if (model.getAddressEntry() != null) {
            address.set(model.getAddressEntry().getAddress());
        }

        model.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                validateInput();
        });
        validateInput();

        model.payFeeSuccess.addListener((ov, oldValue, newValue) -> {
            isPayButtonDisabled.set(newValue);
            showTransactionPublishedScreen.set(newValue);
            isPaymentSpinnerVisible.set(false);
        });

        model.payFeeErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                requestPlaceOfferErrorMessage.set(newValue);
                isPaymentSpinnerVisible.set(false);
            }
        });
    }

    void payFee() {
        model.payFeeErrorMessage.set(null);
        model.payFeeSuccess.set(false);

        isPayButtonDisabled.set(true);
        isPaymentSpinnerVisible.set(true);

        model.payFee();
    }


    WalletService getWalletService() {
        return model.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    Coin getFeeAsCoin() {
        return model.getFeeAsCoin();
    }

    String getAddressAsString() {
        return model.getAddressEntry() != null ? model.getAddressEntry().getAddress().toString() : "";
    }

    String getPaymentLabel() {
        return BSResources.get("Bitsquare account registration fee");
    }

    String getFeeAsString() {
        return formatter.formatCoinWithCode(model.getFeeAsCoin());
    }

    String getTransactionId() {
        return model.getTransactionId();
    }


    private void validateInput() {
        isPayButtonDisabled.set(!(model.isWalletFunded.get()));
    }


}
