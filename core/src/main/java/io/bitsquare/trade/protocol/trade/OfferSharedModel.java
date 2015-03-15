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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.user.User;
import io.bitsquare.util.taskrunner.SharedModel;

import org.bitcoinj.core.ECKey;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferSharedModel extends SharedModel {
    protected static final Logger log = LoggerFactory.getLogger(OfferSharedModel.class);

    // provided
    protected final Offer offer;
    protected final TradeMessageService tradeMessageService;
    protected final WalletService walletService;
    protected final BlockChainService blockChainService;
    protected final SignatureService signatureService;

    // derived
    protected final BankAccount bankAccount;
    protected final String accountId;
    protected final PublicKey messagePublicKey;
    protected final ECKey accountKey;
    protected final byte[] arbitratorPubKey;

    // lazy initialized at first read access, as we don't want to create an entry before it is really needed
    protected AddressEntry addressInfo;

    // data written/read by tasks
    protected TradeMessage tradeMessage;
    protected byte[] takerPubKey;
    protected String peersAccountId;
    protected BankAccount peersBankAccount;

    public OfferSharedModel(Offer offer,
                            TradeMessageService tradeMessageService,
                            WalletService walletService,
                            BlockChainService blockChainService,
                            SignatureService signatureService,
                            User user) {
        this.offer = offer;
        this.tradeMessageService = tradeMessageService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;

        //TODO use default arbitrator for now
        arbitratorPubKey = offer.getArbitrators().get(0).getPubKey();
        bankAccount = user.getBankAccount(offer.getBankAccountId());
        accountId = user.getAccountId();
        messagePublicKey = user.getMessagePublicKey();
        accountKey = walletService.getRegistrationAddressEntry().getKey();
    }

    // getter/setter
    public AddressEntry getAddressInfo() {
        if (addressInfo == null)
            addressInfo = getWalletService().getAddressInfo(offer.getId());

        return addressInfo;
    }

    public String getPeersAccountId() {
        return peersAccountId;
    }

    public void setPeersAccountId(String peersAccountId) {
        this.peersAccountId = peersAccountId;
    }

    public BankAccount getPeersBankAccount() {
        return peersBankAccount;
    }

    public void setPeersBankAccount(BankAccount peersBankAccount) {
        this.peersBankAccount = peersBankAccount;
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

    public byte[] getArbitratorPubKey() {
        return arbitratorPubKey;
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

    public ECKey getAccountKey() {
        return accountKey;
    }

    public byte[] getTakerPubKey() {
        return takerPubKey;
    }

    public void setTakerPubKey(byte[] takerPubKey) {
        this.takerPubKey = takerPubKey;
    }

    public String getTakerAccountId() {
        return peersAccountId;
    }

    public void setTakerAccountId(String peersAccountId) {
        this.peersAccountId = peersAccountId;
    }

    public BankAccount getTakerBankAccount() {
        return peersBankAccount;
    }

    public void setTakerBankAccount(BankAccount peersBankAccount) {
        this.peersBankAccount = peersBankAccount;
    }

    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

}
