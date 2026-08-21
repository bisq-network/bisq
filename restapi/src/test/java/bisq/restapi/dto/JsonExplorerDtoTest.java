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

package bisq.restapi.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class JsonExplorerDtoTest {
    @Test
    void jsonTxUsesValueEquality() {
        JsonTx first = jsonTx();
        JsonTx second = jsonTx();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void jsonTxOutputUsesValueEquality() {
        JsonTxOutput first = jsonTxOutput();
        JsonTxOutput second = jsonTxOutput();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private static JsonTx jsonTx() {
        return new JsonTx("tx-id",
                123,
                "block-hash",
                456,
                List.of(),
                List.of(jsonTxOutput()),
                JsonTxType.TRANSFER_BSQ,
                JsonTxType.TRANSFER_BSQ.getDisplayString(),
                789,
                10,
                -1);
    }

    private static JsonTxOutput jsonTxOutput() {
        return new JsonTxOutput("tx-id",
                0,
                100,
                200,
                123,
                true,
                300,
                400,
                "address",
                null,
                null,
                456,
                JsonTxType.TRANSFER_BSQ,
                JsonTxType.TRANSFER_BSQ.getDisplayString(),
                JsonTxOutputType.BSQ_OUTPUT,
                JsonTxOutputType.BSQ_OUTPUT.getDisplayString(),
                null,
                0,
                true);
    }
}
