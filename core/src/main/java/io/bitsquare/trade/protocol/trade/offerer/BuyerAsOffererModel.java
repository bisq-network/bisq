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
import io.bitsquare.trade.protocol.trade.TradeSharedModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerAsOffererModel extends TradeSharedModel {

    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererModel.class);


    // provided
    private final OpenOffer openOffer;

    // derived
    private final String offererPaybackAddress;

    // data written/read by tasks
    private Trade trade;
    private Peer peer;

    private String preparedOffererDepositTxAsHex;
    private String depositTxAsHex;

    private String peersAccountId;
    private BankAccount peersBankAccount;
    private PublicKey peersMessagePublicKey;
    private String peersContractAsJson;

    private String signedTakerDepositTxAsHex;

    private String txConnOutAsHex;
    private String txScriptSigAsHex;

    private long takerTxOutIndex;
    private Coin takerPaybackAmount;
    private String takeOfferFeeTxId;
    private String tradePubKeyAsHex;
    private String takerPayoutAddress;

    private long offererTxOutIndex;
    private String offererPubKey;
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

        offererPaybackAddress = walletService.getAddressInfoByTradeID(offer.getId()).getAddressString();
    }

    //getter/setter
    public OpenOffer getOpenOffer() {
        return openOffer;
    }

    public Peer getPeer() {
        return peer;
    }

    public String getOffererPaybackAddress() {
        return offererPaybackAddress;
    }

    public String getPreparedOffererDepositTxAsHex() {
        return preparedOffererDepositTxAsHex;
    }

    public void setPreparedOffererDepositTxAsHex(String preparedOffererDepositTxAsHex) {
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
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

    @Override
    public String getTradePubKeyAsHex() {
        return tradePubKeyAsHex;
    }

    @Override
    public void setTradePubKeyAsHex(String tradePubKeyAsHex) {
        this.tradePubKeyAsHex = tradePubKeyAsHex;
    }

    public String getTakerPayoutAddress() {
        return takerPayoutAddress;
    }

    public void setTakerPayoutAddress(String takerPayoutAddress) {
        this.takerPayoutAddress = takerPayoutAddress;
    }

    @Override
    public String getPeersAccountId() {
        return peersAccountId;
    }

    @Override
    public void setPeersAccountId(String peersAccountId) {
        this.peersAccountId = peersAccountId;
    }

    @Override
    public BankAccount getPeersBankAccount() {
        return peersBankAccount;
    }

    @Override
    public void setPeersBankAccount(BankAccount peersBankAccount) {
        this.peersBankAccount = peersBankAccount;
    }

    public PublicKey getPeersMessagePublicKey() {
        return peersMessagePublicKey;
    }

    public void setPeersMessagePublicKey(PublicKey peersMessagePublicKey) {
        this.peersMessagePublicKey = peersMessagePublicKey;
    }

    public String getPeersContractAsJson() {
        return peersContractAsJson;
    }

    public void setPeersContractAsJson(String peersContractAsJson) {
        this.peersContractAsJson = peersContractAsJson;
    }

    public String getSignedTakerDepositTxAsHex() {
        return signedTakerDepositTxAsHex;
    }

    public void setSignedTakerDepositTxAsHex(String signedTakerDepositTxAsHex) {
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
    }

    public String getTxConnOutAsHex() {
        return txConnOutAsHex;
    }

    public void setTxConnOutAsHex(String txConnOutAsHex) {
        this.txConnOutAsHex = txConnOutAsHex;
    }

    public String getTxScriptSigAsHex() {
        return txScriptSigAsHex;
    }

    public void setTxScriptSigAsHex(String txScriptSigAsHex) {
        this.txScriptSigAsHex = txScriptSigAsHex;
    }

    public long getTakerTxOutIndex() {
        return takerTxOutIndex;
    }

    public void setTakerTxOutIndex(long takerTxOutIndex) {
        this.takerTxOutIndex = takerTxOutIndex;
    }

    public String getOffererPubKey() {
        return offererPubKey;
    }

    public void setOffererPubKey(String offererPubKey) {
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

    public void setPeer(Peer peer) {
        this.peer = peer;
    }
}
