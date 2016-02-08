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

import com.google.inject.Inject;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.popups.WalletPasswordPopup;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.handlers.TradeResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class TakeOfferDataModel extends ActivatableDataModel {
    private final TradeManager tradeManager;
    private final TradeWalletService tradeWalletService;
    private final WalletService walletService;
    private final User user;
    private final WalletPasswordPopup walletPasswordPopup;
    private final Preferences preferences;

    private final Coin offerFeeAsCoin;
    private final Coin networkFeeAsCoin;
    private final Coin securityDepositAsCoin;

    private Offer offer;
    private AddressEntry addressEntry;

    final StringProperty btcCode = new SimpleStringProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();

    private BalanceListener balanceListener;
    private PaymentAccount paymentAccount;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    TakeOfferDataModel(TradeManager tradeManager, TradeWalletService tradeWalletService,
                       WalletService walletService, User user, WalletPasswordPopup walletPasswordPopup,
                       Preferences preferences) {
        this.tradeManager = tradeManager;
        this.tradeWalletService = tradeWalletService;
        this.walletService = walletService;
        this.user = user;
        this.walletPasswordPopup = walletPasswordPopup;
        this.preferences = preferences;

        offerFeeAsCoin = FeePolicy.getCreateOfferFee();
        networkFeeAsCoin = FeePolicy.getFixedTxFeeForTrades();
        securityDepositAsCoin = FeePolicy.getSecurityDeposit();
    }

    @Override
    protected void activate() {
        // when leaving screen we reset state
        offer.setState(Offer.State.UNDEFINED);

        addBindings();
        addListeners();
        updateBalance(walletService.getBalanceForAddress(addressEntry.getAddress()));
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
        tradeManager.onCancelAvailabilityRequest(offer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate
    void initWithData(Offer offer) {
        this.offer = offer;

        addressEntry = walletService.getAddressEntryByOfferId(offer.getId());
        checkNotNull(addressEntry, "addressEntry must not be null");

        ObservableList<PaymentAccount> possiblePaymentAccounts = getPossiblePaymentAccounts();
        checkArgument(!possiblePaymentAccounts.isEmpty(), "possiblePaymentAccounts.isEmpty()");
        paymentAccount = possiblePaymentAccounts.get(0);

        amountAsCoin.set(offer.getAmount());

        if (BitsquareApp.DEV_MODE)
            amountAsCoin.set(offer.getAmount());

        calculateVolume();
        calculateTotalToPay();

        balanceListener = new BalanceListener(addressEntry.getAddress()) {
            @Override
            public void onBalanceChanged(@NotNull Coin balance) {
                updateBalance(balance);
            }
        };

        offer.resetState();
    }

    void checkOfferAvailability(ResultHandler resultHandler) {
        tradeManager.checkOfferAvailability(offer, resultHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    // errorMessageHandler is used only in the check availability phase. As soon we have a trade we write the error msg in the trade object as we want to 
    // have it persisted as well.
    void onTakeOffer(TradeResultHandler tradeResultHandler) {
        if (walletService.getWallet().isEncrypted() && tradeWalletService.getAesKey() == null) {
            walletPasswordPopup.onAesKey(aesKey -> {
                tradeWalletService.setAesKey(aesKey);
                doTakeOffer(tradeResultHandler);
            }).show();
        } else {
            doTakeOffer(tradeResultHandler);
        }
    }

    private void doTakeOffer(TradeResultHandler tradeResultHandler) {
        tradeManager.onTakeOffer(amountAsCoin.get(),
                offer,
                paymentAccount.getId(),
                tradeResultHandler
        );
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null)
            this.paymentAccount = paymentAccount;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    Offer.Direction getDirection() {
        return offer.getDirection();
    }

    public Offer getOffer() {
        return offer;
    }

    ObservableList<PaymentAccount> getPossiblePaymentAccounts() {
        ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList(new ArrayList<>());
        for (PaymentAccount paymentAccount : user.getPaymentAccounts()) {
            if (paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod())) {
                for (TradeCurrency tradeCurrency : paymentAccount.getTradeCurrencies()) {
                    if (tradeCurrency.getCode().equals(offer.getCurrencyCode())) {
                        paymentAccounts.add(paymentAccount);
                    }
                }
            }
        }
        return paymentAccounts;
    }

    boolean hasAcceptedArbitrators() {
        return user.getAcceptedArbitrators().size() > 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        btcCode.bind(preferences.btcDenominationProperty());
    }

    private void removeBindings() {
        btcCode.unbind();
    }

    private void addListeners() {
        walletService.addBalanceListener(balanceListener);
    }

    private void removeListeners() {
        walletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (offer != null &&
                offer.getPrice() != null &&
                amountAsCoin.get() != null &&
                !amountAsCoin.get().isZero()) {
            volumeAsFiat.set(new ExchangeRate(offer.getPrice()).coinToFiat(amountAsCoin.get()));

            updateBalance(walletService.getBalanceForAddress(addressEntry.getAddress()));
        }
    }

    void calculateTotalToPay() {
        if (getDirection() == Offer.Direction.SELL)
            totalToPayAsCoin.set(offerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin));
        else
            totalToPayAsCoin.set(offerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin).add(amountAsCoin.get()));
    }

    private void updateBalance(@NotNull Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (offer != null && offer.getMinAmount() != null && amountAsCoin.get() != null)
            return !offer.getMinAmount().isGreaterThan(amountAsCoin.get());
        return true;
    }

    boolean isAmountLargerThanOfferAmount() {
        //noinspection SimplifiableIfStatement
        if (amountAsCoin.get() != null && offer != null)
            return amountAsCoin.get().isGreaterThan(offer.getAmount());
        return true;
    }

    boolean wouldCreateDustForOfferer() {
        //noinspection SimplifiableIfStatement
        if (amountAsCoin.get() != null && offer != null) {
            Coin customAmount = offer.getAmount().subtract(amountAsCoin.get());
            Coin dustAndFee = FeePolicy.getFeePerKb().add(Transaction.MIN_NONDUST_OUTPUT);
            return customAmount.isPositive() && customAmount.isLessThan(dustAndFee);
        } else {
            return true;
        }
    }

    public PaymentMethod getPaymentMethod() {
        return offer.getPaymentMethod();
    }

    public TradeCurrency getTradeCurrency() {
        return new TradeCurrency(offer.getCurrencyCode());
    }

    public Coin getSecurityDepositAsCoin() {
        return securityDepositAsCoin;
    }

    public Coin getOfferFeeAsCoin() {
        return offerFeeAsCoin;
    }

    public Coin getNetworkFeeAsCoin() {
        return networkFeeAsCoin;
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
    }

    public boolean getShowTakeOfferConfirmation() {
        return preferences.getShowTakeOfferConfirmation();
    }

    public void setShowTakeOfferConfirmation(boolean selected) {
        preferences.setShowTakeOfferConfirmation(selected);
    }

    public List<Arbitrator> getArbitrators() {
        return user.getAcceptedArbitrators();
    }

    public Preferences getPreferences() {
        return preferences;
    }
}
