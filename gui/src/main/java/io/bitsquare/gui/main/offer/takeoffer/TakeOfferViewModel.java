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

package io.bitsquare.gui.main.offer.takeoffer;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.deposit.DepositView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferViewModel extends ActivatableWithDataModel<TakeOfferDataModel> implements ViewModel {
    final TakeOfferDataModel dataModel;
    private final BtcValidator btcValidator;
    private final P2PService p2PService;
    private PriceFeedService priceFeedService;
    private final Navigation navigation;
    final BSFormatter formatter;

    private String amountRange;
    private String paymentLabel;
    private boolean takeOfferRequested;
    private Trade trade;
    private Offer offer;
    private String price;
    private String directionLabel;
    private String amountDescription;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty offerWarning = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty spinnerInfoText = new SimpleStringProperty("");

    final BooleanProperty isOfferAvailable = new SimpleBooleanProperty();
    final BooleanProperty isTakeOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty takeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    private ChangeListener<String> amountListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Boolean> isWalletFundedListener;
    private ChangeListener<Trade.State> tradeStateListener;
    private ChangeListener<String> tradeErrorListener;
    private ChangeListener<Offer.State> offerStateListener;
    private ChangeListener<String> offerErrorListener;
    private ConnectionListener connectionListener;
    //  private Subscription isFeeSufficientSubscription;
    private Runnable takeOfferSucceededHandler;
    String marketPriceMargin;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TakeOfferViewModel(TakeOfferDataModel dataModel, BtcValidator btcValidator, P2PService p2PService, PriceFeedService priceFeedService,
                              Navigation navigation, BSFormatter formatter) {
        super(dataModel);
        this.dataModel = dataModel;

        this.btcValidator = btcValidator;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;
        this.formatter = formatter;

        createListeners();
    }

    @Override
    protected void activate() {
        addBindings();
        addListeners();

        amount.set(formatter.formatCoin(dataModel.amountAsCoin.get()));
        showTransactionPublishedScreen.set(false);

        // when getting back to an open screen we want to re-check again
        isOfferAvailable.set(false);
        checkNotNull(offer, "offer must not be null");

        offer.stateProperty().addListener(offerStateListener);
        applyOfferState(offer.stateProperty().get());

        updateButtonDisableState();

        updateSpinnerInfo();
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

        if (offer.getDirection() == Offer.Direction.BUY) {
            directionLabel = BSResources.get("shared.sellBitcoin");
            amountDescription = BSResources.get("takeOffer.amountPriceBox.buy.amountDescription");
        } else {
            directionLabel = BSResources.get("shared.buyBitcoin");
            amountDescription = BSResources.get("takeOffer.amountPriceBox.sell.amountDescription");
        }

        amountRange = formatter.formatCoin(offer.getMinAmount()) + " - " + formatter.formatCoin(offer.getAmount());
        price = formatter.formatPrice(dataModel.tradePrice);
        marketPriceMargin = formatter.formatPercentagePrice(offer.getMarketPriceMargin());
        paymentLabel = BSResources.get("takeOffer.fundsBox.paymentLabel", offer.getShortId());

        checkNotNull(dataModel.getAddressEntry(), "dataModel.getAddressEntry() must not be null");

        offerErrorListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                errorMessage.set(newValue);
        };
        offer.errorMessageProperty().addListener(offerErrorListener);
        errorMessage.set(offer.errorMessageProperty().get());

        btcValidator.setMaxTradeLimitInBitcoin(offer.getAmount());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer(Runnable resultHandler) {
        takeOfferSucceededHandler = resultHandler;
        takeOfferRequested = true;
        showTransactionPublishedScreen.set(false);
        dataModel.onTakeOffer(trade -> {
            this.trade = trade;
            trade.stateProperty().addListener(tradeStateListener);
            applyTradeState(trade.getState());
            trade.errorMessageProperty().addListener(tradeErrorListener);
            applyTradeErrorMessage(trade.errorMessageProperty().get());
            takeOfferCompleted.set(true);
        });

        updateButtonDisableState();
        updateSpinnerInfo();
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        dataModel.onPaymentAccountSelected(paymentAccount);
        if (offer != null)
            btcValidator.setMaxTradeLimitInBitcoin(offer.getAmount());
    }

    public void onShowPayFundsScreen() {
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

    boolean fundFromSavingsWallet() {
        dataModel.fundFromSavingsWallet();
        if (dataModel.isWalletFunded.get()) {
            updateButtonDisableState();
            return true;
        } else {
            new Popup().warning("You don't have enough funds in your Bitsquare wallet.\n" +
                    "You need " + formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()) + " but you have only " +
                    formatter.formatCoinWithCode(dataModel.totalAvailableBalance) + " in your Bitsquare wallet.\n\n" +
                    "Please fund that trade from an external Bitcoin wallet or fund your Bitsquare " +
                    "wallet at \"Funds/Depost funds\".")
                    .actionButtonText("Go to \"Funds/Depost funds\"")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
            return false;
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!formatter.hasBtcValidDecimals(userInput));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input
                amount.set(formatter.formatCoin(dataModel.amountAsCoin.get()));

                calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));

                if (dataModel.isAmountLargerThanOfferAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountLargerThanOfferAmount")));

                if (dataModel.wouldCreateDustForOfferer())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountLargerThanOfferAmountMinusFee")));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyOfferState(Offer.State state) {
        log.debug("applyOfferState state = " + state);
        offerWarning.set(null);

        // We have 2 situations handled here: 
        // 1. when clicking take offer in the offerbook screen, we do the availability check
        // 2. Before actually taking the offer in the take offer screen, we check again the availability as some time might have passed in the meantime
        // So we use the takeOfferRequested flag to display different messages depending on the context.
        switch (state) {
            case UNDEFINED:
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
                    offerWarning.set("Take offer request failed because the offer is not available anymore. " +
                            "Maybe another trader has taken the offer in the meantime.");
                else
                    offerWarning.set("You cannot take that offer because the offer was already taken by another trader.");
                takeOfferRequested = false;
                break;
            case REMOVED:
                if (!takeOfferRequested)
                    offerWarning.set("You cannot take that offer because the offer has been removed in the meantime.");

                takeOfferRequested = false;
                break;
            case OFFERER_OFFLINE:
                if (takeOfferRequested)
                    offerWarning.set("Take offer request failed because offerer is not online anymore.");
                else
                    offerWarning.set("You cannot take that offer because the offerer is offline.");
                takeOfferRequested = false;
                break;
            default:
                log.error("Unhandled offer state: " + state);
                break;
        }

        updateSpinnerInfo();

        updateButtonDisableState();
    }

    private void applyTradeErrorMessage(String errorMessage) {
        if (errorMessage != null) {
            String appendMsg = "";
            switch (trade.getState().getPhase()) {
                case PREPARATION:
                    appendMsg = "\n\nNo funds have left your wallet yet.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.";
                    break;
                case TAKER_FEE_PAID:
                    appendMsg = "\n\nThe trading fee is already paid. In the worst case you have lost that fee. " +
                            "We are sorry about that but keep in mind it is a very small amount.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.";
                    break;
                case DEPOSIT_PAID:
                case FIAT_SENT:
                case FIAT_RECEIVED:
                    appendMsg = "\n\nThe deposit transaction is already published.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.\n" +
                            "If the problem still remains please contact the developers for support.";
                    break;
                case PAYOUT_PAID:
                case WITHDRAWN:
                    appendMsg = "\n\nThe payout transaction is already published.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.\n" +
                            "If the problem still remains please contact the developers for support.";
                    break;
                case DISPUTE:
                    appendMsg = "\n\nThe trade is handled already by an arbitrator.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.\n" +
                            "If the problem still remains please contact the arbitrator or the developers for support.";
                    break;
            }
            this.errorMessage.set(errorMessage + appendMsg);

            updateSpinnerInfo();

            if (takeOfferSucceededHandler != null)
                takeOfferSucceededHandler.run();
        } else {
            this.errorMessage.set(null);
        }
    }

    private void applyTradeState(Trade.State tradeState) {
        log.debug("applyTradeState state = " + tradeState);

        if (trade.getState() == Trade.State.TAKER_PUBLISHED_DEPOSIT_TX
                || trade.getState() == Trade.State.DEPOSIT_SEEN_IN_NETWORK
                || trade.getState() == Trade.State.TAKER_SENT_DEPOSIT_TX_PUBLISHED_MSG
                || trade.getState() == Trade.State.OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG) {
            if (trade.getDepositTx() != null) {
                if (takeOfferSucceededHandler != null)
                    takeOfferSucceededHandler.run();

                showTransactionPublishedScreen.set(true);
                updateSpinnerInfo();
            } else {
                log.error("trade.getDepositTx() == null. That must not happen");
            }
        }
    }

    private void updateButtonDisableState() {
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid
                && dataModel.isMinAmountLessOrEqualAmount()
                && !dataModel.isAmountLargerThanOfferAmount()
                && isOfferAvailable.get()
                && !dataModel.wouldCreateDustForOfferer();
        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        // isTakeOfferButtonDisabled.set(takeOfferRequested || !inputDataValid || notSufficientFees);
        isTakeOfferButtonDisabled.set(takeOfferRequested || !inputDataValid || !dataModel.isWalletFunded.get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        volume.bind(createStringBinding(() -> formatter.formatVolume(dataModel.volumeAsFiat.get()), dataModel.volumeAsFiat));

        if (dataModel.getDirection() == Offer.Direction.SELL) {
            volumeDescriptionLabel.set(BSResources.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.getCurrencyCode()));
        } else {
            volumeDescriptionLabel.set(BSResources.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.getCurrencyCode()));
        }
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()), dataModel.totalToPayAsCoin));
        btcCode.bind(dataModel.btcCode);
    }


    private void removeBindings() {
        volumeDescriptionLabel.unbind();
        volume.unbind();
        totalToPay.unbind();
        btcCode.unbind();
    }

    private void createListeners() {
        amountListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                dataModel.calculateTotalToPay();
            }
            updateButtonDisableState();
        };
        amountAsCoinListener = (ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue));
        isWalletFundedListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };

        tradeStateListener = (ov, oldValue, newValue) -> applyTradeState(newValue);
        tradeErrorListener = (ov, oldValue, newValue) -> applyTradeErrorMessage(newValue);
        offerStateListener = (ov, oldValue, newValue) -> applyOfferState(newValue);
        connectionListener = new ConnectionListener() {
            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                if (connection.getPeersNodeAddressOptional().isPresent() &&
                        connection.getPeersNodeAddressOptional().get().equals(offer.getOffererNodeAddress())) {
                    offerWarning.set("You lost connection to the offerer.\n" +
                            "He might have gone offline or has closed the connection to you because of too " +
                            "many open connections.\n\n" +
                            "If you can still see his offer in the offerbook you can try to take the offer again.");
                    updateSpinnerInfo();
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

    private void updateSpinnerInfo() {
        if (!showPayFundsScreenDisplayed.get() ||
                offerWarning.get() != null ||
                errorMessage.get() != null ||
                showTransactionPublishedScreen.get()) {
            spinnerInfoText.set("");
        } else if (dataModel.isWalletFunded.get()) {
            spinnerInfoText.set("");
           /* if (dataModel.isFeeFromFundingTxSufficient.get()) {
                spinnerInfoText.set("");
            } else {
                spinnerInfoText.set("Check if funding tx miner fee is sufficient...");
            }*/
        } else {
            spinnerInfoText.set("Waiting for funds...");
        }

        isWaitingForFunds.set(!spinnerInfoText.get().isEmpty());
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener(amountAsCoinListener);

        dataModel.isWalletFunded.addListener(isWalletFundedListener);
        p2PService.getNetworkNode().addConnectionListener(connectionListener);
       /* isFeeSufficientSubscription = EasyBind.subscribe(dataModel.isFeeFromFundingTxSufficient, newValue -> {
            updateButtonDisableState();
            updateSpinnerInfo();
        });*/
    }

    private void removeListeners() {
        amount.removeListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.removeListener(amountAsCoinListener);

        dataModel.isWalletFunded.removeListener(isWalletFundedListener);
        if (offer != null) {
            offer.stateProperty().removeListener(offerStateListener);
            offer.errorMessageProperty().removeListener(offerErrorListener);
        }

        if (trade != null) {
            trade.stateProperty().removeListener(tradeStateListener);
            trade.errorMessageProperty().removeListener(tradeErrorListener);
        }
        p2PService.getNetworkNode().removeConnectionListener(connectionListener);
        //isFeeSufficientSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void calculateVolume() {
        setAmountToModel();
        dataModel.calculateVolume();
    }

    private void setAmountToModel() {
        dataModel.setAmount(formatter.parseToCoinWith4Decimals(amount.get()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    BSFormatter getFormatter() {
        return formatter;
    }

    boolean isSeller() {
        return dataModel.getDirection() == Offer.Direction.BUY;
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    public Offer getOffer() {
        return dataModel.getOffer();
    }

    public String getAmountRange() {
        return amountRange;
    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    public String getPrice() {
        return price;
    }

    public String getDirectionLabel() {
        return directionLabel;
    }

    public String getAmountDescription() {
        return amountDescription;
    }

    String getAmount() {
        return formatter.formatCoinWithCode(dataModel.amountAsCoin.get());
    }

    String getTakerFee() {
        return formatter.formatCoinWithCode(dataModel.getTakerFeeAsCoin());
    }

    String getNetworkFee() {
        return formatter.formatCoinWithCode(dataModel.getNetworkFeeAsCoin());
    }

    public String getSecurityDeposit() {
        return formatter.formatCoinWithCode(dataModel.getSecurityDepositAsCoin());
    }

    public PaymentMethod getPaymentMethod() {
        return dataModel.getPaymentMethod();
    }

    ObservableList<PaymentAccount> getPossiblePaymentAccounts() {
        return dataModel.getPossiblePaymentAccounts();
    }

    public List<Arbitrator> getArbitrators() {
        return dataModel.getArbitrators();
    }

    boolean hasAcceptedArbitrators() {
        return dataModel.hasAcceptedArbitrators();
    }

    public void resetOfferWarning() {
        offerWarning.set(null);
    }

    public Trade getTrade() {
        return trade;
    }

    public void resetErrorMessage() {
        offer.setErrorMessage(null);
    }
}
