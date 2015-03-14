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
import io.bitsquare.viewfx.model.ViewModel;
import io.bitsquare.viewfx.model.WithDataModel;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


class RegistrationViewModel extends WithDataModel<RegistrationDataModel> implements ViewModel {

    final BooleanProperty isPayButtonDisabled = new SimpleBooleanProperty(true);
    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isPaymentSpinnerVisible = new SimpleBooleanProperty(false);

    // That is needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();
    private final BSFormatter formatter;


    @Inject
    public RegistrationViewModel(RegistrationDataModel dataModel, BSFormatter formatter) {
        super(dataModel);
        this.formatter = formatter;

        if (dataModel.getAddressEntry() != null) {
            address.set(dataModel.getAddressEntry().getAddress());
        }

        dataModel.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                validateInput();
        });
        validateInput();

        dataModel.payFeeSuccess.addListener((ov, oldValue, newValue) -> {
            isPayButtonDisabled.set(newValue);
            showTransactionPublishedScreen.set(newValue);
            isPaymentSpinnerVisible.set(false);
        });

        dataModel.payFeeErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                requestPlaceOfferErrorMessage.set(newValue);
                isPaymentSpinnerVisible.set(false);
            }
        });
    }

    void payFee() {
        dataModel.payFeeErrorMessage.set(null);
        dataModel.payFeeSuccess.set(false);

        isPayButtonDisabled.set(true);
        isPaymentSpinnerVisible.set(true);

        dataModel.payFee();
    }


    WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    Coin getFeeAsCoin() {
        return dataModel.getFeeAsCoin();
    }

    String getAddressAsString() {
        return dataModel.getAddressEntry() != null ? dataModel.getAddressEntry().getAddress().toString() : "";
    }

    String getPaymentLabel() {
        return BSResources.get("Bitsquare account registration fee");
    }

    String getFeeAsString() {
        return formatter.formatCoinWithCode(dataModel.getFeeAsCoin());
    }

    String getTransactionId() {
        return dataModel.getTransactionId();
    }


    private void validateInput() {
        isPayButtonDisabled.set(!(dataModel.isWalletFunded.get()));
    }


}
