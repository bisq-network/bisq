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

package io.bitsquare.gui.main.trade.takeoffer;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.UIModel;
import io.bitsquare.gui.main.trade.OrderBookInfo;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferProtocol;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferProtocolListener;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.ExchangeRate;
import com.google.bitcoin.utils.Fiat;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class TakeOfferModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(TakeOfferModel.class);

    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final Settings settings;

    private OrderBookInfo orderBookInfo;
    private AddressEntry addressEntry;

    final StringProperty requestTakeOfferErrorMessage = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty requestTakeOfferSuccess = new SimpleBooleanProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> collateralAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> offerFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> networkFeeAsCoin = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    TakeOfferModel(TradeManager tradeManager, WalletFacade walletFacade, Settings settings) {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;
        this.settings = settings;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        super.initialize();

        offerFeeAsCoin.set(FeePolicy.CREATE_OFFER_FEE);
        networkFeeAsCoin.set(FeePolicy.TX_FEE);
    }

    @Override
    public void activate() {
        super.activate();

        btcCode.bind(settings.btcDenominationProperty());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();

        btcCode.unbind();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void takeOffer() {
        // data validation is done in the trade domain
        /*tradeManager.requestPlaceOffer(orderBookInfo.getOffer().getId(),
                orderBookInfo.getOffer().getDirection(),
                priceAsFiat.get(),
                amountAsCoin.get(),
                minAmountAsCoin.get(),
                (transaction) -> {
                    transactionId.set(transaction.getHashAsString());
                    requestTakeOfferSuccess.set(true);
                },
                requestTakeOfferErrorMessage::set
        );*/
        SellerTakesOfferProtocolListener listener = new SellerTakesOfferProtocolListener() {
            @Override
            public void onDepositTxPublished(String depositTxId) {
                transactionId.set(depositTxId);
                requestTakeOfferSuccess.set(true);
            }

            @Override
            public void onBankTransferInited(String tradeId) {

            }

            @Override
            public void onPayoutTxPublished(Trade trade, String hashAsString) {

            }

            @Override
            public void onFault(Throwable throwable, SellerTakesOfferProtocol.State state) {
                requestTakeOfferErrorMessage.set("An error occurred. Error: " + throwable.getMessage());
            }

            @Override
            public void onWaitingForPeerResponse(SellerTakesOfferProtocol.State state) {

            }

            @Override
            public void onCompleted(SellerTakesOfferProtocol.State state) {

            }

            @Override
            public void onTakeOfferRequestRejected(Trade trade) {
                requestTakeOfferErrorMessage.set("Take offer request got rejected.");
            }
        };

        tradeManager.takeOffer(amountAsCoin.get(), orderBookInfo.getOffer(), listener);
        /*new SellerTakesOfferProtocolListener() {
            @Override
            public void onDepositTxPublished(String depositTxId) {
                setDepositTxId(depositTxId);
                accordion.setExpandedPane(waitBankTxTitledPane);
                infoLabel.setText("Deposit transaction published by offerer.\n" +
                        "As soon as the offerer starts the \n" +
                        "Bank transfer, you will be informed.");
                depositTxIdTextField.setText(depositTxId);
            }

            @Override
            public void onBankTransferInited(String tradeId) {
                setTradeId(tradeId);
                headLineLabel.setText("Bank transfer initiated");
                infoLabel.setText("Check your bank account and continue \n" + "when you have received the money.");
                receivedFiatButton.setDisable(false);
            }

            @Override
            public void onPayoutTxPublished(Trade trade, String payoutTxId) {
                accordion.setExpandedPane(summaryTitledPane);

                summaryPaidTextField.setText(BSFormatter.formatCoinWithCode(trade.getTradeAmount()));
                summaryReceivedTextField.setText(BSFormatter.formatFiat(trade.getTradeVolume()));
                summaryFeesTextField.setText(BSFormatter.formatCoinWithCode(
                        FeePolicy.TAKE_OFFER_FEE.add(FeePolicy.TX_FEE)));
                summaryCollateralTextField.setText(BSFormatter.formatCoinWithCode(
                        trade.getCollateralAmount()));
                summaryDepositTxIdTextField.setText(depositTxId);
                summaryPayoutTxIdTextField.setText(payoutTxId);
            }

            @Override
            public void onFault(Throwable throwable, SellerTakesOfferProtocol.State state) {
                log.error("Error while executing trade process at state: " + state + " / " + throwable);
                Popups.openErrorPopup("Error while executing trade process",
                        "Error while executing trade process at state: " + state + " / " + throwable);
            }

            @Override
            public void onWaitingForPeerResponse(SellerTakesOfferProtocol.State state) {
                log.debug("Waiting for peers response at state " + state);
            }

            @Override
            public void onCompleted(SellerTakesOfferProtocol.State state) {
                log.debug("Trade protocol completed at state " + state);
            }

            @Override
            public void onTakeOfferRequestRejected(Trade trade) {
                log.error("Take offer request rejected");
                Popups.openErrorPopup("Take offer request rejected",
                        "Your take offer request has been rejected. It might be that the offerer got another " +
                                "request shortly before your request arrived.");
            }
        });*/

    }

    void calculateVolume() {
        try {
            if (priceAsFiat.get() != null &&
                    amountAsCoin.get() != null &&
                    !amountAsCoin.get().isZero() &&
                    !priceAsFiat.get().isZero()) {
                volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateTotalToPay() {
        calculateCollateral();
        try {
            if (collateralAsCoin.get() != null) {
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(amountAsCoin.get()).add(networkFeeAsCoin.get()).add
                        (collateralAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateCollateral() {
        try {
            if (amountAsCoin.get() != null && orderBookInfo != null)
                collateralAsCoin.set(amountAsCoin.get().multiply(orderBookInfo.getOffer().getCollateral()).
                        divide(1000L));
        } catch (Throwable t) {
            // The multiply might lead to too large numbers, we don't handle it but it should not break the app
            log.error(t.toString());
        }
    }

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmountAsCoin.get() != null && amountAsCoin.get() != null)
            return !minAmountAsCoin.get().isGreaterThan(amountAsCoin.get());
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setOrderBookInfo(@NotNull OrderBookInfo orderBookInfo) {
        this.orderBookInfo = orderBookInfo;
        addressEntry = walletFacade.getAddressInfoByTradeID(orderBookInfo.getOffer().getId());
        walletFacade.addBalanceListener(new BalanceListener(addressEntry.getAddress()) {
            @Override
            public void onBalanceChanged(@NotNull Coin balance) {
                updateBalance(balance);
            }
        });
        updateBalance(walletFacade.getBalanceForAddress(addressEntry.getAddress()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    WalletFacade getWalletFacade() {
        return walletFacade;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBalance(@NotNull Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

}
