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
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import viewfx.model.ViewModel;
import viewfx.model.support.WithDelegate;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


class RegistrationViewModel extends WithDelegate<RegistrationDataModel> implements ViewModel {

    final BooleanProperty isPayButtonDisabled = new SimpleBooleanProperty(true);
    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isPaymentSpinnerVisible = new SimpleBooleanProperty(false);

    // That is needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();
    private final BSFormatter formatter;


    @Inject
    public RegistrationViewModel(RegistrationDataModel delegate, BSFormatter formatter) {
        super(delegate);
        this.formatter = formatter;

        if (delegate.getAddressEntry() != null) {
            address.set(delegate.getAddressEntry().getAddress());
        }

        delegate.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                validateInput();
        });
        validateInput();

        delegate.payFeeSuccess.addListener((ov, oldValue, newValue) -> {
            isPayButtonDisabled.set(newValue);
            showTransactionPublishedScreen.set(newValue);
            isPaymentSpinnerVisible.set(false);
        });

        delegate.payFeeErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                requestPlaceOfferErrorMessage.set(newValue);
                isPaymentSpinnerVisible.set(false);
            }
        });
    }

    void payFee() {
        delegate.payFeeErrorMessage.set(null);
        delegate.payFeeSuccess.set(false);

        isPayButtonDisabled.set(true);
        isPaymentSpinnerVisible.set(true);

        delegate.payFee();
    }


    WalletService getWalletService() {
        return delegate.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    Coin getFeeAsCoin() {
        return delegate.getFeeAsCoin();
    }

    String getAddressAsString() {
        return delegate.getAddressEntry() != null ? delegate.getAddressEntry().getAddress().toString() : "";
    }

    String getPaymentLabel() {
        return BSResources.get("Bitsquare account registration fee");
    }

    String getFeeAsString() {
        return formatter.formatCoinWithCode(delegate.getFeeAsCoin());
    }

    String getTransactionId() {
        return delegate.getTransactionId();
    }


    private void validateInput() {
        isPayButtonDisabled.set(!(delegate.isWalletFunded.get()));
    }


}
