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
import bisq.core.xmr.jsonrpc.result.MoneroTransfer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
@Data
public class XmrTxListItem {
	
	@Getter
	private final String id;
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
	private Long unlockTime;
	@Getter
	private String destinationAddress;

	public XmrTxListItem(MoneroTransfer transfer) {
		id = transfer.getId();
		paymentId = transfer.getPaymentId();
		Long timestamp = transfer.getTimestamp();
		date = timestamp != null && timestamp != 0 ? Date.from(Instant.ofEpochSecond(timestamp)) : null;
		confirmed = transfer.getConfirmations() >= transfer.getSuggestedConfirmationsThreshold();
		confirmations = transfer.getConfirmations();
		unlockTime = transfer.getUnlockTime();
		amount = transfer.getAmount();
		direction = "in".equals(transfer.getType()) ? Res.get("shared.account.wallet.tx.item.in") : Res.get("shared.account.wallet.tx.item.out");
		destinationAddress = transfer.getAddress();
	}
}
