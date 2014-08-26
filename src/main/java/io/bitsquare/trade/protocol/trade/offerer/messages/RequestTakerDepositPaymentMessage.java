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

import java.io.Serializable;

public class RequestTakerDepositPaymentMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = -3988720410493712913L;

    private final String tradeId;
    private BankAccount bankAccount;
    private String accountID;
    private String offererPubKey;
    private String preparedOffererDepositTxAsHex;
    private long offererTxOutIndex;

    public RequestTakerDepositPaymentMessage(String tradeId, BankAccount bankAccount, String accountID,
                                             String offererPubKey, String preparedOffererDepositTxAsHex,
                                             long offererTxOutIndex) {
        this.tradeId = tradeId;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.offererPubKey = offererPubKey;
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.offererTxOutIndex = offererTxOutIndex;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public String getAccountId() {
        return accountID;
    }

    public String getOffererPubKey() {
        return offererPubKey;
    }

    public String getPreparedOffererDepositTxAsHex() {
        return preparedOffererDepositTxAsHex;
    }

    public long getOffererTxOutIndex() {
        return offererTxOutIndex;
    }
}
