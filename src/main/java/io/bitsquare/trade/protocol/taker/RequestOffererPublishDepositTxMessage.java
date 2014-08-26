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

package io.bitsquare.trade.protocol.taker;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.trade.protocol.TradeMessage;

import java.io.Serializable;
import java.security.PublicKey;

public class RequestOffererPublishDepositTxMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = 2179683654379803071L;
    private final String tradeId;
    private BankAccount bankAccount;
    private String accountID;
    private PublicKey takerMessagePublicKey;
    private String signedTakerDepositTxAsHex;
    private String txScriptSigAsHex;
    private String txConnOutAsHex;
    private String contractAsJson;
    private String takerContractSignature;
    private String takerPayoutAddress;
    private long takerTxOutIndex;


    private long offererTxOutIndex;

    public RequestOffererPublishDepositTxMessage(String tradeId,
                                                 BankAccount bankAccount,
                                                 String accountID,
                                                 PublicKey takerMessagePublicKey,
                                                 String signedTakerDepositTxAsHex,
                                                 String txScriptSigAsHex,
                                                 String txConnOutAsHex,
                                                 String contractAsJson,
                                                 String takerContractSignature,
                                                 String takerPayoutAddress,
                                                 long takerTxOutIndex,
                                                 long offererTxOutIndex) {

        this.tradeId = tradeId;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.takerMessagePublicKey = takerMessagePublicKey;
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
        this.txScriptSigAsHex = txScriptSigAsHex;
        this.txConnOutAsHex = txConnOutAsHex;
        this.contractAsJson = contractAsJson;
        this.takerContractSignature = takerContractSignature;
        this.takerPayoutAddress = takerPayoutAddress;
        this.takerTxOutIndex = takerTxOutIndex;
        this.offererTxOutIndex = offererTxOutIndex;
    }


    @Override
    public String getTradeId() {
        return tradeId;
    }

    public long getOffererTxOutIndex() {
        return offererTxOutIndex;
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

    public String getSignedTakerDepositTxAsHex() {
        return signedTakerDepositTxAsHex;
    }

    public String getTxScriptSigAsHex() {
        return txScriptSigAsHex;
    }

    public String getTxConnOutAsHex() {
        return txConnOutAsHex;
    }

    public String getTakerContractAsJson() {
        return contractAsJson;
    }

    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public String getTakerPayoutAddress() {
        return takerPayoutAddress;
    }

    public long getTakerTxOutIndex() {
        return takerTxOutIndex;
    }

}
