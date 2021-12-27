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

package bisq.desktop.main.offer.bsq_swap.create_offer;

import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferViewModel;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferRestrictions;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.AltcoinValidator;
import bisq.core.util.validation.InputValidator;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;

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

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.offer.bsq_swap.BsqSwapOfferModel.BSQ;

@Slf4j
class BsqSwapCreateOfferViewModel extends BsqSwapOfferViewModel<BsqSwapCreateOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final BsqValidator bsqValidator;

    private final AltcoinValidator altcoinValidator;

    private boolean createOfferRequested;
    public final StringProperty amount = new SimpleStringProperty();
    public final StringProperty minAmount = new SimpleStringProperty();
    public final StringProperty price = new SimpleStringProperty();
    final StringProperty tradeFee = new SimpleStringProperty();
    public final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();

    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    public final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty miningPoW = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    private ChangeListener<String> amountStringListener;
    private ChangeListener<String> minAmountStringListener;
    private ChangeListener<String> volumeStringListener;

    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;

    private ChangeListener<String> errorMessageListener;
    private Timer timeoutTimer;
    private boolean ignoreVolumeStringListener, ignoreAmountStringListener;
    private boolean syncMinAmountWithAmount = true;
    private Timer miningPowTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    BsqSwapCreateOfferViewModel(BsqSwapCreateOfferDataModel dataModel,
                                AltcoinValidator altcoinValidator,
                                BtcValidator btcValidator,
                                BsqValidator bsqValidator,
                                AccountAgeWitnessService accountAgeWitnessService,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                BsqFormatter bsqFormatter) {
        super(dataModel, btcFormatter, bsqFormatter, accountAgeWitnessService);

        this.altcoinValidator = altcoinValidator;
        this.btcValidator = btcValidator;
        this.bsqValidator = bsqValidator;
    }

    @Override
    protected void activate() {
        if (DevEnv.isDevMode()) {
            UserThread.runAfter(() -> {
                amount.set("0.001");
                price.set("0.00002");
                minAmount.set(amount.get());
                applyTradeFee();
                setAmountToModel();
                setMinAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();
                dataModel.calculateInputAndPayout();
                updateButtonDisableState();
            }, 100, TimeUnit.MILLISECONDS);
        }

        addBindings();
        addListeners();

        maybeInitializeWithData();

        updateButtonDisableState();
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
        stopTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(OfferDirection direction, @Nullable BsqSwapOfferPayload offerPayload) {
        dataModel.initWithData(direction, offerPayload);

        btcValidator.setMaxValue(PaymentMethod.BSQ_SWAP.getMaxTradeLimitAsCoin(BSQ));
        btcValidator.setMaxTradeLimit(Coin.valueOf(dataModel.getMaxTradeLimit()));
        btcValidator.setMinValue(Restrictions.getMinTradeAmount());

        applyTradeFee();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    void requestNewOffer() {
        // We delay display a bit as pow is mostly very low so it would show flicker quickly
        miningPowTimer = UserThread.runAfter(() -> {
            miningPoW.set(true);
            updateButtonDisableState();
        }, 200, TimeUnit.MILLISECONDS);


        dataModel.requestNewOffer(offer -> {
            if (miningPowTimer != null) {
                miningPowTimer.stop();
            }
            miningPoW.set(false);
            updateButtonDisableState();
        });
    }

    void onPlaceOffer() {
        errorMessage.set(null);
        createOfferRequested = true;

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                stopTimeoutTimer();
                createOfferRequested = false;
                errorMessage.set(Res.get("createOffer.timeoutAtPublishing"));

                updateButtonDisableState();
            }, 60);
        }
        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                stopTimeoutTimer();
                createOfferRequested = false;
                errorMessage.set(newValue);

                updateButtonDisableState();
            }
        };

        dataModel.offer.errorMessageProperty().addListener(errorMessageListener);

        dataModel.onPlaceOffer(() -> {
            stopTimeoutTimer();
            placeOfferCompleted.set(true);
            errorMessage.set(null);
        });

        updateButtonDisableState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                setAmountToModel();
                ignoreAmountStringListener = true;
                amount.set(btcFormatter.formatCoin(dataModel.getBtcAmount().get()));
                ignoreAmountStringListener = false;
                dataModel.calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    minAmount.set(amount.get());
                else
                    amountValidationResult.set(result);

                if (minAmount.get() != null)
                    minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
            } else if (amount.get() != null && btcValidator.getMaxTradeLimit() != null && btcValidator.getMaxTradeLimit().value == OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value) {
                amount.set(btcFormatter.formatCoin(btcValidator.getMaxTradeLimit()));
                new Popup().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.buyer",
                        btcFormatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT),
                        Res.get("offerbook.warning.newVersionAnnouncement")))
                        .width(900)
                        .show();
            }
            // We want to trigger a recalculation of the volume
            UserThread.execute(() -> {
                onFocusOutVolumeTextField(true, false);
                onFocusOutMinAmountTextField(true, false);
            });
        }
    }

    void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                Coin minAmountAsCoin = dataModel.getMinAmount().get();
                syncMinAmountWithAmount = minAmountAsCoin != null &&
                        minAmountAsCoin.equals(dataModel.getBtcAmount().get());
                setMinAmountToModel();

                dataModel.calculateMinVolume();

                if (dataModel.getMinVolume().get() != null) {
                    InputValidator.ValidationResult minVolumeResult = isVolumeInputValid(
                            VolumeUtil.formatVolume(dataModel.getMinVolume().get()));

                    volumeValidationResult.set(minVolumeResult);

                    updateButtonDisableState();
                }

                this.minAmount.set(btcFormatter.formatCoin(minAmountAsCoin));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    this.amount.set(this.minAmount.get());
                } else {
                    minAmountValidationResult.set(result);
                    if (this.amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(this.amount.get()));
                }
            } else {
                syncMinAmountWithAmount = true;
            }
        }
    }

    void onFocusOutPriceTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isPriceInputValid(price.get());
            priceValidationResult.set(result);
            if (result.isValid) {
                setPriceToModel();
                if (dataModel.getPrice().get() != null)
                    price.set(FormattingUtils.formatPrice(dataModel.getPrice().get()));
                dataModel.calculateVolume();
                dataModel.calculateAmount();
                applyTradeFee();
            }

            // We want to trigger a recalculation of the volume and minAmount
            UserThread.execute(() -> {
                onFocusOutVolumeTextField(true, false);
                triggerFocusOutOnAmountFields();
            });
        }
    }

    void triggerFocusOutOnAmountFields() {
        onFocusOutAmountTextField(true, false);
        onFocusOutMinAmountTextField(true, false);
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isVolumeInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                setVolumeToModel();
                ignoreVolumeStringListener = true;

                Volume volume = dataModel.getVolume().get();
                if (volume != null) {
                    this.volume.set(VolumeUtil.formatVolume(volume));
                }

                ignoreVolumeStringListener = false;

                dataModel.calculateAmount();

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    minAmount.set(amount.getValue());
                } else {
                    if (amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(amount.get()));

                    // We only check minAmountValidationResult if amountValidationResult is valid, otherwise we would get
                    // triggered a close of the popup when the minAmountValidationResult is applied
                    if (amountValidationResult.getValue() != null && amountValidationResult.getValue().isValid && minAmount.get() != null)
                        minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isPriceInputValid(String input) {
        return altcoinValidator.validate(input);
    }

    private InputValidator.ValidationResult isVolumeInputValid(String input) {
        return bsqValidator.validate(input);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyTradeFee() {
        tradeFee.set(getTradeFee());
    }

    private void setAmountToModel() {
        if (amount.get() != null && !amount.get().isEmpty()) {
            Coin amount = DisplayUtils.parseToCoinWith4Decimals(this.amount.get(), btcFormatter);
            dataModel.setBtcAmount(amount);
            if (syncMinAmountWithAmount ||
                    dataModel.getMinAmount().get() == null ||
                    dataModel.getMinAmount().get().equals(Coin.ZERO)) {
                minAmount.set(this.amount.get());
                setMinAmountToModel();
            }
        } else {
            dataModel.setBtcAmount(null);
        }
    }

    private void setMinAmountToModel() {
        if (minAmount.get() != null && !minAmount.get().isEmpty()) {
            Coin minAmount = DisplayUtils.parseToCoinWith4Decimals(this.minAmount.get(), btcFormatter);
            dataModel.setMinAmount(minAmount);
        } else {
            dataModel.setMinAmount(null);
        }
    }

    private void setPriceToModel() {
        if (price.get() != null && !price.get().isEmpty()) {
            try {
                dataModel.setPrice(Price.parse(BSQ, this.price.get()));
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.setPrice(null);
        }
    }

    private void setVolumeToModel() {
        if (volume.get() != null && !volume.get().isEmpty()) {
            try {
                dataModel.setVolume(Volume.parse(volume.get(), BSQ));
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.setVolume(null);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addBindings() {
        super.addBindings();

        volumePromptLabel.set(Res.get("createOffer.volume.prompt", BSQ));
    }

    @Override
    protected void removeBindings() {
        super.removeBindings();

        volumePromptLabel.unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createListeners() {
        amountStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreAmountStringListener) {
                if (isBtcInputValid(newValue).isValid) {
                    setAmountToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateInputAndPayout();
                }
                updateButtonDisableState();
            }
        };
        minAmountStringListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid)
                setMinAmountToModel();
            updateButtonDisableState();
        };
        volumeStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreVolumeStringListener) {
                if (isVolumeInputValid(newValue).isValid) {
                    setVolumeToModel();
                    setPriceToModel();
                    dataModel.calculateAmount();
                    dataModel.calculateInputAndPayout();
                }
                updateButtonDisableState();
            }
        };

        amountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                amount.set(btcFormatter.formatCoin(newValue));
            } else {
                amount.set("");
            }

            applyTradeFee();
        };
        minAmountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                minAmount.set(btcFormatter.formatCoin(newValue));
            else
                minAmount.set("");
        };
        priceListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                price.set(FormattingUtils.formatPrice(newValue));
            else
                price.set("");

            applyTradeFee();
        };
        volumeListener = (ov, oldValue, newValue) -> {
            ignoreVolumeStringListener = true;
            if (newValue != null)
                volume.set(VolumeUtil.formatVolume(newValue));
            else
                volume.set("");

            ignoreVolumeStringListener = false;
            applyTradeFee();
        };
    }

    @Override
    protected void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountStringListener);
        minAmount.addListener(minAmountStringListener);
        volume.addListener(volumeStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getBtcAmount().addListener(amountAsCoinListener);
        dataModel.getMinAmount().addListener(minAmountAsCoinListener);
        dataModel.getPrice().addListener(priceListener);
        dataModel.getVolume().addListener(volumeListener);
    }

    @Override
    protected void removeListeners() {
        amount.removeListener(amountStringListener);
        minAmount.removeListener(minAmountStringListener);
        volume.removeListener(volumeStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getBtcAmount().removeListener(amountAsCoinListener);
        dataModel.getMinAmount().removeListener(minAmountAsCoinListener);
        dataModel.getPrice().removeListener(priceListener);
        dataModel.getVolume().removeListener(volumeListener);

        if (dataModel.offer != null && errorMessageListener != null)
            dataModel.offer.getErrorMessageProperty().removeListener(errorMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateButtonDisableState() {
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid &&
                isBtcInputValid(minAmount.get()).isValid &&
                isPriceInputValid(price.get()).isValid &&
                dataModel.getPrice().get() != null &&
                dataModel.getPrice().get().getValue() != 0 &&
                isVolumeInputValid(volume.get()).isValid &&
                isVolumeInputValid(VolumeUtil.formatVolume(dataModel.getMinVolume().get())).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isNextButtonDisabled.set(!inputDataValid);
        cancelButtonDisabled.set(createOfferRequested);
        isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || miningPoW.get());
    }

    private void maybeInitializeWithData() {
        ObjectProperty<Coin> btcMinAmount = dataModel.getMinAmount();
        if (btcMinAmount.get() != null) {
            minAmountAsCoinListener.changed(btcMinAmount, null, btcMinAmount.get());
        }

        ObjectProperty<Coin> btcAmount = dataModel.getBtcAmount();

        if (btcAmount.get() != null && btcMinAmount.get() != null) {
            syncMinAmountWithAmount = btcMinAmount.get().equals(dataModel.getBtcAmount().get());
        }

        if (btcAmount.get() != null) {
            amountAsCoinListener.changed(btcAmount, null, btcAmount.get());
        }

        ObjectProperty<Price> price = dataModel.getPrice();
        if (price.get() != null) {
            priceListener.changed(price, null, price.get());
        }

        ObjectProperty<Volume> volume = dataModel.getVolume();
        if (volume.get() != null) {
            volumeListener.changed(volume, null, volume.get());
        }
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
