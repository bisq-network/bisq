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

package io.bitsquare.trade;

public interface TradeState {
    Phase getPhase();

    void setPhase(Phase phase);

    enum SellerState implements TradeState {
        PREPARATION(Phase.PREPARATION),

        DEPOSIT_PUBLISHED_MSG_RECEIVED(Phase.DEPOSIT_PAID),
        DEPOSIT_CONFIRMED(Phase.DEPOSIT_PAID),

        FIAT_PAYMENT_STARTED_MSG_RECEIVED(Phase.FIAT_SENT),

        FIAT_PAYMENT_RECEIPT(Phase.FIAT_RECEIVED),
        FIAT_PAYMENT_RECEIPT_MSG_SENT(Phase.FIAT_RECEIVED),

        PAYOUT_TX_RECEIVED(Phase.PAYOUT_PAID),
        PAYOUT_TX_COMMITTED(Phase.PAYOUT_PAID),

        PAYOUT_BROAD_CASTED(Phase.PAYOUT_PAID),

        WITHDRAW_COMPLETED(Phase.WITHDRAWN),

        FAILED();

        public Phase getPhase() {
            return phase;
        }

        public void setPhase(Phase phase) {
            this.phase = phase;
        }

        private Phase phase;

        SellerState() {
        }

        SellerState(Phase phase) {
            this.phase = phase;
        }

    }

    enum BuyerState implements TradeState {
        PREPARATION(Phase.PREPARATION),

        DEPOSIT_PUBLISHED(Phase.DEPOSIT_PAID),
        DEPOSIT_PUBLISHED_MSG_SENT(Phase.DEPOSIT_PAID),
        DEPOSIT_CONFIRMED(Phase.DEPOSIT_PAID),

        FIAT_PAYMENT_STARTED(Phase.FIAT_SENT),
        FIAT_PAYMENT_STARTED_MSG_SENT(Phase.FIAT_SENT),

        FIAT_PAYMENT_RECEIPT_MSG_RECEIVED(Phase.FIAT_RECEIVED),

        PAYOUT_TX_COMMITTED(Phase.PAYOUT_PAID),
        PAYOUT_TX_SENT(Phase.PAYOUT_PAID),

        PAYOUT_BROAD_CASTED(Phase.PAYOUT_PAID),

        WITHDRAW_COMPLETED(Phase.WITHDRAWN),

        FAILED();

        public Phase getPhase() {
            return phase;
        }

        public void setPhase(Phase phase) {
            this.phase = phase;
        }

        private Phase phase;

        BuyerState() {
        }

        BuyerState(Phase phase) {
            this.phase = phase;
        }
    }

    enum Phase {
        PREPARATION,
        TAKER_FEE_PAID,
        DEPOSIT_PAID,
        FIAT_SENT,
        FIAT_RECEIVED,
        PAYOUT_PAID,
        WITHDRAWN
    }
}
