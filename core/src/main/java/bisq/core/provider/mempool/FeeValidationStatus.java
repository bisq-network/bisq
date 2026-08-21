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

package bisq.core.provider.mempool;

import bisq.core.locale.Res;

public enum FeeValidationStatus {
    NOT_CHECKED_YET("fee.validation.notCheckedYet"),
    ACK_FEE_OK("fee.validation.ack.feeCheckedOk"),
    ACK_BSQ_TX_IS_NEW("fee.validation.ack.bsqTxIsNew"),
    ACK_CHECK_BYPASSED("fee.validation.ack.checkBypassed"),
    NACK_BTC_TX_NOT_FOUND("fee.validation.error.btcTxNotFound"),
    NACK_BSQ_FEE_NOT_FOUND("fee.validation.error.bsqTxNotFound"),
    NACK_MAKER_FEE_TOO_LOW("fee.validation.error.makerFeeTooLow"),
    NACK_TAKER_FEE_TOO_LOW("fee.validation.error.takerFeeTooLow"),
    NACK_UNKNOWN_FEE_RECEIVER("fee.validation.error.unknownReceiver"),
    NACK_JSON_ERROR("fee.validation.error.json");

    private final String descriptionKey;

    FeeValidationStatus(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    public boolean pass() {
        return this == ACK_FEE_OK || this == ACK_BSQ_TX_IS_NEW || this == ACK_CHECK_BYPASSED;
    }
    public boolean fail() {
        return this != NOT_CHECKED_YET && !pass();
    }

    public String toString() {
        try {
            return Res.get(descriptionKey);
        } catch (Exception ex) {
            return descriptionKey;
        }
    }
}
