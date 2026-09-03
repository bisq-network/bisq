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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MempoolTxStatusTest {
    private static final String TX_ID = "01".repeat(32);

    @Test
    void parsesConfirmedTransactionWithMatchingIdAndBlockHeight() {
        MempoolTxStatus status = MempoolTxStatus.fromJson(TX_ID, """
                {
                  "txid": "%s",
                  "status": {"confirmed": true, "block_height": 800123}
                }
                """.formatted(TX_ID));

        assertEquals(TX_ID, status.txId());
        assertTrue(status.confirmed());
        assertEquals(800_123, status.blockHeight());
    }

    @Test
    void parsesKnownButUnconfirmedTransactionWithZeroBlockHeight() {
        MempoolTxStatus status = MempoolTxStatus.fromJson(TX_ID, """
                {
                  "txid": "%s",
                  "status": {"confirmed": false}
                }
                """.formatted(TX_ID));

        assertFalse(status.confirmed());
        assertEquals(0, status.blockHeight());
    }

    @Test
    void rejectsStatusForDifferentTransaction() {
        String otherTxId = "02".repeat(32);

        assertThrows(IllegalArgumentException.class,
                () -> MempoolTxStatus.fromJson(TX_ID, """
                        {
                          "txid": "%s",
                          "status": {"confirmed": true, "block_height": 800123}
                        }
                        """.formatted(otherTxId)));
    }

    @Test
    void rejectsConfirmedStatusWithoutBlockHeight() {
        assertThrows(NullPointerException.class,
                () -> MempoolTxStatus.fromJson(TX_ID, """
                        {
                          "txid": "%s",
                          "status": {"confirmed": true}
                        }
                        """.formatted(TX_ID)));
    }

    @Test
    void rejectsStatusWithoutConfirmedField() {
        assertThrows(NullPointerException.class,
                () -> MempoolTxStatus.fromJson(TX_ID, """
                        {
                          "txid": "%s",
                          "status": {}
                        }
                        """.formatted(TX_ID)));
    }
}
