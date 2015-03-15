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

package io.bitsquare.trade.protocol.trade.offerer.messages;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.trade.protocol.trade.TradeMessage;

import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.util.List;

public class RequestDepositPaymentMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = -3988720410493712913L;

    private final String tradeId;
    private final List<TransactionOutput> offererConnectedOutputsForAllInputs;
    private final List<TransactionOutput> offererOutputs;
    private final byte[] offererPubKey;
    private final BankAccount bankAccount;
    private final String accountID;

    public RequestDepositPaymentMessage(String tradeId,
                                        List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                        List<TransactionOutput> offererOutputs,
                                        byte[] offererPubKey,
                                        BankAccount bankAccount,
                                        String accountID) {
        this.tradeId = tradeId;
        this.offererConnectedOutputsForAllInputs = offererConnectedOutputsForAllInputs;
        this.offererOutputs = offererOutputs;
        this.offererPubKey = offererPubKey;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public List<TransactionOutput> getOffererConnectedOutputsForAllInputs() {
        return offererConnectedOutputsForAllInputs;
    }

    public List<TransactionOutput> getOffererOutputs() {
        return offererOutputs;
    }

    public byte[] getOffererPubKey() {
        return offererPubKey;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public String getAccountId() {
        return accountID;
    }
}
