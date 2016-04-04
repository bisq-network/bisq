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

package io.bitsquare.trade.protocol.trade.messages;

import io.bitsquare.app.Version;
import io.bitsquare.btc.data.RawTransactionInput;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.payment.PaymentAccountContractData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;

@Immutable

// TODO check if it should not implement MailboxMessage as well?
public final class PublishDepositTxRequest extends TradeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(PublishDepositTxRequest.class);

    public final PaymentAccountContractData offererPaymentAccountContractData;
    public final String offererAccountId;
    public final String offererContractAsJson;
    public final String offererContractSignature;
    public final String offererPayoutAddressString;
    public final byte[] preparedDepositTx;
    public final ArrayList<RawTransactionInput> offererInputs;
    public final byte[] offererTradeWalletPubKey;

    public PublishDepositTxRequest(String tradeId,
                                   PaymentAccountContractData offererPaymentAccountContractData,
                                   String offererAccountId,
                                   byte[] offererTradeWalletPubKey,
                                   String offererContractAsJson,
                                   String offererContractSignature,
                                   String offererPayoutAddressString,
                                   byte[] preparedDepositTx,
                                   ArrayList<RawTransactionInput> offererInputs) {
        super(tradeId);
        this.offererPaymentAccountContractData = offererPaymentAccountContractData;
        this.offererAccountId = offererAccountId;
        this.offererTradeWalletPubKey = offererTradeWalletPubKey;
        this.offererContractAsJson = offererContractAsJson;
        this.offererContractSignature = offererContractSignature;
        this.offererPayoutAddressString = offererPayoutAddressString;
        this.preparedDepositTx = preparedDepositTx;
        this.offererInputs = offererInputs;

        log.trace("offererPaymentAccount size " + Utilities.serialize(offererPaymentAccountContractData).length);
        log.trace("offererTradeWalletPubKey size " + offererTradeWalletPubKey.length);
        log.trace("preparedDepositTx size " + preparedDepositTx.length);
        log.trace("offererInputs size " + Utilities.serialize(new ArrayList<>(offererInputs)).length);
    }
}
