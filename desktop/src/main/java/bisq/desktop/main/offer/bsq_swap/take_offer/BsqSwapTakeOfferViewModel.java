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

package bisq.desktop.main.offer.bsq_swap.take_offer;

import bisq.desktop.Navigation;
import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferViewModel;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferRestrictions;
import bisq.core.offer.OfferUtil;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;

import bisq.common.handlers.ErrorMessageHandler;

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

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.beans.binding.Bindings.createStringBinding;

@Slf4j
class BsqSwapTakeOfferViewModel extends BsqSwapOfferViewModel<BsqSwapTakeOfferDataModel> {
    private final BtcValidator btcValidator;
    private final P2PService p2PService;

    String amountRange;
    private boolean takeOfferRequested;
    BsqSwapTrade trade;
    Offer offer;
    String price;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty offerWarning = new SimpleStringProperty();

    final BooleanProperty isOfferAvailable = new SimpleBooleanProperty();
    final BooleanProperty isTakeOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty isTradeComplete = new SimpleBooleanProperty();
    final BooleanProperty takeOfferCompleted = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    private ChangeListener<String> amountListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<BsqSwapTrade.State> tradeStateListener;
    private ChangeListener<String> tradeErrorListener;
    private ChangeListener<Offer.State> offerStateListener;
    private ChangeListener<String> offerErrorListener;
    private ConnectionListener connectionListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    BsqSwapTakeOfferViewModel(BsqSwapTakeOfferDataModel dataModel,
                              OfferUtil offerUtil,
                              BtcValidator btcValidator,
                              P2PService p2PService,
                              AccountAgeWitnessService accountAgeWitnessService,
                              Navigation navigation,
                              @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                              BsqFormatter bsqFormatter) {
        super(dataModel, btcFormatter, bsqFormatter, accountAgeWitnessService);
        this.btcValidator = btcValidator;
        this.p2PService = p2PService;

        createListeners();
    }

    @Override
    protected void activate() {
        addBindings();
        addListeners();

        amount.set(btcFormatter.formatCoin(dataModel.getBtcAmount().get()));
        isTradeComplete.set(false);

        // when getting back to an open screen we want to re-check again
        isOfferAvailable.set(false);
        checkNotNull(offer, "offer must not be null");

        offer.stateProperty().addListener(offerStateListener);
        applyOfferState(offer.stateProperty().get());

        updateButtonDisableState();
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

        amountRange = btcFormatter.formatCoin(offer.getMinAmount()) + " - " + btcFormatter.formatCoin(offer.getAmount());
        price = FormattingUtils.formatPrice(dataModel.getPrice().get());

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
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer(Runnable resultHandler,
                     ErrorMessageHandler warningHandler,
                     ErrorMessageHandler errorHandler) {
        takeOfferRequested = true;
        isTradeComplete.set(false);
        dataModel.onTakeOffer(trade -> {
                    this.trade = trade;
                    trade.stateProperty().addListener(tradeStateListener);
                    onTradeState(trade.getState());
                    trade.errorMessageProperty().addListener(tradeErrorListener);
                    applyTradeErrorMessage(trade.getErrorMessage());
                    takeOfferCompleted.set(true);
                    resultHandler.run();
                },
                warningHandler,
                errorHandler);

        updateButtonDisableState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Focus handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!DisplayUtils.hasBtcValidDecimals(userInput, btcFormatter));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input
                amount.set(btcFormatter.formatCoin(dataModel.getBtcAmount().get()));

                calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            Res.get("takeOffer.validation.amountSmallerThanMinAmount")));

                if (dataModel.isAmountLargerThanOfferAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            Res.get("takeOffer.validation.amountLargerThanOfferAmount")));
            } else if (btcValidator.getMaxTradeLimit() != null && btcValidator.getMaxTradeLimit().value == OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value) {
                if (dataModel.isBuyOffer()) {
                    new Popup().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.seller",
                            btcFormatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT),
                            Res.get("offerbook.warning.newVersionAnnouncement")))
                            .width(900)
                            .show();
                } else {
                    new Popup().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.buyer",
                            btcFormatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT),
                            Res.get("offerbook.warning.newVersionAnnouncement")))
                            .width(900)
                            .show();
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createListeners() {
        amountListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                dataModel.calculateInputAndPayout();
            }
            updateButtonDisableState();
        };
        amountAsCoinListener = (ov, oldValue, newValue) -> {
            amount.set(btcFormatter.formatCoin(newValue));
        };

        tradeStateListener = (ov, oldValue, newValue) -> onTradeState(newValue);
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

    @Override
    protected void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getBtcAmount().addListener(amountAsCoinListener);

        p2PService.getNetworkNode().addConnectionListener(connectionListener);
    }

    @Override
    protected void removeListeners() {
        amount.removeListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getBtcAmount().removeListener(amountAsCoinListener);

        if (offer != null) {
            offer.stateProperty().removeListener(offerStateListener);
            offer.errorMessageProperty().removeListener(offerErrorListener);
        }

        if (trade != null) {
            trade.stateProperty().removeListener(tradeStateListener);
            trade.errorMessageProperty().removeListener(tradeErrorListener);
        }
        p2PService.getNetworkNode().removeConnectionListener(connectionListener);
    }

    @Override
    protected void addBindings() {
        super.addBindings();

        volume.bind(createStringBinding(() -> VolumeUtil.formatVolume(dataModel.getVolume().get()), dataModel.getVolume()));
    }

    @Override
    protected void removeBindings() {
        super.removeBindings();

        volume.unbind();
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
                break;
            case OFFER_FEE_PAID:
                // irrelevant for taker
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
                break;
            case REMOVED:
                if (!takeOfferRequested)
                    offerWarning.set(Res.get("takeOffer.failed.offerRemoved"));

                break;
            case MAKER_OFFLINE:
                if (takeOfferRequested)
                    offerWarning.set(Res.get("takeOffer.failed.offererNotOnline"));
                else
                    offerWarning.set(Res.get("takeOffer.failed.offererOffline"));
                break;
            default:
                log.error("Unhandled offer state: " + state);
                break;
        }

        updateButtonDisableState();
    }

    void resetOfferWarning() {
        offerWarning.set(null);
    }

    private void applyTradeErrorMessage(@Nullable String errorMessage) {
        this.errorMessage.set(errorMessage);
        if (errorMessage == null) {
            return;
        }
        log.warn(errorMessage);
        trade.setState(BsqSwapTrade.State.FAILED);
    }

    private void onTradeState(BsqSwapTrade.State state) {
        switch (state) {
            case PREPARATION:
                break;
            case COMPLETED:
                isTradeComplete.set(trade.isCompleted());
                break;
            case FAILED:
                break;
        }
    }

    private void updateButtonDisableState() {
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid
                && dataModel.isMinAmountLessOrEqualAmount()
                && !dataModel.isAmountLargerThanOfferAmount()
                && isOfferAvailable.get();
        isNextButtonDisabled.set(!inputDataValid);
        cancelButtonDisabled.set(takeOfferRequested);
        isTakeOfferButtonDisabled.set(takeOfferRequested || !inputDataValid);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void calculateVolume() {
        setAmountToModel();
    }

    private void setAmountToModel() {
        if (amount.get() != null && !amount.get().isEmpty()) {
            Coin amount = DisplayUtils.parseToCoinWith4Decimals(this.amount.get(), btcFormatter);
            dataModel.applyAmount(amount);
        }
    }

    public void resetErrorMessage() {
        offer.setErrorMessage(null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    public boolean isRange() {
        return dataModel.getOffer().isRange();
    }

    public String getTradeFee() {
        return bsqFormatter.formatCoinWithCode(dataModel.getTradeFee());
    }
}
