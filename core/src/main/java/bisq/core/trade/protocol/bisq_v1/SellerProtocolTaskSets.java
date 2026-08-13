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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerFinalizesDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesTradeStatistics;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendsDepositTxAndDelayedPayoutTxMessage;

import bisq.common.taskrunner.Task;

final class SellerProtocolTaskSets {
    private SellerProtocolTaskSets() {
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Task<TradeModel>>[] afterDelayedPayoutSignature() {
        return new Class[]{SellerProcessDelayedPayoutTxSignatureResponse.class,
                SellerFinalizesDelayedPayoutTx.class,
                SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                SellerPublishesDepositTx.class,
                SellerPublishesTradeStatistics.class};
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Task<TradeModel>>[] afterInitialization(boolean messageDelivered) {
        if (messageDelivered) {
            return new Class[]{SellerPublishesDepositTx.class,
                    SellerPublishesTradeStatistics.class};
        }
        return new Class[]{SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                SellerPublishesDepositTx.class,
                SellerPublishesTradeStatistics.class};
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Task<TradeModel>>[] afterBuyerPaymentAccountReveal(boolean publishDeposit) {
        return publishDeposit
                ? new Class[]{SellerProcessShareBuyerPaymentAccountMessage.class,
                ApplyFilter.class,
                VerifyPeersAccountAgeWitness.class,
                SellerPublishesDepositTx.class,
                SellerPublishesTradeStatistics.class}
                : new Class[]{SellerProcessShareBuyerPaymentAccountMessage.class,
                ApplyFilter.class,
                VerifyPeersAccountAgeWitness.class};
    }
}
