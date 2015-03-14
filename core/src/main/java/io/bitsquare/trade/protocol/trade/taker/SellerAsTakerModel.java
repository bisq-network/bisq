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
import io.bitsquare.trade.protocol.trade.OfferSharedModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerModel extends OfferSharedModel {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerModel.class);

    // provided
    private final Trade trade;

    // written/read by task
    private Peer offerer;
    private Transaction preparedDepositTx;
    private Transaction depositTx;
    private Transaction signedTakerDepositTx;
    private Transaction payoutTx;
    private String payoutTxAsHex;
    private Coin takerPaybackAmount;
    private byte[] offererPubKey;
    private long offererTxOutIndex;
    private ECKey.ECDSASignature offererSignature;
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
        takerPubKey = walletService.getAddressInfo(trade.getId()).getPubKey();
    }

    // getter/setter
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

    public String getPayoutTxAsHex() {
        return payoutTxAsHex;
    }

    public void setPayoutTxAsHex(String payoutTxAsHex) {
        this.payoutTxAsHex = payoutTxAsHex;
    }

    public byte[] getOffererPubKey() {
        return offererPubKey;
    }

    public void setOffererPubKeyAsHex(byte[] offererPubKey) {
        this.offererPubKey = offererPubKey;
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
