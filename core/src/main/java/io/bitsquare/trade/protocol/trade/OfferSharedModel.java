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
import io.bitsquare.btc.TradeService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.user.User;
import io.bitsquare.util.taskrunner.SharedModel;

import org.bitcoinj.crypto.DeterministicKey;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferSharedModel extends SharedModel {
    protected static final Logger log = LoggerFactory.getLogger(OfferSharedModel.class);

    // provided
    private final Offer offer;
    private final TradeMessageService tradeMessageService;
    private final WalletService walletService;
    private final BlockChainService blockChainService;
    private final SignatureService signatureService;


    // derived
    private final String id;
    private final BankAccount bankAccount;
    private final String accountId;
    private final PublicKey networkPubKey;
    private final byte[] registrationPubKey;
    private final DeterministicKey registrationKeyPair;
    private final byte[] arbitratorPubKey;
    private final AddressEntry addressEntry;
    private final TradeService tradeService;

    // data written/read by tasks
    private TradeMessage tradeMessage;


    protected OfferSharedModel(Offer offer,
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

        id = offer.getId();
        tradeService = walletService.getTradeService();
        //TODO use default arbitrator for now
        arbitratorPubKey = offer.getArbitrators().get(0).getPubKey();
        registrationPubKey = walletService.getRegistrationAddressEntry().getPubKey();
        registrationKeyPair = walletService.getRegistrationAddressEntry().getKeyPair();
        addressEntry = walletService.getAddressEntry(id);
        bankAccount = user.getBankAccount(offer.getBankAccountId());
        accountId = user.getAccountId();
        networkPubKey = user.getNetworkPubKey();
    }

    // getter/setter

    public String getId() {
        return id;
    }

    public TradeService getTradeService() {
        return tradeService;
    }

    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

    public Offer getOffer() {
        return offer;
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

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public String getAccountId() {
        return accountId;
    }

    public PublicKey getNetworkPubKey() {
        return networkPubKey;
    }

    public byte[] getRegistrationPubKey() {
        return registrationPubKey;
    }

    public DeterministicKey getRegistrationKeyPair() {
        return registrationKeyPair;
    }

    public byte[] getArbitratorPubKey() {
        return arbitratorPubKey;
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
    }

}
