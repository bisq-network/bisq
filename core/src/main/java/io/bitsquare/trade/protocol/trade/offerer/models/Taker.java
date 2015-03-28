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

package io.bitsquare.trade.protocol.trade.offerer.models;

import io.bitsquare.fiat.FiatAccount;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.security.PublicKey;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Taker implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(Taker.class);

    // Mutable
    private String accountId;
    private FiatAccount fiatAccount;
    private PublicKey p2pSigPublicKey;
    private PublicKey p2pEncryptPubKey;
    private String contractAsJson;
    private String contractSignature;
    private Coin payoutAmount;
    private Transaction preparedDepositTx;
    private List<TransactionOutput> connectedOutputsForAllInputs;
    private String payoutAddressString;
    private byte[] tradeWalletPubKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Taker() {
        log.trace("Created by constructor");
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public FiatAccount getFiatAccount() {
        return fiatAccount;
    }

    public void setFiatAccount(FiatAccount fiatAccount) {
        this.fiatAccount = fiatAccount;
    }

    public PublicKey getP2pSigPublicKey() {
        return p2pSigPublicKey;
    }

    public void setP2pSigPublicKey(PublicKey p2pSigPublicKey) {
        this.p2pSigPublicKey = p2pSigPublicKey;
    }

    public PublicKey getP2pEncryptPubKey() {
        return p2pEncryptPubKey;
    }

    public void setP2pEncryptPubKey(PublicKey p2pEncryptPubKey) {
        this.p2pEncryptPubKey = p2pEncryptPubKey;
    }

    public String getContractAsJson() {
        return contractAsJson;
    }

    public void setContractAsJson(String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    public String getContractSignature() {
        return contractSignature;
    }

    public void setContractSignature(String contractSignature) {
        this.contractSignature = contractSignature;
    }

    public Coin getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(Coin payoutAmount) {
        this.payoutAmount = payoutAmount;
    }

    public Transaction getPreparedDepositTx() {
        return preparedDepositTx;
    }

    public void setPreparedDepositTx(Transaction preparedDepositTx) {
        this.preparedDepositTx = preparedDepositTx;
    }

    public List<TransactionOutput> getConnectedOutputsForAllInputs() {
        return connectedOutputsForAllInputs;
    }

    public void setConnectedOutputsForAllInputs(List<TransactionOutput> connectedOutputsForAllInputs) {
        this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
    }

    public String getPayoutAddressString() {
        return payoutAddressString;
    }

    public void setPayoutAddressString(String payoutAddressString) {
        this.payoutAddressString = payoutAddressString;
    }

    public byte[] getTradeWalletPubKey() {
        return tradeWalletPubKey;
    }

    public void setTradeWalletPubKey(byte[] tradeWalletPubKey) {
        this.tradeWalletPubKey = tradeWalletPubKey;
    }


    @Override
    public String toString() {
        return "Taker{" +
                "accountId='" + accountId + '\'' +
                ", fiatAccount=" + fiatAccount +
                ", p2pSigPublicKey=" + p2pSigPublicKey +
                ", p2pEncryptPubKey=" + p2pEncryptPubKey +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", contractSignature='" + contractSignature + '\'' +
                ", payoutAmount=" + payoutAmount +
                ", preparedDepositTx=" + preparedDepositTx +
                ", connectedOutputsForAllInputs=" + connectedOutputsForAllInputs +
                ", payoutAddressString='" + payoutAddressString + '\'' +
                ", tradeWalletPubKey=" + Arrays.toString(tradeWalletPubKey) +
                '}';
    }
}
