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

package io.bitsquare.trade.protocol.trade.taker;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.OfferSharedModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerModel extends OfferSharedModel {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerModel.class);

    // provided
    private final Trade trade;


    // derived
    private final byte[] takerPubKey;

    // written/read by task
    private Peer offerer;
    private Transaction preparedDepositTx;
    private Transaction depositTx;
    private Transaction signedTakerDepositTx;
    private Transaction payoutTx;
    private Coin takerPayoutAmount;
    private byte[] offererPubKey;
    private long offererTxOutIndex;
    private Coin offererPayoutAmount;
    private String offererPayoutAddress;
    private List<TransactionOutput> offererConnectedOutputsForAllInputs;
    private List<TransactionOutput> offererOutputs;
    private List<TransactionOutput> takerConnectedOutputsForAllInputs;
    private List<TransactionOutput> takerOutputs;
    private Transaction takerDepositTx;
    private Transaction publishedDepositTx;
    private BankAccount takerBankAccount;
    private String takerAccountId;
    private ECKey.ECDSASignature offererSignature;

    public SellerAsTakerModel(Trade trade,
                              TradeMessageService tradeMessageService,
                              WalletService walletService,
                              BlockChainService blockChainService,
                              SignatureService signatureService,
                              User user) {
        super(trade.getOffer(),
                tradeMessageService,
                walletService,
                blockChainService,
                signatureService,
                user);

        this.trade = trade;
        takerPubKey = getAddressEntry().getPubKey();
    }

    // getter/setter
    public byte[] getTakerPubKey() {
        return takerPubKey;
    }

    public void setOffererPubKey(byte[] offererPubKey) {
        this.offererPubKey = offererPubKey;
    }

    public List<TransactionOutput> getOffererConnectedOutputsForAllInputs() {
        return offererConnectedOutputsForAllInputs;
    }

    public void setOffererConnectedOutputsForAllInputs(List<TransactionOutput> offererConnectedOutputsForAllInputs) {
        this.offererConnectedOutputsForAllInputs = offererConnectedOutputsForAllInputs;
    }

    public List<TransactionOutput> getOffererOutputs() {
        return offererOutputs;
    }

    public void setOffererOutputs(List<TransactionOutput> offererOutputs) {
        this.offererOutputs = offererOutputs;
    }

    public Trade getTrade() {
        return trade;
    }

    public Peer getOfferer() {
        return offerer;
    }

    public void setOfferer(Peer offerer) {
        this.offerer = offerer;
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
    }

    public byte[] getOffererPubKey() {
        return offererPubKey;
    }


    public Transaction getPreparedDepositTx() {
        return preparedDepositTx;
    }

    public void setPreparedDepositTx(Transaction preparedDepositTx) {
        this.preparedDepositTx = preparedDepositTx;
    }

    public long getOffererTxOutIndex() {
        return offererTxOutIndex;
    }

    public void setOffererTxOutIndex(long offererTxOutIndex) {
        this.offererTxOutIndex = offererTxOutIndex;
    }

    public Transaction getDepositTx() {
        return depositTx;
    }

    public void setDepositTx(Transaction depositTx) {
        this.depositTx = depositTx;
    }

    public Coin getOffererPayoutAmount() {
        return offererPayoutAmount;
    }

    public void setOffererPayoutAmount(Coin offererPayoutAmount) {
        this.offererPayoutAmount = offererPayoutAmount;
    }

    public Coin getTakerPayoutAmount() {
        return takerPayoutAmount;
    }

    public void setTakerPayoutAmount(Coin takerPayoutAmount) {
        this.takerPayoutAmount = takerPayoutAmount;
    }

    public String getOffererPayoutAddress() {
        return offererPayoutAddress;
    }

    public void setOffererPayoutAddress(String offererPayoutAddress) {
        this.offererPayoutAddress = offererPayoutAddress;
    }

    public Transaction getSignedTakerDepositTx() {
        return signedTakerDepositTx;
    }

    public void setSignedTakerDepositTx(Transaction signedTakerDepositTx) {
        this.signedTakerDepositTx = signedTakerDepositTx;
    }


    public void setTakerConnectedOutputsForAllInputs(List<TransactionOutput> takerConnectedOutputsForAllInputs) {
        this.takerConnectedOutputsForAllInputs = takerConnectedOutputsForAllInputs;
    }

    public List<TransactionOutput> getTakerConnectedOutputsForAllInputs() {
        return takerConnectedOutputsForAllInputs;
    }

    public void setTakerOutputs(List<TransactionOutput> takerOutputs) {
        this.takerOutputs = takerOutputs;
    }

    public List<TransactionOutput> getTakerOutputs() {
        return takerOutputs;
    }

    public void setTakerDepositTx(Transaction takerDepositTx) {
        this.takerDepositTx = takerDepositTx;
    }

    public Transaction getTakerDepositTx() {
        return takerDepositTx;
    }

    public void setPublishedDepositTx(Transaction publishedDepositTx) {
        this.publishedDepositTx = publishedDepositTx;
    }

    public Transaction getPublishedDepositTx() {
        return publishedDepositTx;
    }

    public void setTakerBankAccount(BankAccount takerBankAccount) {
        this.takerBankAccount = takerBankAccount;
    }

    public BankAccount getTakerBankAccount() {
        return takerBankAccount;
    }

    public void setTakerAccountId(String takerAccountId) {
        this.takerAccountId = takerAccountId;
    }

    public String getTakerAccountId() {
        return takerAccountId;
    }

    public ECKey.ECDSASignature getOffererSignature() {
        return offererSignature;
    }

    public void setOffererSignature(ECKey.ECDSASignature offererSignature) {
        this.offererSignature = offererSignature;
    }
}
