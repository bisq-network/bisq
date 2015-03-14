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
import org.bitcoinj.core.Transaction;

import java.security.PublicKey;

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

    private Transaction preparedDepositTx;
    private String depositTxAsHex;

    private String takerAccountId;
    private BankAccount takerBankAccount;
    private PublicKey takerMessagePublicKey;
    private String takerContractAsJson;

    private Transaction takersSignedDepositTx;

    private Transaction takersFromTx;
    private byte[] txScriptSig;

    private long takerTxOutIndex;
    private Coin takerPaybackAmount;
    private String takeOfferFeeTxId;
    private String takerPayoutAddress;

    private long offererTxOutIndex;
    private byte[] offererPubKey;
    private String offererSignatureR;
    private String offererSignatureS;
    private Coin offererPaybackAmount;


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

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }

    public String getTakerPayoutAddress() {
        return takerPayoutAddress;
    }

    public void setTakerPayoutAddress(String takerPayoutAddress) {
        this.takerPayoutAddress = takerPayoutAddress;
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

    public Transaction getTakersSignedDepositTx() {
        return takersSignedDepositTx;
    }

    public void setTakersSignedDepositTx(Transaction takersSignedDepositTx) {
        this.takersSignedDepositTx = takersSignedDepositTx;
    }

    public Transaction getTakersFromTx() {
        return takersFromTx;
    }

    public void setTakersFromTx(Transaction takersFromTx) {
        this.takersFromTx = takersFromTx;
    }

    public byte[] getTxScriptSig() {
        return txScriptSig;
    }

    public void setTxScriptSig(byte[] txScriptSig) {
        this.txScriptSig = txScriptSig;
    }

    public long getTakerTxOutIndex() {
        return takerTxOutIndex;
    }

    public void setTakerTxOutIndex(long takerTxOutIndex) {
        this.takerTxOutIndex = takerTxOutIndex;
    }

    public byte[] getOffererPubKey() {
        return offererPubKey;
    }

    public void setOffererPubKey(byte[] offererPubKey) {
        this.offererPubKey = offererPubKey;
    }

    public String getDepositTxAsHex() {
        return depositTxAsHex;
    }

    public void setDepositTxAsHex(String depositTxAsHex) {
        this.depositTxAsHex = depositTxAsHex;
    }

    public String getOffererSignatureR() {
        return offererSignatureR;
    }

    public void setOffererSignatureR(String offererSignatureR) {
        this.offererSignatureR = offererSignatureR;
    }

    public String getOffererSignatureS() {
        return offererSignatureS;
    }

    public void setOffererSignatureS(String offererSignatureS) {
        this.offererSignatureS = offererSignatureS;
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
}
