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

package io.bitsquare.trade.protocol.trade.taker.messages;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.trade.protocol.trade.TradeMessage;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

public class RequestOffererPublishDepositTxMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = 2179683654379803071L;

    private final String tradeId;
    private final BankAccount bankAccount;
    private final String accountID;
    private final PublicKey takerMessagePublicKey;
    private final String contractAsJson;
    private final String takerContractSignature;
    private final String takerPayoutAddress;
    private final Transaction takersDepositTx;
    private final List<TransactionOutput> takerConnectedOutputsForAllInputs;
    private final List<TransactionOutput> takerOutputs;

    public RequestOffererPublishDepositTxMessage(String tradeId,
                                                 BankAccount bankAccount,
                                                 String accountID,
                                                 PublicKey takerMessagePublicKey,
                                                 String contractAsJson,
                                                 String takerContractSignature,
                                                 String takerPayoutAddress,
                                                 Transaction takersDepositTx,
                                                 List<TransactionOutput> takerConnectedOutputsForAllInputs,
                                                 List<TransactionOutput> takerOutputs) {
        this.tradeId = tradeId;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.takerMessagePublicKey = takerMessagePublicKey;
        this.contractAsJson = contractAsJson;
        this.takerContractSignature = takerContractSignature;
        this.takerPayoutAddress = takerPayoutAddress;
        this.takersDepositTx = takersDepositTx;
        this.takerConnectedOutputsForAllInputs = takerConnectedOutputsForAllInputs;
        this.takerOutputs = takerOutputs;
    }


    @Override
    public String getTradeId() {
        return tradeId;
    }

    public BankAccount getTakerBankAccount() {
        return bankAccount;
    }

    public String getTakerAccountId() {
        return accountID;
    }

    public PublicKey getTakerMessagePublicKey() {
        return takerMessagePublicKey;
    }

    public String getTakerContractAsJson() {
        return contractAsJson;
    }

    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public List<TransactionOutput> getTakerOutputs() {
        return takerOutputs;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public String getAccountID() {
        return accountID;
    }

    public String getContractAsJson() {
        return contractAsJson;
    }

    public String getTakerPayoutAddress() {
        return takerPayoutAddress;
    }

    public Transaction getTakersDepositTx() {
        return takersDepositTx;
    }

    public List<TransactionOutput> getTakerConnectedOutputsForAllInputs() {
        return takerConnectedOutputsForAllInputs;
    }
}
