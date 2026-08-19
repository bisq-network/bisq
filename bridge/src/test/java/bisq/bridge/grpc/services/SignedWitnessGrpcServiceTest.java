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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.bridge.grpc.services;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.account.witness.SignedWitnessOwnershipProof;
import bisq.core.account.witness.WitnessReputationPrivacy;

import bisq.common.util.Hex;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import com.google.protobuf.ByteString;

import java.util.stream.Stream;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;



import bisq.bridge.protobuf.SignedWitnessDateRequest;
import bisq.bridge.protobuf.SignedWitnessDateResponse;
import bisq.bridge.protobuf.SignedWitnessOwnershipRequest;
import bisq.bridge.protobuf.SignedWitnessOwnershipResponse;

class SignedWitnessGrpcServiceTest {
    @Test
    void dateOnlyAuthorizationEndpointFailsClosed() {
        SignedWitnessGrpcService service = new SignedWitnessGrpcService(mock(AccountAgeWitnessService.class));
        @SuppressWarnings("unchecked")
        StreamObserver<SignedWitnessDateResponse> observer = mock(StreamObserver.class);

        service.requestSignedWitnessDate(
                SignedWitnessDateRequest.newBuilder().setHashAsHex("12".repeat(20)).build(), observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.FAILED_PRECONDITION, Status.fromThrowable(error.getValue()).getCode());
        verify(observer, never()).onNext(any());
    }

    @Test
    void verifiedOwnershipReturnsOnlyTheSignDateBucketAndNullifier() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        long date = 1_700_000_123_456L;
        when(accountAgeWitnessService.verifySignedWitnessOwnership(any())).thenReturn(date);
        SignedWitnessGrpcService service = new SignedWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<SignedWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifySignedWitnessOwnership(validStructure(), observer);

        ArgumentCaptor<SignedWitnessOwnershipResponse> response =
                ArgumentCaptor.forClass(SignedWitnessOwnershipResponse.class);
        verify(observer).onNext(response.capture());
        verify(observer).onCompleted();
        assertEquals(WitnessReputationPrivacy.toDateBucket(date), response.getValue().getDateBucket());
        assertEquals("f8595c82649513a9df373f4d14077922585b6ed555fdabc0c6c4e6967aa4f563",
                Hex.encode(response.getValue().getWitnessNullifier().toByteArray()));
    }

    @Test
    void missingQualifyingChainIsARequestError() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        when(accountAgeWitnessService.verifySignedWitnessOwnership(any()))
                .thenThrow(new IllegalArgumentException("no qualifying chain"));
        SignedWitnessGrpcService service = new SignedWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<SignedWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifySignedWitnessOwnership(validStructure(), observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(error.getValue()).getCode());
    }

    @ParameterizedTest
    @MethodSource("oversizedByteFields")
    void oversizedByteFieldIsRejectedBeforeProofVerification(SignedWitnessOwnershipRequest request) {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        SignedWitnessGrpcService service = new SignedWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<SignedWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifySignedWitnessOwnership(request, observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(error.getValue()).getCode());
        verifyNoInteractions(accountAgeWitnessService);
    }

    private static Stream<SignedWitnessOwnershipRequest> oversizedByteFields() {
        return Stream.of(
                validStructure().toBuilder()
                        .setWitnessHash(ByteString.copyFrom(new byte[21]))
                        .build(),
                validStructure().toBuilder()
                        .setAccountInputDataWithSalt(ByteString.copyFrom(
                                new byte[SignedWitnessOwnershipProof.MAX_ACCOUNT_INPUT_LENGTH + 1]))
                        .build(),
                validStructure().toBuilder()
                        .setOwnerPublicKey(ByteString.copyFrom(new byte[601]))
                        .build(),
                validStructure().toBuilder()
                        .setSignature(ByteString.copyFrom(new byte[61]))
                        .build());
    }

    private static SignedWitnessOwnershipRequest validStructure() {
        return SignedWitnessOwnershipRequest.newBuilder()
                .setProtocolVersion(SignedWitnessOwnershipProof.VERSION)
                .setProfileId("12".repeat(20))
                .setWitnessHash(ByteString.copyFrom(new byte[20]))
                .setAccountInputDataWithSalt(ByteString.copyFromUtf8("input"))
                .setOwnerPublicKey(ByteString.copyFrom(new byte[400]))
                .setSignature(ByteString.copyFrom(new byte[40]))
                .build();
    }
}
