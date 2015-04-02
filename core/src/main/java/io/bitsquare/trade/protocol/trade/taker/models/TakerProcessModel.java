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

package io.bitsquare.trade.protocol.trade.taker.models;

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.shared.models.TradingPeer;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import java.io.IOException;
import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fully serializable, no transient fields
 * <p/>
 * Holds all data which are needed between tasks. All relevant data for the trade itself are stored in Trade.
 */
public class TakerProcessModel extends ProcessModel implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TakerProcessModel.class);

    // Immutable
    public final TradingPeer tradingPeer;

    // Transient/Immutable
    transient private Offer offer;
    transient private WalletService walletService;
    transient private User user;

    // Mutable
    private Transaction takeOfferFeeTx;
    private Transaction payoutTx;
    private List<TransactionOutput> connectedOutputsForAllInputs;
    private Coin payoutAmount;
    private Transaction preparedDepositTx;
    private List<TransactionOutput> outputs; // used to verify amounts with change outputs
    private byte[] payoutTxSignature;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerProcessModel() {
        log.trace("Created by constructor");
        tradingPeer = new TradingPeer();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");
    }

    @Override
    public void onAllServicesInitialized(Offer offer,
                                         MessageService messageService,
                                         WalletService walletService,
                                         TradeWalletService tradeWalletService,
                                         BlockChainService blockChainService,
                                         SignatureService signatureService,
                                         ArbitrationRepository arbitrationRepository,
                                         User user) {
        log.trace("onAllServicesInitialized");
        super.onAllServicesInitialized(offer,
                messageService,
                walletService,
                tradeWalletService,
                blockChainService,
                signatureService,
                arbitrationRepository,
                user);

        this.offer = offer;
        this.walletService = walletService;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
    }

    @Nullable
    public Transaction getTakeOfferFeeTx() {
        return takeOfferFeeTx;
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FiatAccount getFiatAccount() {
        return user.getFiatAccount(offer.getBankAccountId());
    }

    public DeterministicKey getRegistrationKeyPair() {
        return walletService.getRegistrationAddressEntry().getKeyPair();
    }

    public String getAccountId() {
        return user.getAccountId();
    }

    public PublicKey getP2pSigPubKey() {
        return user.getP2PSigPubKey();
    }

    public PublicKey getP2pEncryptPublicKey() {
        return user.getP2PEncryptPubKey();
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

    public PublicKey getP2pEncryptPubKey() {
        return user.getP2PEncryptPubKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public List<TransactionOutput> getConnectedOutputsForAllInputs() {
        return connectedOutputsForAllInputs;
    }

    public void setConnectedOutputsForAllInputs(List<TransactionOutput> connectedOutputsForAllInputs) {
        this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
    }

    @Nullable
    public Coin getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(Coin payoutAmount) {
        this.payoutAmount = payoutAmount;
    }

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


}
