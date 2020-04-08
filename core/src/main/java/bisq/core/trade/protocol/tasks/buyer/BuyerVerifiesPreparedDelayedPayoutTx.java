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
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.offer.Offer;
import bisq.core.trade.DonationAddressValidation;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerVerifiesPreparedDelayedPayoutTx extends TradeTask {
    @SuppressWarnings({"unused"})
    public BuyerVerifiesPreparedDelayedPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction preparedDelayedPayoutTx = processModel.getPreparedDelayedPayoutTx();

            // Check donation address
            DonationAddressValidation.validate(preparedDelayedPayoutTx,
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());

            // Check amount
            Offer offer = checkNotNull(trade.getOffer());
            Coin msOutputAmount = offer.getBuyerSecurityDeposit()
                    .add(offer.getSellerSecurityDeposit())
                    .add(checkNotNull(trade.getTradeAmount()));
            checkArgument(preparedDelayedPayoutTx.getOutput(0).getValue().equals(msOutputAmount),
                    "output value of deposit tx and delayed payout tx must match");

            // Check lock time
            checkArgument(preparedDelayedPayoutTx.getLockTime() == trade.getLockTime(),
                    "preparedDelayedPayoutTx lock time must match trade.getLockTime()");

            // Check seq num
            checkArgument(preparedDelayedPayoutTx.getInputs().stream().anyMatch(e -> e.getSequenceNumber() == TransactionInput.NO_SEQUENCE - 1),
                    "Sequence number must be 0xFFFFFFFE");

            complete();
        } catch (DonationAddressValidation.DonationAddressException e) {
            failed("Sellers donation address is invalid." +
                    "\nAddress used by BTC seller: " + e.getAddressAsString() +
                    "\nRecent donation address:" + e.getRecentDonationAddressString() +
                    "\nDefault donation address: " + e.getDefaultDonationAddressString());
        } catch (DonationAddressValidation.MissingDelayedPayoutTxException e) {
            failed(e.getMessage());
        } catch (Throwable t) {
            failed(t);
        }
    }
}
