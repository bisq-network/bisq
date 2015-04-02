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

public class TradingPeer implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TradingPeer.class);

    // Mutable
    private String accountId;
    private PublicKey p2pSigPubKey;
    private PublicKey p2pEncryptPubKey;
    private byte[] tradeWalletPubKey;
    private FiatAccount fiatAccount;
    private Transaction preparedDepositTx;
    private List<TransactionOutput> connectedOutputsForAllInputs;
    private List<TransactionOutput> outputs;
    private Coin payoutAmount;
    private String payoutAddressString;
    private byte[] signature;
    private String contractAsJson;
    private String contractSignature;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradingPeer() {
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

    public PublicKey getP2pSigPubKey() {
        return p2pSigPubKey;
    }

    public void setP2pSigPubKey(PublicKey p2pSigPubKey) {
        this.p2pSigPubKey = p2pSigPubKey;
    }

    public PublicKey getP2pEncryptPubKey() {
        return p2pEncryptPubKey;
    }

    public void setP2pEncryptPubKey(PublicKey p2pEncryptPubKey) {
        this.p2pEncryptPubKey = p2pEncryptPubKey;
    }

    public byte[] getTradeWalletPubKey() {
        return tradeWalletPubKey;
    }

    public void setTradeWalletPubKey(byte[] tradeWalletPubKey) {
        this.tradeWalletPubKey = tradeWalletPubKey;
    }

    public FiatAccount getFiatAccount() {
        return fiatAccount;
    }

    public void setFiatAccount(FiatAccount fiatAccount) {
        this.fiatAccount = fiatAccount;
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

    public List<TransactionOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<TransactionOutput> outputs) {
        this.outputs = outputs;
    }

    public Coin getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(Coin payoutAmount) {
        this.payoutAmount = payoutAmount;
    }

    public String getPayoutAddressString() {
        return payoutAddressString;
    }

    public void setPayoutAddressString(String payoutAddressString) {
        this.payoutAddressString = payoutAddressString;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
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

    @Override
    public String toString() {
        return "TradingPeer{" +
                "accountId='" + accountId + '\'' +
                ", p2pSigPubKey=" + p2pSigPubKey +
                ", p2pEncryptPubKey=" + p2pEncryptPubKey +
                ", tradeWalletPubKey=" + Arrays.toString(tradeWalletPubKey) +
                ", fiatAccount=" + fiatAccount +
                ", preparedDepositTx=" + preparedDepositTx +
                ", connectedOutputsForAllInputs=" + connectedOutputsForAllInputs +
                ", outputs=" + outputs +
                ", payoutAmount=" + payoutAmount +
                ", payoutAddressString='" + payoutAddressString + '\'' +
                ", signature=" + Arrays.toString(signature) +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", contractSignature='" + contractSignature + '\'' +
                '}';
    }

}
