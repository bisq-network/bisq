/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.offer.bsq_swap.edit_offer;

import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferViewModel;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OpenOffer;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.AltcoinValidator;
import bisq.core.util.validation.InputValidator;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.offer.bsq_swap.BsqSwapOfferModel.BSQ;

@Slf4j
class BsqSwapEditOfferViewModel extends BsqSwapOfferViewModel<BsqSwapEditOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final AltcoinValidator altcoinValidator;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();

    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();

    final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(false);
    final BooleanProperty isCancelButtonDisabled = new SimpleBooleanProperty(false);

    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    BsqSwapEditOfferViewModel(BsqSwapEditOfferDataModel dataModel,
                              AltcoinValidator altcoinValidator,
                              BtcValidator btcValidator,
                              AccountAgeWitnessService accountAgeWitnessService,
                              @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                              BsqFormatter bsqFormatter) {
        super(dataModel, btcFormatter, bsqFormatter, accountAgeWitnessService);

        this.altcoinValidator = altcoinValidator;
        this.btcValidator = btcValidator;
    }

    @Override
    protected void activate() {
        addBindings();
        addListeners();

        minAmount.set(btcFormatter.formatCoin(dataModel.getMinAmount().get()));
        amount.set(btcFormatter.formatCoin(dataModel.getBtcAmount().get()));
        price.set(FormattingUtils.formatPrice(dataModel.getPrice().get()));
        volume.set(VolumeUtil.formatVolume(dataModel.getVolume().get()));

        isNextButtonDisabled.set(false);
        isCancelButtonDisabled.set(false);
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyOpenOffer(OpenOffer openOffer) {
        dataModel.applyOpenOffer(openOffer);

        btcValidator.setMaxValue(PaymentMethod.BSQ_SWAP.getMaxTradeLimitAsCoin(BSQ));
        btcValidator.setMaxTradeLimit(Coin.valueOf(dataModel.getMaxTradeLimit()));
        btcValidator.setMinValue(Restrictions.getMinTradeAmount());
    }

    public void onStartEditOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onStartEditOffer(resultHandler, errorMessageHandler);
    }

    public void onCancelEditOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onCancelEditOffer(resultHandler, errorMessageHandler);
    }

    public void onPublishOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onPublishOffer(resultHandler, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onFocusOutPriceTextField() {
        InputValidator.ValidationResult result = altcoinValidator.validate(price.get());
        priceValidationResult.set(result);
        isNextButtonDisabled.set(!result.isValid);

        if (result.isValid) {
            dataModel.setPrice(Price.parse(BSQ, price.get()));
            dataModel.calculateVolume();
            dataModel.calculateInputAndPayout();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createListeners() {
        priceListener = (ov, oldValue, newValue) -> {
            price.set(newValue != null ? FormattingUtils.formatPrice(newValue) : "");
        };
        volumeListener = (ov, oldValue, newValue) -> {
            volume.set(VolumeUtil.formatVolume(newValue));
        };
    }

    @Override
    protected void addListeners() {
        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getPrice().addListener(priceListener);
        dataModel.getVolume().addListener(volumeListener);
    }

    @Override
    protected void removeListeners() {
        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getPrice().removeListener(priceListener);
        dataModel.getVolume().removeListener(volumeListener);
    }
}
