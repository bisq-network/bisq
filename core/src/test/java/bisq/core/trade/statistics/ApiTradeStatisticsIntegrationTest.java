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

import bisq.core.monetary.Price;
import bisq.core.payment.payload.PaymentMethod;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.network.p2p.storage.P2PDataStorage.ByteArray;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persisted store file sizes:
 * <p>
 * 10,000       ApiTradeStatistics:     245K
 * 100,000      ApiTradeStatistics:     2.4M        ~ 5 minutes
 * ~ 300,00  ~ 7.3M
 * 395000    ~ 9.5M
 * 449999    ~ 11M
 * 529999    ~ 13M
 * 599999    ~ 15M
 * 669999    ~ 16M
 * 724999    ~ 18M
 * 739999    ~ 18M
 * 879999    ~ 22M
 * 1,000,000    ApiTradeStatistics:     ~ 25M
 * Persisting 1,000,000 took 75324709 ms (20.92 hours, with artificial waits for file writes)
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiTradeStatisticsIntegrationTest {

    private static final int NUM_TEST_STATS = 100;
    private static List<TradeStatistics3> tradeStatistics3List;
    private static List<ApiTradeStatistics> apiTradeStatisticsList;
    private static StatisticsTestBootstrapper app;  // For brevity, name it 'app'.

    @BeforeAll
    public static void bootstrap() {
        tradeStatistics3List = genTradeStatistics3(NUM_TEST_STATS);
        apiTradeStatisticsList = genApiTradeStatistics(tradeStatistics3List);

        app = new StatisticsTestBootstrapper();
        app.initializeServices();
    }

    @Order(1)
    @Test
    public void testPersistStatisticsStores() {
        assertEquals(tradeStatistics3List.size(),
                apiTradeStatisticsList.size(),
                "Generated data set sizes do not match.");

        log.info("Persisting {} {} & {} to {} ...",
                tradeStatistics3List.size(),
                TradeStatistics3.class.getSimpleName(),
                ApiTradeStatistics.class.getSimpleName(),
                app.getDbStorageDir().getAbsolutePath());

        long ts = System.currentTimeMillis();
        for (int listIdx = 0; listIdx < tradeStatistics3List.size(); listIdx++) {
            TradeStatistics3 tradeStatistics3Object = tradeStatistics3List.get(listIdx);
            ApiTradeStatistics apiTradeStatisticsObject = apiTradeStatisticsList.get(listIdx);
            assertEquals(tradeStatistics3Object.getHash(), apiTradeStatisticsObject.getHash());

            // Add payloads to the TradeStatistics3 & ApiTradeStatistics stores.
            addOneTradeStatistics3Payload(tradeStatistics3Object);
            addOneApiTradeStatisticsPayload(apiTradeStatisticsObject);

            // Persist a batch of 5,000 payloads.
            if ((listIdx != 0) && (listIdx % 5_000 == 0)) {
                persistTradeStatistics3Payloads();
                persistApiTradeStatisticsPayloads();
                app.sleep(2_000); // Let disk writes finish.
                log.info("Persisted {} {} & {} payloads ...",
                        listIdx - 1,
                        TradeStatistics3.class.getSimpleName(),
                        ApiTradeStatistics.class.getSimpleName());
            }
        }

        // Persist remaining batches of payloads.
        persistTradeStatistics3Payloads();
        persistApiTradeStatisticsPayloads();
        app.sleep(2_000); // Let disk writes finish.

        log.info("Done persisting {} {} & {} payloads to {} in ~ {} ms.",
                apiTradeStatisticsList.size(),
                TradeStatistics3.class.getSimpleName(),
                ApiTradeStatistics.class.getSimpleName(),
                app.getDbStorageDir().getAbsolutePath(),
                System.currentTimeMillis() - ts);
    }

    @Order(2)
    @Test
    public void testReadTradeStatistics3StoreFile() {
        var persistenceManager = app.createPersistenceManager(app.getDbStorageDir());
        var persistedEnvelope = persistenceManager.getPersisted(app.getTradeStatistics3StorageService().getFileName());

        var persistableNetworkPayloadByByteArrayMap = ((TradeStatistics3Store) persistedEnvelope).getMap();
        assertEquals(NUM_TEST_STATS, persistableNetworkPayloadByByteArrayMap.size(), "Incorrect persisted payload count.");

        persistableNetworkPayloadByByteArrayMap.forEach((k, v) -> {
            assertTrue(tradeStatistics3List.contains(v), "Did not find persisted object in generated list.");
        });

        for (TradeStatistics3 expectedTradeStatistics3 : tradeStatistics3List) {
            ByteArray expectedByteArray = new ByteArray(expectedTradeStatistics3.getHash());
            var actualTradeStatistics3 = persistableNetworkPayloadByByteArrayMap.get(expectedByteArray);
            assertNotNull(actualTradeStatistics3, "Did not find generated list entry in persisted payloads.");
            assertEquals(expectedTradeStatistics3, actualTradeStatistics3, "Generated stats != persisted stats.");
            ByteArray actualByteArray = new ByteArray(actualTradeStatistics3.getHash());
            assertEquals(expectedByteArray, actualByteArray, "ByteArrays do not match.");
        }
    }

    @Order(3)
    @Test
    public void testReadApiTradeStatisticsStoreFile() {
        var persistenceManager = app.createPersistenceManager(app.getDbStorageDir());
        var persistedEnvelope = persistenceManager.getPersisted(app.getApiTradeStatisticsStorageService().getFileName());

        var persistableNetworkPayloadByByteArrayMap = ((ApiTradeStatisticsStore) persistedEnvelope).getMap();
        assertEquals(NUM_TEST_STATS, persistableNetworkPayloadByByteArrayMap.size(), "Incorrect persisted payload count.");

        persistableNetworkPayloadByByteArrayMap.forEach((k, v) -> {
            assertTrue(apiTradeStatisticsList.contains(v), "Did not find persisted object in generated list.");
        });

        for (ApiTradeStatistics expectedApiTradeStatistics : apiTradeStatisticsList) {
            ByteArray expectedByteArray = new ByteArray(expectedApiTradeStatistics.getTradeStatistics3Hash());
            var actualApiTradeStatistics = persistableNetworkPayloadByByteArrayMap.get(expectedByteArray);
            assertNotNull(actualApiTradeStatistics, "Did not find generated list entry in persisted payloads.");
            assertEquals(expectedApiTradeStatistics, actualApiTradeStatistics, "Generated stats != persisted stats.");
            ByteArray actualByteArray = new ByteArray(actualApiTradeStatistics.getHash());
            assertEquals(expectedByteArray, actualByteArray, "ByteArrays do not match.");
        }
    }

    @Order(4)
    @Test
    public void testMerge() {
        BiFunction<byte[], List<TradeStatistics3>, TradeStatistics3> findTradeStatistics3 = (hash, list) ->
                list.stream()
                        .filter(s -> Arrays.equals(s.getHash(), hash))
                        .findFirst().get();
        // Set the transient ApiTradeStatistics.tradeStatistics3 fields in apiTradeStatisticsList.
        apiTradeStatisticsList.stream()
                .forEach(apiStats -> {
                    TradeStatistics3 joinedStats = findTradeStatistics3.apply(apiStats.getTradeStatistics3Hash(), tradeStatistics3List);
                    apiStats.setTradeStatistics3(joinedStats);
                });
        for (ApiTradeStatistics merged : apiTradeStatisticsList) {
            assertNotNull(merged.getTradeStatistics3());
            assertTrue(Arrays.equals(merged.getTradeStatistics3Hash(), merged.getTradeStatistics3().getHash()));
        }
    }

    @Order(5)
    @Test
    public void testStatisticsCalculations() {
        long countMakerApiUsers = apiTradeStatisticsList.stream()
                .filter(s -> s.isMakerApiUser())
                .count();
        long countTakerApiUsers = apiTradeStatisticsList.stream()
                .filter(s -> s.isTakerApiUser())
                .count();
        long countMakerAndTakerApiUsers = apiTradeStatisticsList.stream()
                .filter(s -> s.isMakerApiUser() && s.isTakerApiUser())
                .count();
        log.info("countMakerApiUsers = {}, countTakerApiUsers = {} countMakerAndTakerApiUsers = {}",
                countMakerApiUsers,
                countTakerApiUsers,
                countMakerAndTakerApiUsers);

        // API Trade Amount Stats

        var totalTradeAmountByApiMakers = apiTradeStatisticsList.stream()
                .filter(apiStats -> apiStats.isMakerApiUser())
                .mapToLong(apiStats -> apiStats.getTradeStatistics3().getAmount())
                .sum();

        var totalTradeAmountByApiTakers = apiTradeStatisticsList.stream()
                .filter(apiStats -> apiStats.isTakerApiUser())
                .mapToLong(apiStats -> apiStats.getTradeStatistics3().getAmount())
                .sum();

        var totalTradeAmountByApiMakerAndTakers = apiTradeStatisticsList.stream()
                .filter(apiStats -> apiStats.isTakerApiUser() && apiStats.isMakerApiUser())
                .mapToLong(apiStats -> apiStats.getTradeStatistics3().getAmount())
                .sum();

        var totalTradeAmount = apiTradeStatisticsList.stream()
                .mapToLong(apiStats -> apiStats.getTradeStatistics3().getAmount())
                .sum();

        log.info("totalTradeAmountByApiMakers = {}, totalTradeAmountByApiTakers = {}, totalTradeAmountByApiMakerAndTakers = {}, totalTradeAmount = {}",
                Coin.valueOf(totalTradeAmountByApiMakers).toFriendlyString(),
                Coin.valueOf(totalTradeAmountByApiTakers).toFriendlyString(),
                Coin.valueOf(totalTradeAmountByApiMakerAndTakers).toFriendlyString(),
                Coin.valueOf(totalTradeAmount).toFriendlyString());

        // TODO ? Look at GroupBy operations: https://dzone.com/articles/java-streams-groupingby-examples
    }


    @AfterAll
    public static void shutdown() {
        app.shutdown();
    }

    private void addOneTradeStatistics3Payload(TradeStatistics3 tradeStatistics3) {
        app.getP2PDataStorage().addPersistableNetworkPayload(tradeStatistics3,
                app.getNetworkNode().getNodeAddress(),
                false,
                false);
    }

    private void addOneApiTradeStatisticsPayload(ApiTradeStatistics apiTradeStatistics) {
        app.getP2PDataStorage().addPersistableNetworkPayload(apiTradeStatistics,
                app.getNetworkNode().getNodeAddress(),
                false,
                false);
    }

    private void persistTradeStatistics3Payloads() {
        app.getTradeStatistics3StorageService().persistNow();
    }

    private void persistApiTradeStatisticsPayloads() {
        app.getApiTradeStatisticsStorageService().persistNow();
    }

    private static List<TradeStatistics3> genTradeStatistics3(int numStats) {
        List<TradeStatistics3> list = new ArrayList<>();
        Random random = new Random();
        Date now = new Date();
        for (int i = 0; i < numStats; i++) {
            var tradeDate = now.getTime() + 5;
            var priceString = String.valueOf(20_000 + random.nextInt(20_000));
            var tradePrice = Price.parse("EUR", priceString).getValue();
            var tradeAmountString = String.valueOf(random.nextDouble()).substring(0, 10);
            var tradeAmount = Coin.parseCoin(tradeAmountString).getValue();
            list.add(new TradeStatistics3("EUR",
                    tradePrice,
                    tradeAmount,
                    PaymentMethod.SEPA_ID,
                    tradeDate,
                    null,
                    null,
                    null,
                    null));
        }
        assertEquals(numStats, list.size(), "Incorrect list size");
        // Check the hashes are distinct.
        // TODO This would not be necessary if we generated a Set instead of a List.
        int countDistinctHashes = (int) list.stream()
                .map(s -> Utilities.encodeToHex(s.getHash()))
                .distinct()
                .count();
        assertEquals(list.size(), countDistinctHashes, "Generated duplicate hashes");
        return list;
    }

    static List<ApiTradeStatistics> genApiTradeStatistics(List<TradeStatistics3> tradeStatistics3List) {
        List<ApiTradeStatistics> stats = new ArrayList<>();
        Random random = new Random();
        for (TradeStatistics3 tradeStatistics3 : tradeStatistics3List) {
            var isMakerApiUser = random.nextBoolean();
            // Must be true when isMakerApiUser == false.
            var isTakerApiUser = isMakerApiUser ? random.nextBoolean() : true;
            stats.add(new ApiTradeStatistics(tradeStatistics3.getHash(), isMakerApiUser, isTakerApiUser));
        }
        return stats;
    }
}
