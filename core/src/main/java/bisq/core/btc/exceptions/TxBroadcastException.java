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

package bisq.core.btc.exceptions;

import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Used in case the broadcasting of a tx did not succeed in the expected time.
 * The broadcast can still succeed at a later moment though.
 */
public class TxBroadcastException extends Exception {
    @Getter
    @Nullable
    private String txId;

    public TxBroadcastException(String message) {
        super(message);
    }

    public TxBroadcastException(String message, Throwable cause) {
        super(message, cause);
    }

    public TxBroadcastException(String message, String txId) {
        super(message);
        this.txId = txId;
    }

    @Override
    public String toString() {
        return "TxBroadcastException{" +
                "\n     txId='" + txId + '\'' +
                "\n} " + super.toString();
    }
}
