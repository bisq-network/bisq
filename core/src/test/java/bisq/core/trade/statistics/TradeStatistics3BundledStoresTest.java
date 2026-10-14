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

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;

import bisq.common.app.Version;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled stores hold the statistics of past trades. The checks against a future date and a volume which is not
 * positive must not reject any of them.
 */
class TradeStatistics3BundledStoresTest {
    @Test
    void bundledStatisticsHaveAPastDateAndAPositiveVolume() throws IOException {
        long now = System.currentTimeMillis();
        int numStatistics = 0;
        List<String> rejected = new ArrayList<>();
        for (String version : Version.HISTORICAL_RESOURCE_FILE_VERSION_TAGS) {
            for (protobuf.TradeStatistics3 proto : readStore("/TradeStatistics3Store_" + version + "_BTC_MAINNET")) {
                TradeStatistics3 tradeStatistics;
                try {
                    tradeStatistics = TradeStatistics3.fromProto(proto);
                } catch (InvalidPersistableNetworkPayloadException e) {
                    // TradeStatistics3Store skips these as well
                    continue;
                }
                numStatistics++;
                if (tradeStatistics.getDateAsLong() > now || !hasPositiveVolume(tradeStatistics)) {
                    rejected.add(version + ": " + tradeStatistics);
                }
            }
        }

        assertTrue(numStatistics > 400_000, "numStatistics=" + numStatistics);
        assertEquals(List.of(), rejected);
    }

    private static boolean hasPositiveVolume(TradeStatistics3 tradeStatistics) {
        try {
            return tradeStatistics.getTradeVolume().getValue() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<protobuf.TradeStatistics3> readStore(String resource) throws IOException {
        try (InputStream inputStream = TradeStatistics3BundledStoresTest.class.getResourceAsStream(resource)) {
            assertNotNull(inputStream, "resource not found: " + resource);
            return protobuf.PersistableEnvelope.parseDelimitedFrom(inputStream)
                    .getTradeStatistics3Store()
                    .getItemsList();
        }
    }
}
