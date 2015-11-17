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
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferViewModel extends ActivatableWithDataModel<TakeOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final BSFormatter formatter;

    // static fields

    private String amountRange;
    private String addressAsString;
    private String paymentLabel;
    private boolean takeOfferRequested;
    private Trade trade;
    private Offer offer;
    private String price;
    private String directionLabel;
    private String amountDescription;

    // TODO convert unneeded properties to static fields
    // dynamic fields
    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty offerWarning = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty isOfferAvailable = new SimpleBooleanProperty();
    final BooleanProperty isTakeOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty isTakeOfferSpinnerVisible = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();

    private ChangeListener<String> amountListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Boolean> isWalletFundedListener;
    private ChangeListener<Trade.State> tradeStateListener;
    private ChangeListener<String> tradeErrorListener;
    private ChangeListener<Offer.State> offerStateListener;
    private ChangeListener<String> offerErrorListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TakeOfferViewModel(TakeOfferDataModel dataModel, BtcValidator btcValidator,
                              BSFormatter formatter) {
        super(dataModel);

        this.btcValidator = btcValidator;
        this.formatter = formatter;

        createListeners();
    }

    @Override
    protected void activate() {
        addBindings();
        addListeners();

        amount.set(formatter.formatCoin(dataModel.amountAsCoin.get()));
        isTakeOfferSpinnerVisible.set(false);
        showTransactionPublishedScreen.set(false);

        // when getting back to an open screen we want to re-check again
        isOfferAvailable.set(false);
        checkNotNull(offer, "offer must not be null");

        offer.stateProperty().addListener(offerStateListener);
        applyOfferState(offer.stateProperty().get());

        // when getting back to an open screen we want to re-check again
        UserThread.execute(() -> dataModel.checkOfferAvailability(() -> {
        }));
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

        if (offer.getDirection() == Offer.Direction.BUY) {
            directionLabel = BSResources.get("shared.sellBitcoin");
            amountDescription = BSResources.get("takeOffer.amountPriceBox.buy.amountDescription");
        } else {
            directionLabel = BSResources.get("shared.buyBitcoin");
            amountDescription = BSResources.get("takeOffer.amountPriceBox.sell.amountDescription");
        }

        amountRange = formatter.formatCoin(offer.getMinAmount()) + " - " + formatter.formatCoin(offer.getAmount());
        price = formatter.formatFiat(offer.getPrice());
        paymentLabel = BSResources.get("takeOffer.fundsBox.paymentLabel", offer.getId());

        checkNotNull(dataModel.getAddressEntry(), "dataModel.getAddressEntry() must not be null");

        addressAsString = dataModel.getAddressEntry().getAddress().toString();
        address.set(dataModel.getAddressEntry().getAddress());

        offerErrorListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                errorMessage.set(newValue);
        };
        offer.errorMessageProperty().addListener(offerErrorListener);
        errorMessage.set(offer.errorMessageProperty().get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer() {
        takeOfferRequested = true;
        applyOnTakeOfferResult(false);

        isTakeOfferSpinnerVisible.set(true);
        dataModel.onTakeOffer(trade -> {
            this.trade = trade;
            trade.stateProperty().addListener(tradeStateListener);
            applyTradeState(trade.getState());
            trade.errorMessageProperty().addListener(tradeErrorListener);
            applyTradeErrorMessage(trade.errorMessageProperty().get());
            updateButtonDisableState();
        });
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        dataModel.onPaymentAccountSelected(paymentAccount);
    }

    void onSecurityDepositInfoDisplayed() {
        dataModel.onSecurityDepositInfoDisplayed();
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
                break;
            case NOT_AVAILABLE:
                if (takeOfferRequested)
                    offerWarning.set("Take offer request failed because offer is not available anymore. " +
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

        if (offerWarning != null)
            isTakeOfferSpinnerVisible.set(false);

        updateButtonDisableState();
    }

    private void applyTradeErrorMessage(String errorMessage) {
        if (errorMessage != null) {
            String appendMsg = "";
            switch (trade.getState().getPhase()) {
                case PREPARATION:
                    appendMsg = "\n\nThere have no funds left your wallet yet.\n" +
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
        } else {
            this.errorMessage.set(null);
        }
    }

    private void applyTradeState(Trade.State tradeState) {
        log.debug("applyTradeState state = " + tradeState);

        if (trade.getState() == Trade.State.DEPOSIT_PUBLISHED
                || trade.getState() == Trade.State.DEPOSIT_SEEN_IN_NETWORK
                || trade.getState() == Trade.State.DEPOSIT_PUBLISHED_MSG_SENT
                || trade.getState() == Trade.State.DEPOSIT_PUBLISHED_MSG_RECEIVED) {
            if (trade.getDepositTx() != null)
                applyOnTakeOfferResult(true);
            else
                log.error("trade.getDepositTx() == null. That must not happen");
        }

        if (errorMessage.get() != null)
            isTakeOfferSpinnerVisible.set(false);
    }

    private void applyOnTakeOfferResult(boolean success) {
        isTakeOfferSpinnerVisible.set(false);
        showTransactionPublishedScreen.set(success);
    }

    private void updateButtonDisableState() {
        isTakeOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid
                        && dataModel.isMinAmountLessOrEqualAmount()
                        && !dataModel.isAmountLargerThanOfferAmount()
                        && dataModel.isWalletFunded.get()
                        && !takeOfferRequested)
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        volume.bind(createStringBinding(() -> formatter.formatFiat(dataModel.volumeAsFiat.get()), dataModel.volumeAsFiat));


        if (dataModel.getDirection() == Offer.Direction.BUY) {
            volumeDescriptionLabel.set(BSResources.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.getTradeCurrency().getCode()));
        } else {
            volumeDescriptionLabel.set(BSResources.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.getTradeCurrency().getCode()));
        }
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()), dataModel.totalToPayAsCoin));
        totalToPayAsCoin.bind(dataModel.totalToPayAsCoin);
        btcCode.bind(dataModel.btcCode);
    }


    private void removeBindings() {
        volumeDescriptionLabel.unbind();
        volume.unbind();
        totalToPay.unbind();
        totalToPayAsCoin.unbind();
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
        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();
        tradeStateListener = (ov, oldValue, newValue) -> applyTradeState(newValue);
        tradeErrorListener = (ov, oldValue, newValue) -> applyTradeErrorMessage(newValue);
        offerStateListener = (ov, oldValue, newValue) -> applyOfferState(newValue);
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener(amountAsCoinListener);

        dataModel.isWalletFunded.addListener(isWalletFundedListener);

    }

    private void removeListeners() {
        amount.removeListener(amountListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.removeListener(amountAsCoinListener);

        dataModel.isWalletFunded.removeListener(isWalletFundedListener);
        if (offer != null) {
            offer.stateProperty().removeListener(offerStateListener);
            offer.errorMessageProperty().addListener(offerErrorListener);
        }

        if (trade != null) {
            trade.stateProperty().removeListener(tradeStateListener);
            trade.errorMessageProperty().removeListener(tradeErrorListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void calculateVolume() {
        setAmountToModel();
        dataModel.calculateVolume();
    }

    private void setAmountToModel() {
        dataModel.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    BSFormatter getFormatter() {
        return formatter;
    }

    boolean getDisplaySecurityDepositInfo() {
        return dataModel.getDisplaySecurityDepositInfo();
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

    public String getAddressAsString() {
        return addressAsString;
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

    String getOfferFee() {
        return formatter.formatCoinWithCode(dataModel.getOfferFeeAsCoin());
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

    public TradeCurrency getTradeCurrency() {
        return dataModel.getTradeCurrency();
    }

    public boolean getShowTakeOfferConfirmation() {
        return dataModel.getShowTakeOfferConfirmation();
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
