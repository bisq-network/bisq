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

package io.bitsquare.trade.protocol.trade.taker.models;

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

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fully serializable, no transient fields
 */
public class Offerer implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(Offerer.class);

    // Mutable
    private byte[] tradeWalletPubKey;
    private Coin payoutAmount;
    private String payoutAddressString;
    private List<TransactionOutput> connectedOutputsForAllInputs;
    private List<TransactionOutput> outputs;
    private byte[] signature;
    private FiatAccount fiatAccount;
    private String accountId;
    private PublicKey p2pEncryptPubKey;
    private String contractAsJson;
    private String contractSignature;
    private Transaction preparedDepositTx;
    private PublicKey p2pSigPubKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offerer() {
        log.trace("Created by constructor");
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public byte[] getTradeWalletPubKey() {
        return tradeWalletPubKey;
    }

    public void setTradeWalletPubKey(byte[] tradeWalletPubKey) {
        this.tradeWalletPubKey = tradeWalletPubKey;
    }

    @Nullable
    public Coin getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(Coin payoutAmount) {
        this.payoutAmount = payoutAmount;
    }

    @Nullable
    public String getPayoutAddressString() {
        return payoutAddressString;
    }

    public void setPayoutAddressString(String payoutAddressString) {
        this.payoutAddressString = payoutAddressString;
    }

    @Nullable
    public List<TransactionOutput> getConnectedOutputsForAllInputs() {
        return connectedOutputsForAllInputs;
    }

    public void setConnectedOutputsForAllInputs(List<TransactionOutput> connectedOutputsForAllInputs) {
        this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
    }

    @Nullable
    public List<TransactionOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<TransactionOutput> outputs) {
        this.outputs = outputs;
    }

    @Nullable
    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Nullable
    public FiatAccount getFiatAccount() {
        return fiatAccount;
    }

    public void setFiatAccount(FiatAccount fiatAccount) {
        this.fiatAccount = fiatAccount;
    }

    @Nullable
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    @Nullable
    public PublicKey getP2pEncryptPubKey() {
        return p2pEncryptPubKey;
    }

    public void setP2pEncryptPubKey(PublicKey p2pEncryptPubKey) {
        this.p2pEncryptPubKey = p2pEncryptPubKey;
    }

    @Nullable
    public String getContractAsJson() {
        return contractAsJson;
    }

    public void setContractAsJson(String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    @Nullable
    public String getContractSignature() {
        return contractSignature;
    }

    public void setContractSignature(String contractSignature) {
        this.contractSignature = contractSignature;
    }

    @Nullable
    public Transaction getPreparedDepositTx() {
        return preparedDepositTx;
    }

    public void setPreparedDepositTx(Transaction preparedDepositTx) {
        this.preparedDepositTx = preparedDepositTx;
    }

    @Nullable
    public PublicKey getP2pSigPubKey() {
        return p2pSigPubKey;
    }

    public void setP2pSigPubKey(PublicKey p2pSigPubKey) {
        this.p2pSigPubKey = p2pSigPubKey;
    }

    @Override
    public String toString() {
        return "Offerer{" +
                "tradeWalletPubKey=" + Arrays.toString(tradeWalletPubKey) +
                ", payoutAmount=" + payoutAmount +
                ", payoutAddressString='" + payoutAddressString + '\'' +
                ", connectedOutputsForAllInputs=" + connectedOutputsForAllInputs +
                ", outputs=" + outputs +
                ", signature=" + Arrays.toString(signature) +
                ", fiatAccount=" + fiatAccount +
                ", accountId='" + accountId + '\'' +
                ", p2pSigPubKey=" + p2pSigPubKey +
                ", p2pEncryptPubKey=" + p2pEncryptPubKey +
                '}';
    }
}
