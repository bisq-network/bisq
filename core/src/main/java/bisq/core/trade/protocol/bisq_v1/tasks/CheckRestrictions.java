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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.coin.CoinUtil;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class CheckRestrictions extends TradeTask {
    public CheckRestrictions(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Coin amount = trade.getAmount();

            Coin buyerSecurityDeposit = trade.getOffer().getBuyerSecurityDeposit();
            Coin minBuyerSecurityDeposit = Restrictions.getMinBuyerSecurityDepositAsCoin();
            checkArgument(buyerSecurityDeposit.getValue() >= minBuyerSecurityDeposit.getValue(),
                    "Buyer security deposit is less then the min. buyer security deposit (as coin)");

            double minBuyerSecurityDepositAsPercent = Restrictions.getMinBuyerSecurityDepositAsPercent();
            Coin minBuyerSecurityDepositFromPercentage = CoinUtil.getPercentOfAmountAsCoin(minBuyerSecurityDepositAsPercent, amount);
            checkArgument(buyerSecurityDeposit.getValue() >= minBuyerSecurityDepositFromPercentage.getValue(),
                    "Buyer security deposit is less then the min. buyer security deposit (as percentage)");

            Coin sellerSecurityDeposit = trade.getOffer().getSellerSecurityDeposit();
            Coin minSellerSecurityDeposit = Restrictions.getMinSellerSecurityDepositAsCoin();
            checkArgument(sellerSecurityDeposit.getValue() >= minSellerSecurityDeposit.getValue(),
                    "Seller security deposit is less then the min. seller security deposit (as coin)");

            double minSellerSecurityDepositAsPercent = Restrictions.getMinSellerSecurityDepositAsPercent();
            Coin minSellerSecurityDepositFromPercentage = CoinUtil.getPercentOfAmountAsCoin(minSellerSecurityDepositAsPercent, amount);
            checkArgument(sellerSecurityDeposit.getValue() >= minSellerSecurityDepositFromPercentage.getValue(),
                    "Seller security deposit is less then the min. seller security deposit (as percentage)");

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

