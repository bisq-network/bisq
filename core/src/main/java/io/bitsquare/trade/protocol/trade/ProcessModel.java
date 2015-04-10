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

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.crypto.CryptoService;
import io.bitsquare.crypto.KeyRing;
import io.bitsquare.crypto.PubKeyRing;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.user.User;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessModel implements Model, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ProcessModel.class);

    // Transient/Immutable
    transient private MessageService messageService;
    transient private AddressService addressService;
    transient private WalletService walletService;
    transient private TradeWalletService tradeWalletService;
    transient private BlockChainService blockChainService;
    transient private CryptoService cryptoService;
    transient private ArbitrationRepository arbitrationRepository;
    transient private Offer offer;
    transient private User user;
    transient private KeyRing keyRing;

    // Mutable
    public final TradingPeer tradingPeer;
    transient private TradeMessage tradeMessage;
    private String takeOfferFeeTxId;
    private List<TransactionOutput> connectedOutputsForAllInputs;
    // private Coin payoutAmount;
    private Transaction preparedDepositTx;
    private List<TransactionOutput> outputs; // used to verify amounts with change outputs
    private byte[] payoutTxSignature;
    private Transaction takeOfferFeeTx;


    public ProcessModel() {
        log.trace("Created by constructor");
        tradingPeer = new TradingPeer();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");
    }

    public void onAllServicesInitialized(Offer offer,
                                         MessageService messageService,
                                         AddressService addressService,
                                         WalletService walletService,
                                         TradeWalletService tradeWalletService,
                                         BlockChainService blockChainService,
                                         CryptoService cryptoService,
                                         ArbitrationRepository arbitrationRepository,
                                         User user,
                                         KeyRing keyRing) {
        this.offer = offer;
        this.messageService = messageService;
        this.addressService = addressService;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.blockChainService = blockChainService;
        this.cryptoService = cryptoService;
        this.arbitrationRepository = arbitrationRepository;
        this.user = user;
        this.keyRing = keyRing;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MessageService getMessageService() {
        return messageService;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public TradeWalletService getTradeWalletService() {
        return tradeWalletService;
    }

    public BlockChainService getBlockChainService() {
        return blockChainService;
    }

    public byte[] getArbitratorPubKey() {
        return arbitrationRepository.getDefaultArbitrator().getPubKey();
    }

    public Offer getOffer() {
        return offer;
    }

    public String getId() {
        return offer.getId();
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
    public Transaction getTakeOfferFeeTx() {
        return takeOfferFeeTx;
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
    }

    public FiatAccount getFiatAccount() {
        return user.getFiatAccount(offer.getBankAccountId());
    }

    public DeterministicKey getRegistrationKeyPair() {
        return walletService.getRegistrationAddressEntry().getKeyPair();
    }

    public String getAccountId() {
        return user.getAccountId();
    }

    public byte[] getRegistrationPubKey() {
        return walletService.getRegistrationAddressEntry().getPubKey();
    }

    public AddressEntry getAddressEntry() {
        return walletService.getAddressEntry(offer.getId());
    }

    public byte[] getTradeWalletPubKey() {
        return getAddressEntry().getPubKey();
    }

    @Nullable
    public List<TransactionOutput> getConnectedOutputsForAllInputs() {
        return connectedOutputsForAllInputs;
    }

    public void setConnectedOutputsForAllInputs(List<TransactionOutput> connectedOutputsForAllInputs) {
        this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
    }

   /* @Nullable
    public Coin getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(Coin payoutAmount) {
        this.payoutAmount = payoutAmount;
    }*/

    @Nullable
    public Transaction getPreparedDepositTx() {
        return preparedDepositTx;
    }

    public void setPreparedDepositTx(Transaction preparedDepositTx) {
        this.preparedDepositTx = preparedDepositTx;
    }

    @Nullable
    public List<TransactionOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<TransactionOutput> outputs) {
        this.outputs = outputs;
    }

    @Nullable
    public byte[] getPayoutTxSignature() {
        return payoutTxSignature;
    }

    public void setPayoutTxSignature(byte[] payoutTxSignature) {
        this.payoutTxSignature = payoutTxSignature;
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }

    @Override
    public void persist() {
    }

    @Override
    public void onComplete() {
    }

    public AddressService getAddressService() {
        return addressService;
    }

    public PubKeyRing getPubKeyRing() {
        return keyRing.getPubKeyRing();
    }

    public CryptoService getCryptoService() {
        return cryptoService;
    }

    public void setCryptoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }
}
