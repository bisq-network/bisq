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

package bisq.desktop.main.offer.atomictakeoffer;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.offer.FeeUtil;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayloadI;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;

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

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.beans.binding.Bindings.createStringBinding;

class AtomicTakeOfferViewModel extends ActivatableWithDataModel<AtomicTakeOfferDataModel> implements ViewModel {
    final AtomicTakeOfferDataModel dataModel;
    private final OfferUtil offerUtil;
    private final BtcValidator btcValidator;
    private final P2PService p2PService;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;

    private String amountRange;
    private boolean takeOfferRequested;
    private AtomicTrade atomicTrade;
    private Offer offer;
    private String price;
    private String amountDescription;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty offerWarning = new SimpleStringProperty();
    final StringProperty tradeFee = new SimpleStringProperty();
    final StringProperty tradeFeeInBtcWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeInBsqWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeDescription = new SimpleStringProperty();
    final BooleanProperty isTradeFeeVisible = new SimpleBooleanProperty(false);

    final BooleanProperty isOfferAvailable = new SimpleBooleanProperty();
    final BooleanProperty isAtomicTakeOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty takeOfferCompleted = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    private ChangeListener<String> amountListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Boolean> isTxBuilderReadyListener;
    private ChangeListener<AtomicTrade.State> tradeStateListener;
    private ChangeListener<String> tradeErrorListener;
    private ChangeListener<Offer.State> offerStateListener;
    private ChangeListener<String> offerErrorListener;
    private ConnectionListener connectionListener;
    private Runnable takeOfferSucceededHandler;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AtomicTakeOfferViewModel(AtomicTakeOfferDataModel dataModel,
                                    OfferUtil offerUtil,
                                    BtcValidator btcValidator,
                                    P2PService p2PService,
                                    @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                    BsqFormatter bsqFormatter) {
        super(dataModel);
        this.dataModel = dataModel;
        this.offerUtil = offerUtil;
        this.btcValidator = btcValidator;
        this.p2PService = p2PService;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        createListeners();
    }

    @Override
    protected void activate() {
        addBindings();
        addListeners();

        amount.set(btcFormatter.formatCoin(dataModel.getAmount().get()));
        showTransactionPublishedScreen.set(false);

        // when getting back to an open screen we want to re-check again
        isOfferAvailable.set(false);
        checkNotNull(offer, "offer must not be null");

        offer.stateProperty().addListener(offerStateListener);
        applyOfferState(offer.stateProperty().get());

        updateButtonDisableState();

        if (!DevEnv.isDaoActivated()) {
            isTradeFeeVisible.setValue(false);
        }
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before doActivate
    void initWithData(Offer offer) {
        dataModel.initWithData(offer);
        this.offer = offer;

        amountDescription = offer.isBuyOffer()
                ? Res.get("takeOffer.amountPriceBox.buy.amountDescription")
                : Res.get("takeOffer.amountPriceBox.sell.amountDescription");

        amountRange = btcFormatter.formatCoin(offer.getMinAmount()) + " - " + btcFormatter.formatCoin(offer.getAmount());
        price = FormattingUtils.formatPrice(dataModel.tradePrice);

        offerErrorListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                errorMessage.set(newValue);
        };
        offer.errorMessageProperty().addListener(offerErrorListener);
        errorMessage.set(offer.getErrorMessage());

        btcValidator.setMaxValue(offer.getAmount());
        btcValidator.setMaxTradeLimit(Coin.valueOf(Math.min(dataModel.getMaxTradeLimit(), offer.getAmount().value)));
        btcValidator.setMinValue(offer.getMinAmount());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer(Runnable resultHandler) {
        takeOfferSucceededHandler = resultHandler;
        takeOfferRequested = true;
        showTransactionPublishedScreen.set(false);
        dataModel.onTakeOffer(atomicTrade -> {
            this.atomicTrade = atomicTrade;
            atomicTrade.stateProperty().addListener(tradeStateListener);
            applyTradeState();
            atomicTrade.errorMessageProperty().addListener(tradeErrorListener);
            applyTradeErrorMessage(atomicTrade.getErrorMessage());
            takeOfferCompleted.set(true);
        });

        updateButtonDisableState();
    }

    public void setIsCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
        dataModel.setPreferredCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc);
        applyTakerFee();
    }

    private void applyTakerFee() {
        tradeFeeDescription.set(Res.get("createOffer.tradeFee.descriptionBSQEnabled"));
        Coin takerFeeAsCoin = dataModel.getTakerFee();
        if (takerFeeAsCoin == null) {
            return;
        }

        isTradeFeeVisible.setValue(true);
        tradeFee.set(getFormatterForTakerFee().formatCoin(takerFeeAsCoin));
        tradeFeeInBtcWithFiat.set(FeeUtil.getTradeFeeWithFiatEquivalent(offerUtil,
                dataModel.getTakerFeeInBtc(),
                true,
                btcFormatter));
        tradeFeeInBsqWithFiat.set(FeeUtil.getTradeFeeWithFiatEquivalent(offerUtil,
                dataModel.getTakerFeeInBsq(),
                false,
                bsqFormatter));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue, String userInput) {
        if (!oldValue && newValue) {
            return;
        }

        InputValidator.ValidationResult result = isBtcInputValid(amount.get());
        amountValidationResult.set(result);
        if (result.isValid) {
            showWarningInvalidBtcDecimalPlaces.set(!DisplayUtils.hasBtcValidDecimals(userInput, btcFormatter));
            // only allow max 4 decimal places for btc values
            setAmountToModel();
            // reformat input
            amount.set(btcFormatter.formatCoin(dataModel.getAmount().get()));

            calculateVolume();

            if (!dataModel.isMinAmountLessOrEqualAmount())
                amountValidationResult.set(new InputValidator.ValidationResult(false,
                        Res.get("takeOffer.validation.amountSmallerThanMinAmount")));

            if (dataModel.isAmountLargerThanOfferAmount())
                amountValidationResult.set(new InputValidator.ValidationResult(false,
                        Res.get("takeOffer.validation.amountLargerThanOfferAmount")));

        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyOfferState(Offer.State state) {
        offerWarning.set(null);

        // We have 2 situations handled here:
        // 1. when clicking take offer in the offerbook screen, we do the availability check
        // 2. Before actually taking the offer in the take offer screen, we check again the availability as some time might have passed in the meantime
        // So we use the takeOfferRequested flag to display different network_messages depending on the context.
        switch (state) {
            case UNKNOWN:
            case OFFER_FEE_PAID:
                break;
            case AVAILABLE:
                isOfferAvailable.set(true);
                updateButtonDisableState();
                break;
            case NOT_AVAILABLE:
                if (takeOfferRequested)
                    offerWarning.set(Res.get("takeOffer.failed.offerNotAvailable"));
                else
                    offerWarning.set(Res.get("takeOffer.failed.offerTaken"));
                takeOfferRequested = false;
                break;
            case REMOVED:
                if (!takeOfferRequested)
                    offerWarning.set(Res.get("takeOffer.failed.offerRemoved"));

                takeOfferRequested = false;
                break;
            case MAKER_OFFLINE:
                if (takeOfferRequested)
                    offerWarning.set(Res.get("takeOffer.failed.offererNotOnline"));
                else
                    offerWarning.set(Res.get("takeOffer.failed.offererOffline"));
                takeOfferRequested = false;
                break;
            default:
                log.error("Unhandled offer state: " + state);
                break;
        }

        updateButtonDisableState();
    }

    private void applyTradeErrorMessage(@Nullable String errorMessage) {
        if (errorMessage != null) {
            String appendMsg;
            switch (atomicTrade.getState()) {
                case PREPARATION:
                    appendMsg = Res.get("takeOffer.error.noFundsLost");
                    break;
                case TX_PUBLISHED:
                case TX_CONFIRMED:
                    appendMsg = Res.get("takeOffer.error.payoutPublished");
                    break;
                default:
                    appendMsg = Res.get("shared.na");
            }
            this.errorMessage.set(errorMessage + appendMsg);

            if (takeOfferSucceededHandler != null)
                takeOfferSucceededHandler.run();
        } else {
            this.errorMessage.set(null);
        }
    }

    private void applyTradeState() {
        if (atomicTrade.getState().equals(AtomicTrade.State.TX_PUBLISHED) ||
                atomicTrade.getState().equals(AtomicTrade.State.TX_CONFIRMED)) {
            if (takeOfferSucceededHandler != null)
                takeOfferSucceededHandler.run();

            showTransactionPublishedScreen.set(true);
        }
    }

    private void updateButtonDisableState() {
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid
                && dataModel.isMinAmountLessOrEqualAmount()
                && !dataModel.isAmountLargerThanOfferAmount()
                && isOfferAvailable.get()
                && dataModel.isTxBuilderReady.get()
                && dataModel.hasEnoughBtc()
                && dataModel.hasEnoughBsq();
        isAtomicTakeOfferButtonDisabled.set(!inputDataValid);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        volume.bind(createStringBinding(() -> DisplayUtils.formatVolume(dataModel.volume.get()), dataModel.volume));

        if (dataModel.getDirection() == OfferPayloadI.Direction.SELL) {
            volumeDescriptionLabel.set(Res.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.getCurrencyCode()));
        } else {
            volumeDescriptionLabel.set(Res.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.getCurrencyCode()));
        }
        totalToPay.bind(createStringBinding(() -> btcFormatter.formatCoinWithCode(dataModel.getTotalToPayAsCoin().get()), dataModel.getTotalToPayAsCoin()));

    }


    private void removeBindings() {
        volumeDescriptionLabel.unbind();
        volume.unbind();
        totalToPay.unbind();
    }

    private void createListeners() {
        amountListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                applyTakerFee();
            }
            updateButtonDisableState();
        };
        amountAsCoinListener = (ov, oldValue, newValue) -> {
            amount.set(btcFormatter.formatCoin(newValue));
            applyTakerFee();
        };
        isTxBuilderReadyListener = (ov, oldValue, newValue) -> updateButtonDisableState();

        tradeStateListener = (ov, oldValue, newValue) -> applyTradeState();
        tradeErrorListener = (ov, oldValue, newValue) -> applyTradeErrorMessage(newValue);
        offerStateListener = (ov, oldValue, newValue) -> applyOfferState(newValue);
        connectionListener = new ConnectionListener() {
            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                if (connection.getPeersNodeAddressOptional().isPresent() &&
                        connection.getPeersNodeAddressOptional().get().equals(offer.getMakerNodeAddress())) {
                    offerWarning.set(Res.get("takeOffer.warning.connectionToPeerLost"));
                }
            }

            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().addListener(amountAsCoinListener);

        p2PService.getNetworkNode().addConnectionListener(connectionListener);
        dataModel.isTxBuilderReady.addListener(isTxBuilderReadyListener);
    }

    private void removeListeners() {
        amount.removeListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().removeListener(amountAsCoinListener);

        if (offer != null) {
            offer.stateProperty().removeListener(offerStateListener);
            offer.errorMessageProperty().removeListener(offerErrorListener);
        }

        if (atomicTrade != null) {
            atomicTrade.stateProperty().removeListener(tradeStateListener);
            atomicTrade.errorMessageProperty().removeListener(tradeErrorListener);
        }
        p2PService.getNetworkNode().removeConnectionListener(connectionListener);
        dataModel.isTxBuilderReady.removeListener(isTxBuilderReadyListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void calculateVolume() {
        setAmountToModel();
        dataModel.calculateVolume();
    }

    private void setAmountToModel() {
        if (amount.get() != null && !amount.get().isEmpty()) {
            Coin amount = DisplayUtils.parseToCoinWith4Decimals(this.amount.get(), btcFormatter);
            dataModel.applyAmount(amount);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    public Offer getOffer() {
        return dataModel.getOffer();
    }

    public boolean isRange() {
        return dataModel.getOffer().isRange();
    }

    public String getAmountRange() {
        return amountRange;
    }


    public String getPrice() {
        return price;
    }

    public String getAmountDescription() {
        return amountDescription;
    }

    PaymentAccount getPaymentAccount() {
        return dataModel.getPaymentAccount();
    }

    public void resetOfferWarning() {
        offerWarning.set(null);
    }

    public AtomicTrade getAtomicTrade() {
        return atomicTrade;
    }

    public void resetErrorMessage() {
        offer.setErrorMessage(null);
    }

    private CoinFormatter getFormatterForTakerFee() {
        return dataModel.isCurrencyForTakerFeeBtc() ? btcFormatter : bsqFormatter;
    }
}
