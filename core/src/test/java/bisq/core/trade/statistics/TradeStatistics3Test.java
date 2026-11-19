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
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.stablecoin.StableCoinPaymentRail;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Version;
import bisq.common.crypto.Hash;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.ExtraDataMapValidator;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import java.time.Clock;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TradeStatistics3Test {
    private static final long POST_STRICT_HASH_CHECK_DATE = 1_700_000_000_000L;

    // The payment method of a statistic is stored as the position in PaymentMethodMapper, so an entry must never
    // move. The positions of the stablecoin rails are pinned here by name. A new entry is added at the end of the
    // mapper and of this list.
    @Test
    public void stableCoinRailsKeepTheirPositionInThePaymentMethodMapper() {
        List<String> mappedFromPosition58 = List.of(
                "USDT_ERC20", "USDT_TRC20", "USDT_BEP20", "USDT_SPL",
                "USDC_ERC20", "USDC_SPL", "USDC_BEP20",
                "USDS_ERC20", "USDE_ERC20", "DAI_ERC20",
                "RLUSD_ERC20",
                "USDG_ERC20", "USDG_SPL",
                "PYUSD_ERC20", "PYUSD_SPL",
                "U_ERC20", "U_BEP20",
                "USDF_ERC20");
        assertEquals(56, TradeStatistics3.PaymentMethodMapper.SBP.ordinal());
        // The custom payment method came first in the release candidate
        assertEquals(57, TradeStatistics3.PaymentMethodMapper.CUSTOM.ordinal());
        for (int i = 0; i < mappedFromPosition58.size(); i++) {
            assertEquals(mappedFromPosition58.get(i), TradeStatistics3.PaymentMethodMapper.values()[58 + i].name());
        }
    }

    // A rail without an entry would be stored with its full name in every statistic
    @Test
    public void everyStableCoinRailIsInThePaymentMethodMapper() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            assertDoesNotThrow(() -> TradeStatistics3.PaymentMethodMapper.valueOf(rail.name()), rail.name());
        }
    }

    @Test
    public void stableCoinStatisticIsAValidUsdStatistic() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                1_000_000_000L,
                Coin.parseCoin("0.05").value,
                "USDT_TRC20",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertTrue(tradeStatistics.isValid());
        assertEquals("USDT_TRC20", tradeStatistics.getPaymentMethodId());
        assertEquals(String.valueOf(TradeStatistics3.PaymentMethodMapper.USDT_TRC20.ordinal()),
                tradeStatistics.toProtoTradeStatistics3().getPaymentMethod());
        assertEquals("5000", tradeStatistics.getTradeVolume().toPlainString());
    }

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
    public void storeSkipsAStatisticAboveTheSizeLimitWithItsOptionalFieldsOnLoad() {
        TradeStatistics3 valid = tradeStatistics("USD");
        protobuf.TradeStatistics3 oversized = aboveTheSizeLimit(valid);

        TradeStatistics3Store store = TradeStatistics3Store.fromProto(protobuf.TradeStatistics3Store.newBuilder()
                .addItems(oversized).addItems(valid.toProtoTradeStatistics3()).build());
        assertEquals(1, store.getMap().size());
        assertTrue(store.containsKey(new P2PDataStorage.ByteArray(valid.getHash())));
    }

    // Nodes up to v1.10.11 have no size limit and the hash does not cover the extra data, so they accept and relay
    // such a statistic. A statistic which fails to parse would count as a rule violation of the relay. It parses,
    // alone and in a bundle, and the store refuses it (TradeStatistics3StorageServiceTest).
    @Test
    public void relayedStatisticAboveTheSizeLimitParses() throws Exception {
        protobuf.TradeStatistics3 oversized = aboveTheSizeLimit(tradeStatistics("USD"));
        CoreNetworkProtoResolver resolver = new CoreNetworkProtoResolver(Clock.systemUTC());
        protobuf.NetworkEnvelope envelope = addPersistableNetworkPayloadEnvelope(oversized);

        NetworkEnvelope message = resolver.fromProto(envelope);

        TradeStatistics3 statistic = (TradeStatistics3) ((AddPersistableNetworkPayloadMessage) message)
                .getPersistableNetworkPayload();
        assertTrue(statistic.toProtoTradeStatistics3().getSerializedSize() > TradeStatistics3.MAX_SERIALIZED_BYTES);
        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(
                protobuf.BundleOfEnvelopes.newBuilder().addEnvelopes(envelope).build(),
                resolver,
                Version.getP2PMessageVersion());
        assertEquals(1, bundle.getEnvelopes().size());
        assertEquals(0, bundle.getNumUnparsableEnvelopes());
    }

    private static protobuf.TradeStatistics3 aboveTheSizeLimit(TradeStatistics3 tradeStatistics) {
        protobuf.TradeStatistics3 oversized = tradeStatistics.toProtoTradeStatistics3().toBuilder()
                .putExtraData("referralId", "x".repeat(TradeStatistics3.MAX_SERIALIZED_BYTES))
                .build();
        assertTrue(oversized.getSerializedSize() > TradeStatistics3.MAX_SERIALIZED_BYTES);
        return oversized;
    }

    @Test
    public void reloadCountsOnlyDistinctDecodableStatisticsTowardsCapacity() {
        TradeStatistics3 first = tradeStatistics("USD");
        TradeStatistics3 second = tradeStatistics("EUR");
        protobuf.TradeStatistics3Store records = protobuf.TradeStatistics3Store.newBuilder()
                .addItems(withWrongHash(second))
                .addItems(first.toProtoTradeStatistics3())
                .addItems(first.toProtoTradeStatistics3())
                .addItems(second.toProtoTradeStatistics3())
                .addItems(tradeStatistics("GBP").toProtoTradeStatistics3())
                .build();

        TradeStatistics3Store store = TradeStatistics3Store.fromProto(records, 2);
        assertEquals(2, store.getMap().size());
        assertTrue(store.containsKey(new P2PDataStorage.ByteArray(first.getHash())));
        assertTrue(store.containsKey(new P2PDataStorage.ByteArray(second.getHash())));
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

    @Test
    public void isValidRejectsDateMoreThanOneDayAhead() {
        long now = System.currentTimeMillis();
        long tenMinutes = TimeUnit.MINUTES.toMillis(10);

        assertTrue(usdStatistics(now + TradeStatistics3.FUTURE_DATE_TOLERANCE - tenMinutes).isValid());
        assertFalse(usdStatistics(now + TradeStatistics3.FUTURE_DATE_TOLERANCE + tenMinutes).isValid());
        assertFalse(usdStatistics(Long.MAX_VALUE).isValid());
    }

    // The fiat volume is amount * price / 10^8. It overflows for a price no trade can have, also within the cap
    @Test
    public void isValidRejectsFiatStatisticWhoseVolumeOverflows() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                Long.MAX_VALUE,
                TradeStatistics3.HISTORICAL_MAX_TRADE_AMOUNT,
                "SEPA",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertThrows(ArithmeticException.class, tradeStatistics::getTradeVolume);
        assertFalse(tradeStatistics.isValid());
    }

    // Before STRICT_FILTER_DATE neither the amount cap nor the currency list applies
    @Test
    public void isValidRejectsOldStatisticWhoseVolumeOverflows() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                "SEPA",
                1_600_000_000_000L,
                null,
                null,
                (TreeMap<String, String>) null);

        assertThrows(ArithmeticException.class, tradeStatistics::getTradeVolume);
        assertFalse(tradeStatistics.isValid());
    }

    @Test
    public void isValidRejectsPriceAboveTheCap() {
        assertTrue(usdStatistics(TradeStatistics3.MAX_PRICE, System.currentTimeMillis()).isValid());
        assertFalse(usdStatistics(TradeStatistics3.MAX_PRICE + 1, System.currentTimeMillis()).isValid());
        assertFalse(usdStatistics(Long.MAX_VALUE, System.currentTimeMillis()).isValid());
    }

    // The altcoin volume is amount * 10^8 / price, which is zero for a price above 10^8 times the amount
    @Test
    public void isValidRejectsAltcoinStatisticWithZeroVolume() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("XMR",
                200_000_000,
                1,
                "BLOCK_CHAINS",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertEquals(0, tradeStatistics.getTradeVolume().getValue());
        assertFalse(tradeStatistics.isValid());
    }

    // Nodes of earlier versions relay statistics which this version does not use. A rejection while parsing would count
    // as a rule violation of the relaying peer, so parsing accepts them.
    @Test
    public void addPersistableNetworkPayloadMessageFromProtoAcceptsStatisticWhichIsNotValid() throws Exception {
        TradeStatistics3 futureDated = usdStatistics(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30));
        protobuf.AddPersistableNetworkPayloadMessage proto = protobuf.AddPersistableNetworkPayloadMessage.newBuilder()
                .setPayload(futureDated.toProtoMessage())
                .build();

        AddPersistableNetworkPayloadMessage message = AddPersistableNetworkPayloadMessage.fromProto(proto,
                new CoreNetworkProtoResolver(Clock.systemUTC()),
                Version.getP2PMessageVersion());

        assertEquals(futureDated, message.getPersistableNetworkPayload());
        assertFalse(futureDated.isValid());
    }

    @Test
    public void isValidAcceptsAltcoinStatistic() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("XMR",
                500_000,
                Coin.parseCoin("0.25").value,
                "BLOCK_CHAINS",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertTrue(tradeStatistics.isValid());
        assertTrue(tradeStatistics.getTradeVolume().getValue() > 0);
    }

    // The payment method of a statistic is stored as its position in PaymentMethodMapper, so an entry must never
    // move once it is released. The statistic does not tell which custom payment method was used.
    @Test
    public void customPaymentMethodStatisticStoresThePositionOfThePaymentMethod() {
        assertEquals(57, TradeStatistics3.PaymentMethodMapper.CUSTOM.ordinal());

        TradeStatistics3 tradeStatistics = new TradeStatistics3("KES",
                130_000_000_000L,
                Coin.parseCoin("0.05").value,
                PaymentMethod.CUSTOM_ID,
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertTrue(tradeStatistics.isValid());
        assertEquals(PaymentMethod.CUSTOM_ID, tradeStatistics.getPaymentMethodId());
        assertEquals("57", tradeStatistics.toProtoTradeStatistics3().getPaymentMethod());
    }

    // A statistic which this node creates stores the position of every payment method of the mapper. A node of an
    // earlier version keeps a position which it does not know as it is, so it computes the same hash.
    @Test
    public void createdStatisticStoresThePositionOfEveryPaymentMethodOfTheMapper() {
        for (TradeStatistics3.PaymentMethodMapper paymentMethod : TradeStatistics3.PaymentMethodMapper.values()) {
            TradeStatistics3 tradeStatistics = created(paymentMethod.name());
            protobuf.TradeStatistics3 proto = tradeStatistics.toProtoTradeStatistics3();

            assertEquals(String.valueOf(paymentMethod.ordinal()), proto.getPaymentMethod());
            assertEquals(paymentMethod.name(), tradeStatistics.getPaymentMethodId());
            assertEquals(tradeStatistics, TradeStatistics3.fromProto(proto));
            assertEquals(proto, spelledOut(proto.getPaymentMethod()));
        }
    }

    // BSQ_SWAP is not in the mapper, so a payment method of the mapper shows that a BSQ swap statistic is created as
    // the others
    @Test
    public void createdBsqSwapStatisticStoresThePositionOfAPaymentMethodOfTheMapper() {
        Offer offer = mock(Offer.class);
        when(offer.getCurrencyCode()).thenReturn("BSQ");
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(PaymentMethod.CUSTOM_ID));
        BsqSwapTrade bsqSwapTrade = mock(BsqSwapTrade.class);
        when(bsqSwapTrade.getOffer()).thenReturn(offer);
        when(bsqSwapTrade.getPrice()).thenReturn(Price.valueOf("BSQ", 5_000));
        when(bsqSwapTrade.getAmountAsLong()).thenReturn(Coin.parseCoin("0.1").value);
        when(bsqSwapTrade.getTakeOfferDate()).thenReturn(POST_STRICT_HASH_CHECK_DATE);

        TradeStatistics3 tradeStatistics = TradeStatistics3.from(bsqSwapTrade);

        assertEquals(String.valueOf(TradeStatistics3.PaymentMethodMapper.CUSTOM.ordinal()),
                tradeStatistics.toProtoTradeStatistics3().getPaymentMethod());
    }

    // A payment method which no version has in its mapper, as MERCADO_PAGO or BSQ_SWAP, is kept as it is by all
    // versions
    @Test
    public void statisticWithAPaymentMethodOutsideOfTheMapperIsKeptAsItIs() {
        for (String paymentMethod : List.of(PaymentMethod.MERCADO_PAGO_ID, PaymentMethod.BSQ_SWAP_ID)) {
            protobuf.TradeStatistics3 proto = created(paymentMethod).toProtoTradeStatistics3();

            assertEquals(spelledOut(paymentMethod), proto);
            assertEquals(paymentMethod, TradeStatistics3.fromProto(proto).getPaymentMethodId());
        }
    }

    // The check of a received statistic replaces the names up to SBP, which is the end of the mapper of v1.10.4 to
    // v1.10.11 with its 57 entries. An entry inserted before SBP would move that bound.
    @Test
    public void sbpIsTheLastPaymentMethodOfTheMapperOfEarlierVersions() {
        assertEquals(56, TradeStatistics3.PaymentMethodMapper.SBP.ordinal());
        assertEquals(TradeStatistics3.PaymentMethodMapper.CUSTOM.ordinal(),
                TradeStatistics3.PaymentMethodMapper.SBP.ordinal() + 1);
    }

    // Nodes since v1.10.4 check the hash of a received statistic with only the names up to SBP replaced by their
    // positions. v1.10.11 accepts a name appended in this release spelled out with its hash over the name, so this
    // version accepts it and relays it unchanged.
    @Test
    public void receivedStatisticWithAPaymentMethodAppendedAfterSbpSpelledOutIsAccepted() {
        for (TradeStatistics3.PaymentMethodMapper paymentMethod : TradeStatistics3.PaymentMethodMapper.values()) {
            if (paymentMethod.ordinal() <= TradeStatistics3.PaymentMethodMapper.SBP.ordinal()) {
                continue;
            }
            protobuf.TradeStatistics3 proto = spelledOut(paymentMethod.name());

            TradeStatistics3 tradeStatistics = TradeStatistics3.fromProto(proto);

            assertEquals(paymentMethod.name(), tradeStatistics.getPaymentMethodId());
            assertEquals(proto, tradeStatistics.toProtoTradeStatistics3());
        }
    }

    // Nodes since v1.10.4 reject a name up to SBP spelled out with its hash over the name, while they parse it, and
    // count that as a rule violation of the relay. This version must not accept and relay it.
    @Test
    public void receivedStatisticWithAPaymentMethodUpToSbpSpelledOutIsCheckedAsByEarlierVersions() {
        for (TradeStatistics3.PaymentMethodMapper paymentMethod : TradeStatistics3.PaymentMethodMapper.values()) {
            if (paymentMethod.ordinal() > TradeStatistics3.PaymentMethodMapper.SBP.ordinal()) {
                continue;
            }
            assertThrows(InvalidPersistableNetworkPayloadException.class,
                    () -> TradeStatistics3.fromProto(spelledOut(paymentMethod.name())),
                    paymentMethod.name());

            protobuf.TradeStatistics3 withHashOverThePosition = created(paymentMethod.name()).toProtoTradeStatistics3()
                    .toBuilder()
                    .setPaymentMethod(paymentMethod.name())
                    .build();
            assertEquals(String.valueOf(paymentMethod.ordinal()),
                    TradeStatistics3.fromProto(withHashOverThePosition).toProtoTradeStatistics3().getPaymentMethod());
        }
    }

    // A node of v1.10.11 relays statistics with names appended in this release spelled out, alone or in a bundle. A
    // statistic which fails to parse would count as a rule violation of the relay.
    @Test
    public void relayedStatisticsWithPaymentMethodsAppendedAfterSbpSpelledOutParse() throws Exception {
        CoreNetworkProtoResolver resolver = new CoreNetworkProtoResolver(Clock.systemUTC());
        List<protobuf.NetworkEnvelope> envelopes = List.of(
                addPersistableNetworkPayloadEnvelope(spelledOut(PaymentMethod.CUSTOM_ID)),
                addPersistableNetworkPayloadEnvelope(spelledOut(StableCoinPaymentRail.USDT_TRC20.name())));

        for (protobuf.NetworkEnvelope envelope : envelopes) {
            assertTrue(resolver.fromProto(envelope) instanceof AddPersistableNetworkPayloadMessage);
        }
        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(
                protobuf.BundleOfEnvelopes.newBuilder().addAllEnvelopes(envelopes).build(),
                resolver,
                Version.getP2PMessageVersion());
        assertEquals(2, bundle.getEnvelopes().size());
        assertEquals(0, bundle.getNumUnparsableEnvelopes());
    }

    private static TradeStatistics3 created(String paymentMethod) {
        return new TradeStatistics3("USD",
                50_000_000,
                Coin.parseCoin("0.25").value,
                paymentMethod,
                POST_STRICT_HASH_CHECK_DATE,
                null,
                null,
                (TreeMap<String, String>) null);
    }

    // The statistic as a node creates it for a payment method which is not in its mapper, with the payment method as
    // given and the hash over it: v1.10.11 for a payment method appended in this release
    private static protobuf.TradeStatistics3 spelledOut(String paymentMethod) {
        String paymentMethodOutsideOfTheMapper = "NOT_IN_THE_MAPPER";
        TradeStatistics3 template = created(paymentMethodOutsideOfTheMapper);
        String json = JsonUtil.objectToJson(template);
        String jsonWithPaymentMethod = json.replace("\"" + paymentMethodOutsideOfTheMapper + "\"",
                "\"" + paymentMethod + "\"");
        assertNotEquals(json, jsonWithPaymentMethod);
        byte[] hash = Hash.getSha256Ripemd160hash(jsonWithPaymentMethod.getBytes(StandardCharsets.UTF_8));
        return template.toProtoTradeStatistics3().toBuilder()
                .setPaymentMethod(paymentMethod)
                .setHash(ByteString.copyFrom(hash))
                .build();
    }

    private static protobuf.NetworkEnvelope addPersistableNetworkPayloadEnvelope(protobuf.TradeStatistics3 tradeStatistics) {
        return protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setAddPersistableNetworkPayloadMessage(protobuf.AddPersistableNetworkPayloadMessage.newBuilder()
                        .setPayload(toPersistableNetworkPayload(tradeStatistics)))
                .build();
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

    private static TradeStatistics3 usdStatistics(long date) {
        return usdStatistics(50_000_000, date);
    }

    private static TradeStatistics3 usdStatistics(long price, long date) {
        return new TradeStatistics3("USD",
                price,
                Coin.parseCoin("0.25").value,
                "SEPA",
                date,
                null,
                null,
                (TreeMap<String, String>) null);
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
