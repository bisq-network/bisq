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

import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.TradeSharedModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerModel extends TradeSharedModel {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerModel.class);

    // provided
    private final Trade trade;

    // derived
    private final Coin tradeAmount;
    private final Coin securityDeposit;
    private final PublicKey offererMessagePublicKey;

    // written/read by task
    private Peer peer;

    private String preparedPeersDepositTxAsHex;
    private String depositTxAsHex;
    private Transaction signedTakerDepositTx;
    private Transaction payoutTx;
    private String payoutTxAsHex;

    private Coin takerPaybackAmount;

    private String peersPubKey;
    private long peersTxOutIndex;
    private String offererSignatureR;
    private String offererSignatureS;
    private Coin offererPaybackAmount;
    private String offererPayoutAddress;

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
        tradeAmount = trade.getTradeAmount();
        securityDeposit = trade.getSecurityDeposit();
        offererMessagePublicKey = offer.getMessagePublicKey();
        tradePubKeyAsHex = walletService.getAddressInfoByTradeID(trade.getId()).getPubKeyAsHexString();
    }

    // getter/setter
    public Trade getTrade() {
        return trade;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public Coin getSecurityDeposit() {
        return securityDeposit;
    }

    public PublicKey getOffererMessagePublicKey() {
        return offererMessagePublicKey;
    }

    public Peer getPeer() {
        return peer;
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
    }

    public String getPayoutTxAsHex() {
        return payoutTxAsHex;
    }

    public void setPayoutTxAsHex(String payoutTxAsHex) {
        this.payoutTxAsHex = payoutTxAsHex;
    }

    public String getPeersPubKey() {
        return peersPubKey;
    }

    public void setPeersPubKey(String peersPubKey) {
        this.peersPubKey = peersPubKey;
    }

    public String getPreparedPeersDepositTxAsHex() {
        return preparedPeersDepositTxAsHex;
    }

    public void setPreparedPeersDepositTxAsHex(String preparedPeersDepositTxAsHex) {
        this.preparedPeersDepositTxAsHex = preparedPeersDepositTxAsHex;
    }

    public long getPeersTxOutIndex() {
        return peersTxOutIndex;
    }

    public void setPeersTxOutIndex(long peersTxOutIndex) {
        this.peersTxOutIndex = peersTxOutIndex;
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


}
