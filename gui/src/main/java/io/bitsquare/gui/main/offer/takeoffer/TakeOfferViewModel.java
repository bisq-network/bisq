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

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.offer.Offer;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferViewModel extends ActivatableWithDataModel<TakeOfferDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(TakeOfferViewModel.class);

    public enum State {
        CHECK_AVAILABILITY,
        AMOUNT_SCREEN,
        PAYMENT_SCREEN,
        DETAILS_SCREEN
    }

    private final BtcValidator btcValidator;
    private final BSFormatter formatter;
    private final String offerFee;
    private final String networkFee;

    // static fields
    private String amountRange;
    private String price;
    private String directionLabel;
    private String bankAccountType;
    private String bankAccountCurrency;
    private String bankAccountCounty;
    private String acceptedCountries;
    private String acceptedLanguages;
    private String acceptedArbitratorIds;
    private String addressAsString;
    private String paymentLabel;
    private boolean detailsVisible;


    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty securityDeposit = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty amountDescription = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty amountPriceBoxInfo = new SimpleStringProperty();
    final StringProperty fundsBoxInfoDisplay = new SimpleStringProperty();

    final BooleanProperty takeOfferButtonDisabled = new SimpleBooleanProperty(false);
    final BooleanProperty isTakeOfferSpinnerVisible = new SimpleBooleanProperty(false);
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();


    // Needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();

    final ObjectProperty<State> state = new SimpleObjectProperty<>(TakeOfferViewModel.State.CHECK_AVAILABILITY);
    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    private boolean takeOfferRequested;

    // listeners
    private ChangeListener<String> amountChangeListener;
    private ChangeListener<Boolean> isWalletFundedChangeListener;
    private ChangeListener<Coin> amountAsCoinChangeListener;
    private ChangeListener<Offer.State> offerStateChangeListener;
    private ChangeListener<TradeState> tradeStateChangeListener;
    // Offer and trade are stored only for remove listener at deactivate
    private Offer offer;
    private Trade trade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TakeOfferViewModel(TakeOfferDataModel dataModel, BtcValidator btcValidator, BSFormatter formatter) {
        super(dataModel);

        this.btcValidator = btcValidator;
        this.formatter = formatter;

        this.offerFee = formatter.formatCoinWithCode(dataModel.offerFeeAsCoin.get());
        this.networkFee = formatter.formatCoinWithCode(dataModel.networkFeeAsCoin.get());

        createListeners();
    }

    @Override
    protected void doActivate() {
        addBindings();
        addListeners();
        isTakeOfferSpinnerVisible.set(false);
        showTransactionPublishedScreen.set(false);
    }

    @Override
    protected void doDeactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        volume.bind(createStringBinding(() -> formatter.formatFiatWithCode(dataModel.volumeAsFiat.get()), dataModel.volumeAsFiat));
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()), dataModel.totalToPayAsCoin));
        securityDeposit.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.securityDepositAsCoin.get()), dataModel.securityDepositAsCoin));
        totalToPayAsCoin.bind(dataModel.totalToPayAsCoin);
        btcCode.bind(dataModel.btcCode);
    }

    private void removeBindings() {
        volume.unbind();
        totalToPay.unbind();
        securityDeposit.unbind();
        totalToPayAsCoin.unbind();
        btcCode.unbind();
    }

    private void createListeners() {
        amountChangeListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                dataModel.calculateTotalToPay();
            }
            evaluateViewState();
        };
        isWalletFundedChangeListener = (ov, oldValue, newValue) -> evaluateViewState();
        amountAsCoinChangeListener = (ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue));
        offerStateChangeListener = (ov, oldValue, newValue) -> applyOfferState(newValue);
        tradeStateChangeListener = (ov, oldValue, newValue) -> applyTradeState(newValue);
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountChangeListener);
        dataModel.isWalletFunded.addListener(isWalletFundedChangeListener);
        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener(amountAsCoinChangeListener);

        amountChangeListener.changed(null, null, amount.get());
        isWalletFundedChangeListener.changed(null, null, dataModel.isWalletFunded.get());
        amountAsCoinChangeListener.changed(null, null, dataModel.amountAsCoin.get());
    }

    private void removeListeners() {
        amount.removeListener(amountChangeListener);
        dataModel.isWalletFunded.removeListener(isWalletFundedChangeListener);
        dataModel.amountAsCoin.removeListener(amountAsCoinChangeListener);

        if (offer != null)
            offer.stateProperty().removeListener(offerStateChangeListener);

        if (trade != null)
            trade.tradeStateProperty().removeListener(tradeStateChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(Coin amount, Offer offer) {
        dataModel.initWithData(amount, offer);

        this.offer = offer;

        directionLabel = offer.getDirection() == Offer.Direction.SELL ? BSResources.get("shared.buyBitcoin") : BSResources.get("shared.sellBitcoin");

        fiatCode.set(offer.getCurrencyCode());
        if (!dataModel.isMinAmountLessOrEqualAmount())
            amountValidationResult.set(new InputValidator.ValidationResult(false, BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));

        if (dataModel.getDirection() == Offer.Direction.BUY) {
            amountDescription.set(BSResources.get("takeOffer.amountPriceBox.buy.amountDescription", offer.getId()));
            volumeDescriptionLabel.set(BSResources.get("takeOffer.amountPriceBox.buy.volumeDescription", fiatCode.get()));
            amountPriceBoxInfo.set(BSResources.get("takeOffer.amountPriceBox.buy.info"));
            fundsBoxInfoDisplay.set(BSResources.get("takeOffer.fundsBox.buy.info"));
        }
        else {
            amountDescription.set(BSResources.get("takeOffer.amountPriceBox.sell.amountDescription", offer.getId()));
            volumeDescriptionLabel.set(BSResources.get("takeOffer.amountPriceBox.sell.volumeDescription", fiatCode.get()));
            amountPriceBoxInfo.set(BSResources.get("takeOffer.amountPriceBox.sell.info"));
            fundsBoxInfoDisplay.set(BSResources.get("takeOffer.fundsBox.sell.info"));
        }

        amountRange = formatter.formatCoinWithCode(offer.getMinAmount()) + " - " + formatter.formatCoinWithCode(offer.getAmount());
        price = formatter.formatFiatWithCode(offer.getPrice());

        paymentLabel = BSResources.get("takeOffer.fundsBox.paymentLabel", offer.getId());
        assert dataModel.getAddressEntry() != null;
        addressAsString = dataModel.getAddressEntry().getAddress().toString();
        address.set(dataModel.getAddressEntry().getAddress());

        acceptedCountries = formatter.countryLocalesToString(offer.getAcceptedCountries());
        acceptedLanguages = formatter.languageCodesToString(offer.getAcceptedLanguageCodes());
        acceptedArbitratorIds = formatter.arbitratorIdsToNames(offer.getArbitratorIds());
        bankAccountType = BSResources.get(offer.getFiatAccountType().toString());
        bankAccountCurrency = BSResources.get(CurrencyUtil.getDisplayName(offer.getCurrencyCode()));
        bankAccountCounty = BSResources.get(offer.getBankAccountCountry().name);

        offer.stateProperty().addListener(offerStateChangeListener);
        applyOfferState(offer.stateProperty().get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer() {
        takeOfferRequested = true;
        applyOnTakeOfferResult(false);

        isTakeOfferSpinnerVisible.set(true);
        dataModel.onTakeOffer((trade) -> {
            this.trade = trade;
            trade.tradeStateProperty().addListener(tradeStateChangeListener);
            applyTradeState(trade.tradeStateProperty().get());
            evaluateViewState();
        });
    }

    void onShowPaymentScreen() {
        state.set(TakeOfferViewModel.State.PAYMENT_SCREEN);
    }

    void onToggleShowAdvancedSettings() {
        detailsVisible = !detailsVisible;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyOfferState(Offer.State state) {
        log.debug("applyOfferState state = " + state);

        switch (state) {
            case UNDEFINED:
                // TODO set spinner?
                break;
            case AVAILABLE:
                this.state.set(TakeOfferViewModel.State.AMOUNT_SCREEN);
                break;
            case NOT_AVAILABLE:
                if (takeOfferRequested)
                    errorMessage.set("Take offer request failed because offer is not available anymore. " +
                            "Maybe another trader has taken the offer in the meantime.");
                else
                    errorMessage.set("You cannot take that offer because the offer was already taken by another trader.");
                takeOfferRequested = false;
                break;
            case REMOVED:
                if (!takeOfferRequested)
                    errorMessage.set("You cannot take that offer because the offer has been removed in the meantime.");

                takeOfferRequested = false;
                break;
            case OFFERER_OFFLINE:
                if (takeOfferRequested)
                    errorMessage.set("Take offer request failed because offerer is not online anymore.");
                else
                    errorMessage.set("You cannot take that offer because the offerer is offline.");
                takeOfferRequested = false;
                break;
            case FAULT:
                if (takeOfferRequested)
                    errorMessage.set("Take offer request failed.");
                else
                    errorMessage.set("The check for the offer availability failed.");
                takeOfferRequested = false;
                break;
            case TIMEOUT:
                if (takeOfferRequested)
                    errorMessage.set("Take offer request failed due a timeout.");
                else
                    errorMessage.set("The check for the offer availability failed due a timeout.");
                takeOfferRequested = false;
                break;
            default:
                log.error("Unhandled offer state: " + state);
                break;
        }

        if (errorMessage.get() != null) {
            isTakeOfferSpinnerVisible.set(false);
        }

        evaluateViewState();
    }

    private void applyTradeState(TradeState tradeState) {
        log.debug("applyTradeState state = " + tradeState);

        String msg = "An error occurred.";
        if (trade.getErrorMessage() != null)
            msg = "Error message: " + trade.getErrorMessage();

        if (trade instanceof SellerAsTakerTrade) {
            switch ((TradeState.SellerState) tradeState) {
                case PREPARATION:
                    break;
                case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                    assert trade.getDepositTx() != null;
                    transactionId.set(trade.getDepositTx().getHashAsString());
                    applyOnTakeOfferResult(true);
                    break;
                case DEPOSIT_CONFIRMED:
                case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                case FIAT_PAYMENT_RECEIPT:
                case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                case PAYOUT_TX_RECEIVED:
                case PAYOUT_TX_COMMITTED:
                case PAYOUT_BROAD_CASTED:
                    break;
               /* case TIMEOUT:
                    errorMessage.set("A timeout occurred. Maybe there are connection problems. " +
                            "Please try later again.\n" + msg);
                    takeOfferRequested = false;
                    break;
                case FAULT:
                    errorMessage.set(msg);
                    takeOfferRequested = false;
                    break;*/
                default:
                    log.warn("Unhandled trade state: " + tradeState);
                    break;
            }
        }
        else if (trade instanceof BuyerAsTakerTrade) {
            switch ((TradeState.BuyerState) tradeState) {
                case PREPARATION:
                    break;
                case DEPOSIT_PUBLISHED:
                    assert trade.getDepositTx() != null;
                    transactionId.set(trade.getDepositTx().getHashAsString());
                    applyOnTakeOfferResult(true);
                    break;
                case DEPOSIT_PUBLISHED_MSG_SENT:
                case DEPOSIT_CONFIRMED:
                case FIAT_PAYMENT_STARTED:
                case FIAT_PAYMENT_STARTED_MSG_SENT:
                case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                case PAYOUT_TX_COMMITTED:
                case PAYOUT_TX_SENT:
                case PAYOUT_BROAD_CASTED:
                    break;
               /* case TIMEOUT:
                    errorMessage.set("A timeout occurred. Maybe there are connection problems. " +
                            "Please try later again.\n" + msg);
                    takeOfferRequested = false;
                    break;
                case FAULT:
                    errorMessage.set(msg);
                    takeOfferRequested = false;
                    break;*/
                default:
                    log.warn("Unhandled trade state: " + tradeState);
                    break;
            }
        }

        if (errorMessage.get() != null)
            isTakeOfferSpinnerVisible.set(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Focus handling
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
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isSeller() {
        return dataModel.getDirection() == Offer.Direction.BUY;
    }

    void onSecurityDepositInfoDisplayed() {
        dataModel.onSecurityDepositInfoDisplayed();
    }

    WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    String getOfferFee() {
        return offerFee;
    }

    String getNetworkFee() {
        return networkFee;
    }

    String getFiatCode() {
        return fiatCode.get();
    }

    String getAmount() {
        return formatter.formatCoinWithCode(dataModel.amountAsCoin.get());
    }

    String getAmountRange() {
        return amountRange;
    }

    String getPrice() {
        return price;
    }

    String getDirectionLabel() {
        return directionLabel;
    }

    String getBankAccountType() {
        return bankAccountType;
    }

    String getBankAccountCurrency() {
        return bankAccountCurrency;
    }

    String getBankAccountCounty() {
        return bankAccountCounty;
    }

    String getAcceptedCountries() {
        return acceptedCountries;
    }

    String getAcceptedLanguages() {
        return acceptedLanguages;
    }

    String getAcceptedArbitratorIds() {
        return acceptedArbitratorIds;
    }

    String getAddressAsString() {
        return addressAsString;
    }

    String getPaymentLabel() {
        return paymentLabel;
    }

    boolean getDisplaySecurityDepositInfo() {
        return dataModel.getDisplaySecurityDepositInfo();
    }

    boolean isDetailsVisible() {
        return detailsVisible;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyOnTakeOfferResult(boolean success) {
        isTakeOfferSpinnerVisible.set(false);
        showTransactionPublishedScreen.set(success);
    }

    private void calculateVolume() {
        setAmountToModel();
        dataModel.calculateVolume();
    }

    private void setAmountToModel() {
        dataModel.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void evaluateViewState() {
        boolean isAmountAndPriceValidAndWalletFunded = isBtcInputValid(amount.get()).isValid &&
                dataModel.isMinAmountLessOrEqualAmount() &&
                !dataModel.isAmountLargerThanOfferAmount() &&
                dataModel.isWalletFunded.get();

        if (isAmountAndPriceValidAndWalletFunded && state.get() != TakeOfferViewModel.State.CHECK_AVAILABILITY)
            state.set(TakeOfferViewModel.State.PAYMENT_SCREEN);

        takeOfferButtonDisabled.set(!isAmountAndPriceValidAndWalletFunded || takeOfferRequested);
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

}
