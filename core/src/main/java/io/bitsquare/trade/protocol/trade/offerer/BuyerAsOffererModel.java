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

package io.bitsquare.trade.protocol.trade.offerer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.OpenOffer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.OfferSharedModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.security.PublicKey;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerAsOffererModel extends OfferSharedModel {

    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererModel.class);


    // provided
    private final OpenOffer openOffer;

    // derived
    private final String offererPaybackAddress;

    // data written/read by tasks
    private Trade trade;
    private Peer taker;

    private String takerAccountId;
    private BankAccount takerBankAccount;
    private PublicKey takerMessagePublicKey;
    private String takerContractAsJson;

    private Coin takerPaybackAmount;
    private String takeOfferFeeTxId;

    private byte[] offererPubKey;
    private ECKey.ECDSASignature offererSignature;
    private Coin offererPaybackAmount;
    private List<TransactionOutput> offererConnectedOutputsForAllInputs;
    private List<TransactionOutput> offererOutputs;
    private Transaction takerDepositTx;
    private List<TransactionOutput> takerConnectedOutputsForAllInputs;
    private List<TransactionOutput> takerOutputs;
    private String takerPayoutAddress;
    private Transaction offererPayoutTx;
    private Transaction publishedDepositTx;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererModel(OpenOffer openOffer,
                               TradeMessageService tradeMessageService,
                               WalletService walletService,
                               BlockChainService blockChainService,
                               SignatureService signatureService,
                               User user) {
        super(openOffer.getOffer(),
                tradeMessageService,
                walletService,
                blockChainService,
                signatureService,
                user);
        this.openOffer = openOffer;

        offererPaybackAddress = walletService.getAddressInfo(offer.getId()).getAddressString();
    }

    //getter/setter
    public OpenOffer getOpenOffer() {
        return openOffer;
    }

    public Peer getTaker() {
        return taker;
    }

    public String getOffererPaybackAddress() {
        return offererPaybackAddress;
    }


    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }

    @Override
    public String getTakerAccountId() {
        return takerAccountId;
    }

    @Override
    public void setTakerAccountId(String takerAccountId) {
        this.takerAccountId = takerAccountId;
    }

    @Override
    public BankAccount getTakerBankAccount() {
        return takerBankAccount;
    }

    @Override
    public void setTakerBankAccount(BankAccount takerBankAccount) {
        this.takerBankAccount = takerBankAccount;
    }

    public PublicKey getTakerMessagePublicKey() {
        return takerMessagePublicKey;
    }

    public void setTakerMessagePublicKey(PublicKey takerMessagePublicKey) {
        this.takerMessagePublicKey = takerMessagePublicKey;
    }

    public String getTakerContractAsJson() {
        return takerContractAsJson;
    }

    public void setTakerContractAsJson(String takerContractAsJson) {
        this.takerContractAsJson = takerContractAsJson;
    }


    public byte[] getOffererPubKey() {
        return offererPubKey;
    }

    public void setOffererPubKey(byte[] offererPubKey) {
        this.offererPubKey = offererPubKey;
    }


    public ECKey.ECDSASignature getOffererSignature() {
        return offererSignature;
    }

    public void setOffererSignature(ECKey.ECDSASignature offererSignature) {
        this.offererSignature = offererSignature;
    }

    public Coin getOffererPaybackAmount() {
        return offererPaybackAmount;
    }

    public void setOffererPaybackAmount(Coin offererPaybackAmount) {
        this.offererPaybackAmount = offererPaybackAmount;
    }

    public Coin getTakerPaybackAmount() {
        return takerPaybackAmount;
    }

    public void setTakerPaybackAmount(Coin takerPaybackAmount) {
        this.takerPaybackAmount = takerPaybackAmount;
    }

    public void setTrade(Trade trade) {
        this.trade = trade;
    }

    public Trade getTrade() {
        return trade;
    }

    public void setTaker(Peer taker) {
        this.taker = taker;
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

    public void setTakerDepositTx(Transaction takerDepositTx) {
        this.takerDepositTx = takerDepositTx;
    }

    public Transaction getTakerDepositTx() {
        return takerDepositTx;
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

    public String getTakerPayoutAddress() {
        return takerPayoutAddress;
    }

    public void setTakerPayoutAddress(String takerPayoutAddress) {
        this.takerPayoutAddress = takerPayoutAddress;
    }

    public void setOffererPayoutTx(Transaction offererPayoutTx) {
        this.offererPayoutTx = offererPayoutTx;
    }

    public Transaction getOffererPayoutTx() {
        return offererPayoutTx;
    }

    public void setPublishedDepositTx(Transaction publishedDepositTx) {
        this.publishedDepositTx = publishedDepositTx;
    }

    public Transaction getPublishedDepositTx() {
        return publishedDepositTx;
    }
}
