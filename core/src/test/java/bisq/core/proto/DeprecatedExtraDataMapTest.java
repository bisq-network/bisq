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

package bisq.core.proto;

import bisq.core.alert.Alert;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.filter.Filter;
import bisq.core.filter.TestFilter;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.ProofOfWork;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import com.google.protobuf.Message;

import org.bitcoinj.core.ECKey;

import java.security.PublicKey;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.bitcoinj.core.Utils.HEX;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeprecatedExtraDataMapTest {
    @Test
    public void alertRejectsNonEmptyExtraDataMap() {
        protobuf.Alert proto = alert().toProtoMessage().getAlert();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                Alert::fromProto,
                protobuf.Alert::getExtraDataMap);
    }

    @Test
    public void filterRejectsNonEmptyExtraDataMap() {
        protobuf.Filter proto = filter().toProtoMessage().getFilter();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                Filter::fromProto,
                protobuf.Filter::getExtraDataMap);
    }

    @Test
    public void arbitratorRejectsNonEmptyExtraDataMap() {
        protobuf.Arbitrator proto = arbitrator().toProtoMessage().getArbitrator();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                Arbitrator::fromProto,
                protobuf.Arbitrator::getExtraDataMap);
    }

    @Test
    public void mediatorRejectsNonEmptyExtraDataMap() {
        protobuf.Mediator proto = mediator().toProtoMessage().getMediator();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                Mediator::fromProto,
                protobuf.Mediator::getExtraDataMap);
    }

    @Test
    public void refundAgentRejectsNonEmptyExtraDataMap() {
        protobuf.RefundAgent proto = refundAgent().toProtoMessage().getRefundAgent();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                RefundAgent::fromProto,
                protobuf.RefundAgent::getExtraDataMap);
    }

    @Test
    public void tempProposalPayloadRejectsNonEmptyExtraDataMap() {
        protobuf.TempProposalPayload proto = tempProposalPayload().toProtoMessage().getTempProposalPayload();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                TempProposalPayload::fromProto,
                protobuf.TempProposalPayload::getExtraDataMap);
    }

    @Test
    public void bsqSwapOfferPayloadRejectsNonEmptyExtraDataMap() {
        protobuf.BsqSwapOfferPayload proto = bsqSwapOfferPayload().toProtoMessage().getBsqSwapOfferPayload();

        assertDeprecatedExtraDataMap(proto,
                p -> p.toBuilder().putAllExtraData(Collections.emptyMap()).build(),
                p -> p.toBuilder().putExtraData("key", "value").build(),
                BsqSwapOfferPayload::fromProto,
                protobuf.BsqSwapOfferPayload::getExtraDataMap);
    }

    private static <T extends Message> void assertDeprecatedExtraDataMap(
            T proto,
            Function<T, T> addEmptyExtraDataMap,
            Function<T, T> addNonEmptyExtraDataMap,
            Function<T, ?> fromProto,
            Function<T, ? extends java.util.Map<String, String>> extraDataMap) {
        assertTrue(extraDataMap.apply(proto).isEmpty());
        assertNotNull(fromProto.apply(proto));

        T protoWithEmptyExtraDataMap = addEmptyExtraDataMap.apply(proto);
        assertTrue(extraDataMap.apply(protoWithEmptyExtraDataMap).isEmpty());
        assertArrayEquals(proto.toByteArray(), protoWithEmptyExtraDataMap.toByteArray());
        assertNotNull(fromProto.apply(protoWithEmptyExtraDataMap));

        T protoWithNonEmptyExtraDataMap = addNonEmptyExtraDataMap.apply(proto);
        assertThrows(IllegalArgumentException.class, () -> fromProto.apply(protoWithNonEmptyExtraDataMap));
    }

    private static Alert alert() {
        return new Alert("message",
                true,
                false,
                "version",
                Sig.getPublicKeyBytes(signaturePublicKey()),
                "signature");
    }

    private static Filter filter() {
        return TestFilter.createFilter(signaturePublicKey(), HEX.encode(new ECKey().getPubKey()), 1L);
    }

    private static Arbitrator arbitrator() {
        return new Arbitrator(new NodeAddress("host", 1000),
                new ECKey().getPubKey(),
                "btcAddress",
                pubKeyRing(),
                List.of("en"),
                1L,
                new ECKey().getPubKey(),
                "registrationSignature",
                "email",
                "info");
    }

    private static Mediator mediator() {
        return new Mediator(new NodeAddress("host", 1000),
                pubKeyRing(),
                List.of("en"),
                1L,
                new ECKey().getPubKey(),
                "registrationSignature",
                "email",
                "info");
    }

    private static RefundAgent refundAgent() {
        return new RefundAgent(new NodeAddress("host", 1000),
                pubKeyRing(),
                List.of("en"),
                1L,
                new ECKey().getPubKey(),
                "registrationSignature",
                "email",
                "info");
    }

    private static TempProposalPayload tempProposalPayload() {
        return new TempProposalPayload(new GenericProposal("name", "link", null), signaturePublicKey());
    }

    private static BsqSwapOfferPayload bsqSwapOfferPayload() {
        return new BsqSwapOfferPayload("id",
                1L,
                new NodeAddress("host", 1000),
                pubKeyRing(),
                OfferDirection.BUY,
                1L,
                2L,
                1L,
                proofOfWork(),
                "version",
                1);
    }

    private static ProofOfWork proofOfWork() {
        return new ProofOfWork(new byte[]{1},
                1L,
                new byte[]{2},
                1.0,
                1L,
                new byte[]{3},
                1);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(signaturePublicKey(), Encryption.generateKeyPair().getPublic());
    }

    private static PublicKey signaturePublicKey() {
        return Sig.generateKeyPair().getPublic();
    }
}
