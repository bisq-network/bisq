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

import bisq.core.payment.payload.PaymentMethod;

import bisq.common.util.ExtraDataMapValidator;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TradeStatistics3Test {
    @Test
    public void isValidAcceptsHistoricalTradeAboveCurrentTradeLimit() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                50_000_000,
                Coin.parseCoin("0.25").value,
                "SEPA",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertTrue(tradeStatistics.isValid());
    }

    @Test
    public void isValidRejectsTradeAboveHistoricalSanityLimit() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                50_000_000,
                TradeStatistics3.HISTORICAL_MAX_TRADE_AMOUNT + 1,
                "SEPA",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertFalse(tradeStatistics.isValid());
    }

    @Test
    public void singleEntryExtraDataMapSerializesLikeHashMap() {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put(REFERRAL_ID, "referralId");
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                50_000_000,
                Coin.parseCoin("0.25").value,
                "SEPA",
                1,
                "mediator",
                "refundAgent",
                extraDataMap,
                new byte[20]);

        protobuf.TradeStatistics3 treeMapProto = tradeStatistics.toProtoTradeStatistics3();
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(REFERRAL_ID, "referralId");
        protobuf.TradeStatistics3 hashMapProto = treeMapProto.toBuilder()
                .clearExtraData()
                .putAllExtraData(hashMap)
                .build();

        assertArrayEquals(hashMapProto.toByteArray(), treeMapProto.toByteArray());
    }

    @Test
    public void fromProtoConvertsExtraDataMapToTreeMapWithoutChangingSingleEntryBytes() {
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(REFERRAL_ID, "referralId");
        protobuf.TradeStatistics3 hashMapProto = protobuf.TradeStatistics3.newBuilder()
                .setCurrency("USD")
                .setPrice(50_000_000)
                .setAmount(Coin.parseCoin("0.25").value)
                .setPaymentMethod(String.valueOf(TradeStatistics3.PaymentMethodMapper.SEPA.ordinal()))
                .setDate(1)
                .setMediator("mediator")
                .setRefundAgent("refundAgent")
                .setHash(ByteString.copyFrom(new byte[20]))
                .putAllExtraData(hashMap)
                .build();

        TradeStatistics3 tradeStatistics = TradeStatistics3.fromProto(hashMapProto);

        assertTrue(tradeStatistics.getExtraDataMap() instanceof TreeMap);
        assertArrayEquals(hashMapProto.toByteArray(), tradeStatistics.toProtoTradeStatistics3().toByteArray());
    }

    @Test
    public void fromProtoPreservesNullExtraDataMap() {
        protobuf.TradeStatistics3 proto = protobuf.TradeStatistics3.newBuilder()
                .setCurrency("USD")
                .setPrice(50_000_000)
                .setAmount(Coin.parseCoin("0.25").value)
                .setPaymentMethod(String.valueOf(TradeStatistics3.PaymentMethodMapper.SEPA.ordinal()))
                .setDate(1)
                .setHash(ByteString.copyFrom(new byte[20]))
                .build();

        TradeStatistics3 tradeStatistics = TradeStatistics3.fromProto(proto);

        assertNull(tradeStatistics.getExtraDataMap());
        assertTrue(tradeStatistics.toProtoTradeStatistics3().getExtraDataMap().isEmpty());
    }

    @Test
    public void fromProtoSanitizesInvalidExtraDataMap() {
        Map<String, String> oversizedMap = new HashMap<>();
        for (int i = 0; i <= ExtraDataMapValidator.MAX_SIZE; i++) {
            oversizedMap.put("key" + i, "value");
        }
        protobuf.TradeStatistics3 proto = protobuf.TradeStatistics3.newBuilder()
                .setCurrency("USD")
                .setPrice(50_000_000)
                .setAmount(Coin.parseCoin("0.25").value)
                .setPaymentMethod(String.valueOf(TradeStatistics3.PaymentMethodMapper.SEPA.ordinal()))
                .setDate(1)
                .setHash(ByteString.copyFrom(new byte[20]))
                .putAllExtraData(oversizedMap)
                .build();

        TradeStatistics3 tradeStatistics = TradeStatistics3.fromProto(proto);

        Map<String, String> extraDataMap = tradeStatistics.getExtraDataMap();
        assertNotNull(extraDataMap);
        assertTrue(extraDataMap instanceof TreeMap);
        assertTrue(extraDataMap.isEmpty());
        assertTrue(tradeStatistics.toProtoTradeStatistics3().getExtraDataMap().isEmpty());
    }

    @Disabled("Not fixed yet")
    @Test
    public void allPaymentMethodsCoveredByWrapper() {
        Set<String> paymentMethodCodes = PaymentMethod.getPaymentMethods().stream()
                .map(PaymentMethod::getId)
                .collect(Collectors.toSet());

        Set<String> wrapperCodes = Arrays.stream(TradeStatistics3.PaymentMethodMapper.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of(), Sets.difference(paymentMethodCodes, wrapperCodes));
    }
}
