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

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.data.RawTransactionInput;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.filter.PaymentAccountFilter;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.user.User;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ProcessModel implements Model, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(ProcessModel.class);

    // Transient/Immutable
    transient private TradeManager tradeManager;
    transient private OpenOfferManager openOfferManager;
    transient private WalletService walletService;
    transient private TradeWalletService tradeWalletService;
    transient private ArbitratorManager arbitratorManager;
    transient private Offer offer;
    transient private User user;
    transient private FilterManager filterManager;
    transient private KeyRing keyRing;
    transient private P2PService p2PService;

    // Mutable
    public final TradingPeer tradingPeer;
    transient private TradeMessage tradeMessage;
    private byte[] payoutTxSignature;

    private List<NodeAddress> takerAcceptedArbitratorNodeAddresses;

    // that is used to store temp. the peers address when we get an incoming message before the message is verified.
    // After successful verified we copy that over to the trade.tradingPeerAddress
    private NodeAddress tempTradingPeerNodeAddress;
    private byte[] preparedDepositTx;
    private ArrayList<RawTransactionInput> rawTransactionInputs;
    private long changeOutputValue;
    @Nullable
    private String changeOutputAddress;
    private Transaction takeOfferFeeTx;
    private boolean useSavingsWallet;
    private Coin fundsNeededForTrade;

    public ProcessModel() {
        tradingPeer = new TradingPeer();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public void onAllServicesInitialized(Offer offer,
                                         TradeManager tradeManager,
                                         OpenOfferManager openOfferManager,
                                         P2PService p2PService,
                                         WalletService walletService,
                                         TradeWalletService tradeWalletService,
                                         ArbitratorManager arbitratorManager,
                                         User user,
                                         FilterManager filterManager,
                                         KeyRing keyRing,
                                         boolean useSavingsWallet,
                                         Coin fundsNeededForTrade) {
        this.offer = offer;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.arbitratorManager = arbitratorManager;
        this.user = user;
        this.filterManager = filterManager;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.useSavingsWallet = useSavingsWallet;
        this.fundsNeededForTrade = fundsNeededForTrade;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////


    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public OpenOfferManager getOpenOfferManager() {
        return openOfferManager;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public TradeWalletService getTradeWalletService() {
        return tradeWalletService;
    }

    public Offer getOffer() {
        return offer;
    }

    public String getId() {
        return offer.getId();
    }

    public User getUser() {
        return user;
    }

    public NodeAddress getMyAddress() {
        return p2PService.getAddress();
    }

    public Coin getFundsNeededForTrade() {
        return fundsNeededForTrade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

    @Nullable
    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

    @Nullable
    public PaymentAccountContractData getPaymentAccountContractData(Trade trade) {
        PaymentAccount paymentAccount;
        if (trade instanceof OffererTrade)
            paymentAccount = user.getPaymentAccount(offer.getOffererPaymentAccountId());
        else
            paymentAccount = user.getPaymentAccount(trade.getTakerPaymentAccountId());
        return paymentAccount != null ? paymentAccount.getContractData() : null;
    }

    public String getAccountId() {
        return user.getAccountId();
    }

    @Nullable
    public byte[] getPayoutTxSignature() {
        return payoutTxSignature;
    }

    public void setPayoutTxSignature(byte[] payoutTxSignature) {
        this.payoutTxSignature = payoutTxSignature;
    }

    @Override
    public void persist() {
    }

    @Override
    public void onComplete() {
    }

    public P2PService getP2PService() {
        return p2PService;
    }

    public PubKeyRing getPubKeyRing() {
        return keyRing.getPubKeyRing();
    }

    public KeyRing getKeyRing() {
        return keyRing;
    }

    public void setTakerAcceptedArbitratorNodeAddresses(List<NodeAddress> takerAcceptedArbitratorNodeAddresses) {
        this.takerAcceptedArbitratorNodeAddresses = takerAcceptedArbitratorNodeAddresses;
    }

    public List<NodeAddress> getTakerAcceptedArbitratorNodeAddresses() {
        return takerAcceptedArbitratorNodeAddresses;
    }

    public void setTempTradingPeerNodeAddress(NodeAddress tempTradingPeerNodeAddress) {
        this.tempTradingPeerNodeAddress = tempTradingPeerNodeAddress;
    }

    public NodeAddress getTempTradingPeerNodeAddress() {
        return tempTradingPeerNodeAddress;
    }

    public ArbitratorManager getArbitratorManager() {
        return arbitratorManager;
    }

    public void setPreparedDepositTx(byte[] preparedDepositTx) {
        this.preparedDepositTx = preparedDepositTx;
    }

    public byte[] getPreparedDepositTx() {
        return preparedDepositTx;
    }

    public void setRawTransactionInputs(ArrayList<RawTransactionInput> rawTransactionInputs) {
        this.rawTransactionInputs = rawTransactionInputs;
    }

    public ArrayList<RawTransactionInput> getRawTransactionInputs() {
        return rawTransactionInputs;
    }

    public void setChangeOutputValue(long changeOutputValue) {
        this.changeOutputValue = changeOutputValue;
    }

    public long getChangeOutputValue() {
        return changeOutputValue;
    }

    public void setChangeOutputAddress(String changeOutputAddress) {
        this.changeOutputAddress = changeOutputAddress;
    }

    @Nullable
    public String getChangeOutputAddress() {
        return changeOutputAddress;
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
    }

    public Transaction getTakeOfferFeeTx() {
        return takeOfferFeeTx;
    }

    public boolean getUseSavingsWallet() {
        return useSavingsWallet;
    }

    public boolean isPeersPaymentAccountDataAreBanned(PaymentAccountContractData paymentAccountContractData, PaymentAccountFilter[] appliedPaymentAccountFilter) {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().bannedPaymentAccounts.stream()
                        .filter(paymentAccountFilter -> {
                            final boolean samePaymentMethodId = paymentAccountFilter.paymentMethodId.equals(paymentAccountContractData.getPaymentMethodName());
                            if (samePaymentMethodId) {
                                try {
                                    Method method = paymentAccountContractData.getClass().getMethod(paymentAccountFilter.getMethodName);
                                    String result = (String) method.invoke(paymentAccountContractData);
                                    appliedPaymentAccountFilter[0] = paymentAccountFilter;
                                    return result.equals(paymentAccountFilter.value);
                                } catch (Throwable e) {
                                    log.error(e.getMessage());
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        })
                        .findAny()
                        .isPresent();
    }
}
