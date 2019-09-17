/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have confirmed a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.xmr.wallet;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;

import bisq.core.locale.Res;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import monero.wallet.model.MoneroTxWallet;

@Slf4j
@EqualsAndHashCode
@Data
public class XmrTxListItem {
	
	@Getter
	private final String txId;
	@Getter
	private final Date date;
	@Getter
	private final String paymentId;
	@Getter
	private final String direction;
	@Getter
	private BigInteger amount;
	@Getter
	private boolean confirmed;
	@Getter
	private long confirmations = 0;
	@Getter
	private String key;
	@Getter
	private Integer mixin;
	@Getter
	private Long unlockTime;
	@Getter
	private String destinationAddress;

	public XmrTxListItem(MoneroTxWallet txWallet) {
		txId = txWallet.getId();
		paymentId = txWallet.getPaymentId();
		Long timestamp = txWallet.getBlock().getTimestamp();
		date = timestamp != null && timestamp != 0 ? Date.from(Instant.ofEpochSecond(timestamp)) : null;
		confirmed = txWallet.isConfirmed();
		confirmations = txWallet.getNumConfirmations();
		key = txWallet.getKey();
		mixin = txWallet.getMixin();
		unlockTime = txWallet.getUnlockTime();
		BigInteger valueSentToMe = txWallet.getIncomingAmount() != null ? txWallet.getIncomingAmount() : BigInteger.ZERO;
		BigInteger valueSentFromMe = txWallet.getOutgoingAmount() != null ? txWallet.getOutgoingAmount() : BigInteger.ZERO;
		if(txWallet.isOutgoing()) {
			destinationAddress = txWallet.getOutgoingTransfer() != null && 
					txWallet.getOutgoingTransfer().getDestinations() != null ? 
					txWallet.getOutgoingTransfer().getDestinations().get(0).getAddress() : null;
		} else {
			destinationAddress = txWallet.getIncomingTransfers() != null ? 
					txWallet.getIncomingTransfers().get(0).getAddress() : null;
		}
		amount = txWallet.isIncoming() ? valueSentToMe : valueSentFromMe;
		direction = txWallet.isIncoming() ? Res.get("shared.account.wallet.tx.item.in") : Res.get("shared.account.wallet.tx.item.out");
	}
}
