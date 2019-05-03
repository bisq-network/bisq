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

package bisq.desktop.main.offer.takeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.ObservableList;

import java.util.Optional;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferViewModel extends ActivatableWithDataModel<TakeOfferDataModel> implements ViewModel {
    final TakeOfferDataModel dataModel;
    private final BtcValidator btcValidator;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final Navigation navigation;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;

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
    final StringProperty spinnerInfoText = new SimpleStringProperty("");
    final StringProperty tradeFee = new SimpleStringProperty();
    final StringProperty tradeFeeInBtcWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeInBsqWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeDescription = new SimpleStringProperty();
    final BooleanProperty isTradeFeeVisible = new SimpleBooleanProperty(false);

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
    public TakeOfferViewModel(TakeOfferDataModel dataModel,
                              BtcValidator btcValidator,
                              P2PService p2PService,
                              WalletsSetup walletsSetup,
                              Preferences preferences,
                              PriceFeedService priceFeedService,
                              Navigation navigation,
                              BSFormatter btcFormatter,
                              BsqFormatter bsqFormatter) {
        super(dataModel);
        this.dataModel = dataModel;

        this.btcValidator = btcValidator;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;
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

        updateSpinnerInfo();

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

        if (offer.isBuyOffer()) {
            directionLabel = Res.get("shared.sellBitcoin");
            amountDescription = Res.get("takeOffer.amountPriceBox.buy.amountDescription");
        } else {
            directionLabel = Res.get("shared.buyBitcoin");
            amountDescription = Res.get("takeOffer.amountPriceBox.sell.amountDescription");
        }

        amountRange = btcFormatter.formatCoin(offer.getMinAmount()) + " - " + btcFormatter.formatCoin(offer.getAmount());
        price = btcFormatter.formatPrice(dataModel.tradePrice);
        marketPriceMargin = btcFormatter.formatToPercent(offer.getMarketPriceMargin());
        paymentLabel = Res.get("takeOffer.fundsBox.paymentLabel", offer.getShortId());

        checkNotNull(dataModel.getAddressEntry(), "dataModel.getAddressEntry() must not be null");

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
        dataModel.onTakeOffer(trade -> {
            this.trade = trade;
            trade.stateProperty().addListener(tradeStateListener);
            applyTradeState(trade.getState());
            trade.errorMessageProperty().addListener(tradeErrorListener);
            applyTradeErrorMessage(trade.getErrorMessage());
            takeOfferCompleted.set(true);
        });

        updateButtonDisableState();
        updateSpinnerInfo();
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        dataModel.onPaymentAccountSelected(paymentAccount);
        btcValidator.setMaxTradeLimit(Coin.valueOf(Math.min(dataModel.getMaxTradeLimit(), offer.getAmount().value)));
        updateButtonDisableState();
    }

    public void onShowPayFundsScreen() {
        dataModel.onShowPayFundsScreen();
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

    boolean fundFromSavingsWallet() {
        dataModel.fundFromSavingsWallet();
        if (dataModel.getIsBtcWalletFunded().get()) {
            updateButtonDisableState();
            return true;
        } else {
            new Popup<>().warning(Res.get("shared.notEnoughFunds",
                    btcFormatter.formatCoinWithCode(dataModel.getTotalToPayAsCoin().get()),
                    btcFormatter.formatCoinWithCode(dataModel.getTotalAvailableBalance())))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
            return false;
        }

    }

    public void setIsCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
        dataModel.setPreferredCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc);
        applyTakerFee();
    }

    private void applyTakerFee() {
        Coin takerFeeAsCoin = dataModel.getTakerFee();
        if (takerFeeAsCoin != null) {
            isTradeFeeVisible.setValue(true);

            tradeFee.set(getFormatterForTakerFee().formatCoin(takerFeeAsCoin));

            Coin makerFeeInBtc = dataModel.getTakerFeeInBtc();
            Optional<Volume> optionalBtcFeeInFiat = OfferUtil.getFeeInUserFiatCurrency(makerFeeInBtc,
                    true, preferences, priceFeedService, bsqFormatter);
            String btcFeeWithFiatAmount = OfferUtil.getFeeWithFiatAmount(makerFeeInBtc, optionalBtcFeeInFiat, btcFormatter);
            if (DevEnv.isDaoActivated()) {
                tradeFeeInBtcWithFiat.set(btcFeeWithFiatAmount);
            } else {
                tradeFeeInBtcWithFiat.set(btcFormatter.formatCoinWithCode(takerFeeAsCoin));
            }

            Coin makerFeeInBsq = dataModel.getTakerFeeInBsq();
            Optional<Volume> optionalBsqFeeInFiat = OfferUtil.getFeeInUserFiatCurrency(makerFeeInBsq,
                    false, preferences, priceFeedService, bsqFormatter);
            String bsqFeeWithFiatAmount = OfferUtil.getFeeWithFiatAmount(makerFeeInBsq, optionalBsqFeeInFiat, bsqFormatter);
            if (DevEnv.isDaoActivated()) {
                tradeFeeInBsqWithFiat.set(bsqFeeWithFiatAmount);
            } else {
                // Before DAO is enabled we show fee as fiat and % in second line
                String feeInFiatAsString;
                if (optionalBtcFeeInFiat != null && optionalBtcFeeInFiat.isPresent()) {
                    feeInFiatAsString = btcFormatter.formatVolumeWithCode(optionalBtcFeeInFiat.get());
                } else {
                    feeInFiatAsString = Res.get("shared.na");
                }

                double amountAsLong = (double) dataModel.getAmount().get().value;
                double makerFeeInBtcAsLong = (double) makerFeeInBtc.value;
                double percent = makerFeeInBtcAsLong / amountAsLong;

                tradeFeeInBsqWithFiat.set(Res.get("createOffer.tradeFee.fiatAndPercent",
                        feeInFiatAsString,
                        btcFormatter.formatToPercentWithSymbol(percent)));
            }
        }
        tradeFeeDescription.set(DevEnv.isDaoActivated() ? Res.get("createOffer.tradeFee.descriptionBSQEnabled") :
                Res.get("createOffer.tradeFee.descriptionBTCOnly"));
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
                showWarningInvalidBtcDecimalPlaces.set(!btcFormatter.hasBtcValidDecimals(userInput));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input
                amount.set(btcFormatter.formatCoin(dataModel.getAmount().get()));

                calculateVolume();

                Price tradePrice = dataModel.tradePrice;
                long maxTradeLimit = dataModel.getMaxTradeLimit();
                if (dataModel.getPaymentMethod().getId().equals(PaymentMethod.HAL_CASH_ID)) {
                    Coin adjustedAmountForHalCash = OfferUtil.getAdjustedAmountForHalCash(dataModel.getAmount().get(),
                            tradePrice,
                            maxTradeLimit);
                    dataModel.applyAmount(adjustedAmountForHalCash);
                    amount.set(btcFormatter.formatCoin(dataModel.getAmount().get()));
                } else if (CurrencyUtil.isFiatCurrency(dataModel.getCurrencyCode())) {
                    if (!isAmountEqualMinAmount(dataModel.getAmount().get())) {
                        // We only apply the rounding if the amount is variable (minAmount is lower as amount).
                        // Otherwise we could get an amount lower then the minAmount set by rounding
                        Coin roundedAmount = OfferUtil.getRoundedFiatAmount(dataModel.getAmount().get(), tradePrice,
                                maxTradeLimit);
                        dataModel.applyAmount(roundedAmount);
                    }
                    amount.set(btcFormatter.formatCoin(dataModel.getAmount().get()));
                }

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            Res.get("takeOffer.validation.amountSmallerThanMinAmount")));

                if (dataModel.isAmountLargerThanOfferAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            Res.get("takeOffer.validation.amountLargerThanOfferAmount")));

                if (dataModel.wouldCreateDustForMaker())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            Res.get("takeOffer.validation.amountLargerThanOfferAmountMinusFee")));
            } else if (btcValidator.getMaxTradeLimit() != null && btcValidator.getMaxTradeLimit().value == OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value) {
                if (dataModel.getDirection() == OfferPayload.Direction.BUY) {
                    new Popup<>().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.seller",
                            Res.get("offerbook.warning.newVersionAnnouncement")))
                            .width(900)
                            .show();
                } else {
                    new Popup<>().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.buyer",
                            Res.get("offerbook.warning.newVersionAnnouncement")))
                            .width(900)
                            .show();
                }

            }
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

        updateSpinnerInfo();

        updateButtonDisableState();
    }

    private void applyTradeErrorMessage(@Nullable String errorMessage) {
        if (errorMessage != null) {
            String appendMsg = "";
            switch (trade.getState().getPhase()) {
                case INIT:
                    appendMsg = Res.get("takeOffer.error.noFundsLost");
                    break;
                case TAKER_FEE_PUBLISHED:
                    appendMsg = Res.get("takeOffer.error.feePaid");
                    break;
                case DEPOSIT_PUBLISHED:
                case FIAT_SENT:
                case FIAT_RECEIVED:
                    appendMsg = Res.get("takeOffer.error.depositPublished");
                    break;
                case PAYOUT_PUBLISHED:
                case WITHDRAWN:
                    appendMsg = Res.get("takeOffer.error.payoutPublished");
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
        if (trade.isDepositPublished()) {
            if (trade.getDepositTx() != null) {
                if (takeOfferSucceededHandler != null)
                    takeOfferSucceededHandler.run();

                showTransactionPublishedScreen.set(true);
                updateSpinnerInfo();
            } else {
                final String msg = "trade.getDepositTx() must not be null.";
                DevEnv.logErrorAndThrowIfDevMode(msg);
            }
        }
    }

    private void updateButtonDisableState() {
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid
                && dataModel.isMinAmountLessOrEqualAmount()
                && !dataModel.isAmountLargerThanOfferAmount()
                && isOfferAvailable.get()
                && !dataModel.wouldCreateDustForMaker();
        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        // isTakeOfferButtonDisabled.set(takeOfferRequested || !inputDataValid || notSufficientFees);
        isTakeOfferButtonDisabled.set(takeOfferRequested || !inputDataValid || !dataModel.getIsBtcWalletFunded().get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        volume.bind(createStringBinding(() -> btcFormatter.formatVolume(dataModel.volume.get()), dataModel.volume));

        if (dataModel.getDirection() == OfferPayload.Direction.SELL) {
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
                dataModel.calculateTotalToPay();
                applyTakerFee();
            }
            updateButtonDisableState();
        };
        amountAsCoinListener = (ov, oldValue, newValue) -> {
            amount.set(btcFormatter.formatCoin(newValue));
            applyTakerFee();
        };
        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();

        tradeStateListener = (ov, oldValue, newValue) -> applyTradeState(newValue);
        tradeErrorListener = (ov, oldValue, newValue) -> applyTradeErrorMessage(newValue);
        offerStateListener = (ov, oldValue, newValue) -> applyOfferState(newValue);
        connectionListener = new ConnectionListener() {
            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                if (connection.getPeersNodeAddressOptional().isPresent() &&
                        connection.getPeersNodeAddressOptional().get().equals(offer.getMakerNodeAddress())) {
                    offerWarning.set(Res.get("takeOffer.warning.connectionToPeerLost"));
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
        } else if (dataModel.getIsBtcWalletFunded().get()) {
            spinnerInfoText.set("");
           /* if (dataModel.isFeeFromFundingTxSufficient.get()) {
                spinnerInfoText.set("");
            } else {
                spinnerInfoText.set("Check if funding tx miner fee is sufficient...");
            }*/
        } else {
            spinnerInfoText.set(Res.get("shared.waitingForFunds"));
        }

        isWaitingForFunds.set(!spinnerInfoText.get().isEmpty());
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().addListener(amountAsCoinListener);

        dataModel.getIsBtcWalletFunded().addListener(isWalletFundedListener);
        p2PService.getNetworkNode().addConnectionListener(connectionListener);
       /* isFeeSufficientSubscription = EasyBind.subscribe(dataModel.isFeeFromFundingTxSufficient, newValue -> {
            updateButtonDisableState();
            updateSpinnerInfo();
        });*/
    }

    private void removeListeners() {
        amount.removeListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().removeListener(amountAsCoinListener);

        dataModel.getIsBtcWalletFunded().removeListener(isWalletFundedListener);
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
        if (amount.get() != null && !amount.get().isEmpty()) {
            Coin amount = btcFormatter.parseToCoinWith4Decimals(this.amount.get());
            long maxTradeLimit = dataModel.getMaxTradeLimit();
            Price price = dataModel.tradePrice;
            if (price != null) {
                if (dataModel.isHalCashAccount()) {
                    amount = OfferUtil.getAdjustedAmountForHalCash(amount, price, maxTradeLimit);
                } else if (CurrencyUtil.isFiatCurrency(dataModel.getCurrencyCode()) && !isAmountEqualMinAmount(amount)) {
                    // We only apply the rounding if the amount is variable (minAmount is lower as amount).
                    // Otherwise we could get an amount lower then the minAmount set by rounding
                    amount = OfferUtil.getRoundedFiatAmount(amount, price, maxTradeLimit);
                }
            }
            dataModel.applyAmount(amount);
        }
    }

    private boolean isAmountEqualMinAmount(Coin amount) {
        return amount.value == offer.getMinAmount().value;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    BSFormatter getBtcFormatter() {
        return btcFormatter;
    }

    boolean isSeller() {
        return dataModel.getDirection() == OfferPayload.Direction.BUY;
    }

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

    String getTradeAmount() {
        return btcFormatter.formatCoinWithCode(dataModel.getAmount().get());
    }

    public String getSecurityDepositInfo() {
        return btcFormatter.formatCoinWithCode(dataModel.getSecurityDeposit()) +
                GUIUtil.getPercentageOfTradeAmount(dataModel.getSecurityDeposit(), dataModel.getAmount().get(), btcFormatter);
    }

    public String getSecurityDepositWithCode() {
        return btcFormatter.formatCoinWithCode(dataModel.getSecurityDeposit());
    }

    public String getTradeFee() {
        //TODO use last bisq market price to estimate BSQ val
        final Coin takerFeeAsCoin = dataModel.getTakerFee();
        final String takerFee = getFormatterForTakerFee().formatCoinWithCode(takerFeeAsCoin);
        if (dataModel.isCurrencyForTakerFeeBtc())
            return takerFee + GUIUtil.getPercentageOfTradeAmount(takerFeeAsCoin, dataModel.getAmount().get(), btcFormatter);
        else
            return takerFee + " (" + Res.get("shared.tradingFeeInBsqInfo", btcFormatter.formatCoinWithCode(takerFeeAsCoin)) + ")";
    }

    public String getTakerFeePercentage() {
        final Coin takerFeeAsCoin = dataModel.getTakerFee();
        if (dataModel.isCurrencyForTakerFeeBtc())
            return takerFeeAsCoin != null ? GUIUtil.getPercentage(takerFeeAsCoin, dataModel.getAmount().get(), btcFormatter) : Res.get("shared.na");
        else
            return Res.get("dao.paidWithBsq");
    }

    public String getTotalToPayInfo() {
        final String totalToPay = this.totalToPay.get();
        if (dataModel.isCurrencyForTakerFeeBtc())
            return totalToPay;
        else
            return totalToPay + " + " + bsqFormatter.formatCoinWithCode(dataModel.getTakerFee());
    }

    public String getTxFee() {
        Coin txFeeAsCoin = dataModel.getTotalTxFee();
        return btcFormatter.formatCoinWithCode(txFeeAsCoin) +
                GUIUtil.getPercentageOfTradeAmount(txFeeAsCoin, dataModel.getAmount().get(), btcFormatter);

    }

    public String getTxFeePercentage() {
        Coin txFeeAsCoin = dataModel.getTotalTxFee();
        return GUIUtil.getPercentage(txFeeAsCoin, dataModel.getAmount().get(), btcFormatter);
    }

    public PaymentMethod getPaymentMethod() {
        return dataModel.getPaymentMethod();
    }

    ObservableList<PaymentAccount> getPossiblePaymentAccounts() {
        return dataModel.getPossiblePaymentAccounts();
    }

    public PaymentAccount getLastSelectedPaymentAccount() {
        return dataModel.getLastSelectedPaymentAccount();
    }

    boolean hasAcceptedArbitrators() {
        return dataModel.hasAcceptedArbitrators();
    }

    boolean isReadyForTxBroadcast() {
        return GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup);
    }

    void showNotReadyForTxBroadcastPopups() {
        GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
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

    public String getBuyerSecurityDeposit() {
        return btcFormatter.formatCoin(dataModel.getBuyerSecurityDeposit());
    }

    public String getSellerSecurityDeposit() {
        return btcFormatter.formatCoin(dataModel.getSellerSecurityDeposit());
    }

    private BSFormatter getFormatterForTakerFee() {
        return dataModel.isCurrencyForTakerFeeBtc() ? btcFormatter : bsqFormatter;
    }
}
