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
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.TradeMessage;
import io.bitsquare.trade.protocol.trade.TradeSharedModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerTakesOfferModel extends TradeSharedModel {
    private static final Logger log = LoggerFactory.getLogger(SellerTakesOfferModel.class);
    private Transaction payoutTx;
    private String payoutTxAsHex;

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setPayoutTxAsHex(String payoutTxAsHex) {
        this.payoutTxAsHex = payoutTxAsHex;
    }

    public String getPayoutTxAsHex() {
        return payoutTxAsHex;
    }

    public enum State {
        Init,
        GetPeerAddress,
        RequestTakeOffer,

        ValidateRespondToTakeOfferRequestMessage,
        PayTakeOfferFee,
        SendTakeOfferFeePayedMessage,

        ValidateTakerDepositPaymentRequestMessage,
        VerifyOffererAccount,
        CreateAndSignContract,
        PayDeposit,
        SendSignedTakerDepositTxAsHex,

        ValidateDepositTxPublishedMessage,
        TakerCommitDepositTx,
        handleBankTransferInitedMessage,
        SignAndPublishPayoutTx,
        SendPayoutTxToOfferer
    }

    // provided data
    private final TradeMessageService tradeMessageService;
    private final WalletService walletService;
    private final BlockChainService blockChainService;
    private final SignatureService signatureService;

    // derived
    private final Offer offer;
    private final String tradeId;
    private final BankAccount bankAccount;
    private final String accountId;
    private final PublicKey messagePublicKey;
    private final Coin tradeAmount;
    private final String tradePubKeyAsHex;
    private final ECKey accountKey;
    private final PublicKey offererMessagePublicKey;
    private final Coin securityDeposit;
    private final String arbitratorPubKey;


    // written/read by task
    private Peer peer;

    // written by messages, read by tasks
    private String peersAccountId;
    private BankAccount peersBankAccount;
    private String peersPubKey;
    private String preparedPeersDepositTxAsHex;
    private long peersTxOutIndex;

    private String depositTxAsHex;
    private String offererSignatureR;
    private String offererSignatureS;
    private Coin offererPaybackAmount;
    private Coin takerPaybackAmount;
    private String offererPayoutAddress;
    private Transaction signedTakerDepositTx;

    private TradeMessage tradeMessage;
    
    // state
    private State state;

    public SellerTakesOfferModel(Trade trade,
                                 TradeMessageService tradeMessageService,
                                 WalletService walletService,
                                 BlockChainService blockChainService,
                                 SignatureService signatureService,
                                 User user) {
        super(trade);
        this.tradeMessageService = tradeMessageService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;

        offer = trade.getOffer();
        tradeId = trade.getId();
        tradeAmount = trade.getTradeAmount();
        securityDeposit = trade.getSecurityDeposit();
        //TODO use 1. for now
        arbitratorPubKey = trade.getOffer().getArbitrators().get(0).getPubKeyAsHex();

        offererMessagePublicKey = offer.getMessagePublicKey();

        bankAccount = user.getCurrentBankAccount().get();
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();

        tradePubKeyAsHex = walletService.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();
        accountKey = walletService.getRegistrationAddressEntry().getKey();

        state = State.Init;
    }


    // Setters
    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    public void setPeersAccountId(String peersAccountId) {
        this.peersAccountId = peersAccountId;
    }

    public void setPeersBankAccount(BankAccount peersBankAccount) {
        this.peersBankAccount = peersBankAccount;
    }

    public void setPeersPubKey(String peersPubKey) {
        this.peersPubKey = peersPubKey;
    }

    public void setPreparedPeersDepositTxAsHex(String preparedPeersDepositTxAsHex) {
        this.preparedPeersDepositTxAsHex = preparedPeersDepositTxAsHex;
    }

    public void setPeersTxOutIndex(long peersTxOutIndex) {
        this.peersTxOutIndex = peersTxOutIndex;
    }

    public void setDepositTxAsHex(String depositTxAsHex) {
        this.depositTxAsHex = depositTxAsHex;
    }

    public void setOffererSignatureR(String offererSignatureR) {
        this.offererSignatureR = offererSignatureR;
    }

    public void setOffererSignatureS(String offererSignatureS) {
        this.offererSignatureS = offererSignatureS;
    }

    public void setOffererPaybackAmount(Coin offererPaybackAmount) {
        this.offererPaybackAmount = offererPaybackAmount;
    }

    public void setTakerPaybackAmount(Coin takerPaybackAmount) {
        this.takerPaybackAmount = takerPaybackAmount;
    }

    public void setOffererPayoutAddress(String offererPayoutAddress) {
        this.offererPayoutAddress = offererPayoutAddress;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }
    public void setSignedTakerDepositTx(Transaction signedTakerDepositTx) {
        this.signedTakerDepositTx = signedTakerDepositTx;
    }

    // Getters
    public Trade getTrade() {
        return trade;
    }

    public TradeMessageService getTradeMessageService() {
        return tradeMessageService;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public BlockChainService getBlockChainService() {
        return blockChainService;
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

    public Offer getOffer() {
        return offer;
    }

    public String getTradeId() {
        return tradeId;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public String getAccountId() {
        return accountId;
    }

    public PublicKey getMessagePublicKey() {
        return messagePublicKey;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public String getTradePubKeyAsHex() {
        return tradePubKeyAsHex;
    }

    public ECKey getAccountKey() {
        return accountKey;
    }

    public PublicKey getOffererMessagePublicKey() {
        return offererMessagePublicKey;
    }

    public Coin getSecurityDeposit() {
        return securityDeposit;
    }

    public String getArbitratorPubKey() {
        return arbitratorPubKey;
    }

    public Peer getPeer() {
        return peer;
    }

    public String getPeersAccountId() {
        return peersAccountId;
    }

    public BankAccount getPeersBankAccount() {
        return peersBankAccount;
    }

    public String getPeersPubKey() {
        return peersPubKey;
    }

    public String getPreparedPeersDepositTxAsHex() {
        return preparedPeersDepositTxAsHex;
    }

    public long getPeersTxOutIndex() {
        return peersTxOutIndex;
    }

    public String getDepositTxAsHex() {
        return depositTxAsHex;
    }

    public String getOffererSignatureR() {
        return offererSignatureR;
    }

    public String getOffererSignatureS() {
        return offererSignatureS;
    }

    public Coin getOffererPaybackAmount() {
        return offererPaybackAmount;
    }

    public Coin getTakerPaybackAmount() {
        return takerPaybackAmount;
    }

    public String getOffererPayoutAddress() {
        return offererPayoutAddress;
    }

    public State getState() {
        return state;
    }

    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }  public Transaction getSignedTakerDepositTx() {
        return signedTakerDepositTx;
    }
}
