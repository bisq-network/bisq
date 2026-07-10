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
import bisq.core.proto.network.CoreNetworkProtoResolver;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;
import bisq.common.util.ExtraDataMapValidator;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import java.time.Clock;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TradeStatistics3Test {
    private static final long POST_STRICT_HASH_CHECK_DATE = 1_700_000_000_000L;

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
        protobuf.TradeStatistics3 treeMapProto = tradeStatistics("USD", extraDataMap()).toProtoTradeStatistics3();
        protobuf.TradeStatistics3 hashMapProto = treeMapProto.toBuilder()
                .clearExtraData()
                .putAllExtraData(hashMap)
                .build();

        TradeStatistics3 tradeStatistics = TradeStatistics3.fromProto(hashMapProto);

        assertTrue(tradeStatistics.getExtraDataMap() instanceof TreeMap);
        assertArrayEquals(hashMapProto.toByteArray(), tradeStatistics.toProtoTradeStatistics3().toByteArray());
    }

    @Test
    public void fromProtoPreservesNullExtraDataMap() {
        protobuf.TradeStatistics3 proto = tradeStatistics("USD").toProtoTradeStatistics3();

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
        protobuf.TradeStatistics3 proto = tradeStatistics("USD").toProtoTradeStatistics3()
                .toBuilder()
                .putAllExtraData(oversizedMap)
                .build();

        TradeStatistics3 tradeStatistics = TradeStatistics3.fromProto(proto);

        Map<String, String> extraDataMap = tradeStatistics.getExtraDataMap();
        assertNotNull(extraDataMap);
        assertTrue(extraDataMap instanceof TreeMap);
        assertTrue(extraDataMap.isEmpty());
        assertTrue(tradeStatistics.toProtoTradeStatistics3().getExtraDataMap().isEmpty());
    }

    @Test
    public void fromProtoThrowsIfHashDoesNotMatchTradeStatisticsData() {
        TradeStatistics3 tradeStatistics = tradeStatistics("USD");

        assertThrows(InvalidPersistableNetworkPayloadException.class,
                () -> TradeStatistics3.fromProto(withWrongHash(tradeStatistics)));
    }

    @Test
    public void tradeStatistics3StoreFromProtoSkipsOnlyInvalidTradeStatistics() {
        TradeStatistics3 validTradeStatistics = tradeStatistics("USD");
        TradeStatistics3 invalidTradeStatistics = tradeStatistics("EUR");
        protobuf.TradeStatistics3Store proto = protobuf.TradeStatistics3Store.newBuilder()
                .addItems(validTradeStatistics.toProtoTradeStatistics3())
                .addItems(withWrongHash(invalidTradeStatistics))
                .build();

        TradeStatistics3Store tradeStatistics3Store = TradeStatistics3Store.fromProto(proto);

        assertEquals(1, tradeStatistics3Store.getMap().size());
        assertTrue(tradeStatistics3Store.containsKey(new P2PDataStorage.ByteArray(validTradeStatistics.getHash())));
    }

    @Test
    public void getDataResponseFromProtoSkipsOnlyInvalidTradeStatistics() {
        TradeStatistics3 validTradeStatistics = tradeStatistics("USD");
        TradeStatistics3 invalidTradeStatistics = tradeStatistics("EUR");
        protobuf.GetDataResponse proto = protobuf.GetDataResponse.newBuilder()
                .addPersistableNetworkPayloadItems(toPersistableNetworkPayload(validTradeStatistics.toProtoTradeStatistics3()))
                .addPersistableNetworkPayloadItems(toPersistableNetworkPayload(withWrongHash(invalidTradeStatistics)))
                .build();

        GetDataResponse getDataResponse = GetDataResponse.fromProto(proto,
                new CoreNetworkProtoResolver(Clock.systemUTC()),
                Version.getP2PMessageVersion());

        Set<PersistableNetworkPayload> payloads = getDataResponse.getPersistableNetworkPayloadSet();
        assertEquals(1, payloads.size());
        TradeStatistics3 tradeStatistics = (TradeStatistics3) payloads.iterator().next();
        assertEquals("USD", tradeStatistics.getCurrency());
        assertArrayEquals(validTradeStatistics.getHash(), tradeStatistics.getHash());
    }

    @Test
    public void addPersistableNetworkPayloadMessageFromProtoWrapsInvalidTradeStatistics() {
        TradeStatistics3 invalidTradeStatistics = tradeStatistics("EUR");
        protobuf.AddPersistableNetworkPayloadMessage proto = protobuf.AddPersistableNetworkPayloadMessage.newBuilder()
                .setPayload(toPersistableNetworkPayload(withWrongHash(invalidTradeStatistics)))
                .build();

        assertThrows(ProtobufferException.class,
                () -> AddPersistableNetworkPayloadMessage.fromProto(proto,
                        new CoreNetworkProtoResolver(Clock.systemUTC()),
                        Version.getP2PMessageVersion()));
    }

    @Test
    public void bundleOfEnvelopesFromProtoSkipsInvalidAddPersistableNetworkPayloadMessage() {
        TradeStatistics3 validTradeStatistics = tradeStatistics("USD");
        TradeStatistics3 invalidTradeStatistics = tradeStatistics("EUR");
        protobuf.NetworkEnvelope invalidEnvelope = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setAddPersistableNetworkPayloadMessage(protobuf.AddPersistableNetworkPayloadMessage.newBuilder()
                        .setPayload(toPersistableNetworkPayload(withWrongHash(invalidTradeStatistics))))
                .build();
        protobuf.BundleOfEnvelopes proto = protobuf.BundleOfEnvelopes.newBuilder()
                .addEnvelopes(invalidEnvelope)
                .addEnvelopes(new AddPersistableNetworkPayloadMessage(validTradeStatistics).toProtoNetworkEnvelope())
                .build();

        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(proto,
                new CoreNetworkProtoResolver(Clock.systemUTC()),
                Version.getP2PMessageVersion());

        assertEquals(1, bundle.getEnvelopes().size());
        assertTrue(bundle.getEnvelopes().get(0) instanceof AddPersistableNetworkPayloadMessage);
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

    private static TradeStatistics3 tradeStatistics(String currency) {
        return tradeStatistics(currency, null);
    }

    private static TradeStatistics3 tradeStatistics(String currency, TreeMap<String, String> extraDataMap) {
        return new TradeStatistics3(currency,
                50_000_000,
                Coin.parseCoin("0.25").value,
                "SEPA",
                POST_STRICT_HASH_CHECK_DATE,
                "mediator",
                "refundAgent",
                extraDataMap);
    }

    private static TreeMap<String, String> extraDataMap() {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put(REFERRAL_ID, "referralId");
        return extraDataMap;
    }

    private static protobuf.TradeStatistics3 withWrongHash(TradeStatistics3 tradeStatistics) {
        byte[] wrongHash = tradeStatistics.getHash().clone();
        wrongHash[0] ^= 1;
        return tradeStatistics.toProtoTradeStatistics3().toBuilder()
                .setHash(ByteString.copyFrom(wrongHash))
                .build();
    }

    private static protobuf.PersistableNetworkPayload toPersistableNetworkPayload(protobuf.TradeStatistics3 tradeStatistics) {
        return protobuf.PersistableNetworkPayload.newBuilder()
                .setTradeStatistics3(tradeStatistics)
                .build();
    }
}
