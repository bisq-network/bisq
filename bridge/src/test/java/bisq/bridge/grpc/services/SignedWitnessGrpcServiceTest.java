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

import bisq.bridge.protobuf.SignedWitnessDateRequest;
import bisq.bridge.protobuf.SignedWitnessDateResponse;
import bisq.bridge.protobuf.SignedWitnessOwnershipRequest;
import bisq.bridge.protobuf.SignedWitnessOwnershipResponse;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.account.witness.SignedWitnessOwnershipProof;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void verifiedOwnershipReturnsTheQualifyingSignDate() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        long date = System.currentTimeMillis() - 1000;
        when(accountAgeWitnessService.verifySignedWitnessOwnership(any())).thenReturn(date);
        SignedWitnessGrpcService service = new SignedWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<SignedWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifySignedWitnessOwnership(validStructure(), observer);

        ArgumentCaptor<SignedWitnessOwnershipResponse> response =
                ArgumentCaptor.forClass(SignedWitnessOwnershipResponse.class);
        verify(observer).onNext(response.capture());
        verify(observer).onCompleted();
        assertEquals(date, response.getValue().getDate());
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
