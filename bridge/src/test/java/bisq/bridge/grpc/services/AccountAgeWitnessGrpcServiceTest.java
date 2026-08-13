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

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessOwnershipProof;
import bisq.core.account.witness.AccountAgeWitnessService;

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



import bisq.bridge.protobuf.AccountAgeWitnessDateRequest;
import bisq.bridge.protobuf.AccountAgeWitnessDateResponse;
import bisq.bridge.protobuf.AccountAgeWitnessOwnershipRequest;
import bisq.bridge.protobuf.AccountAgeWitnessOwnershipResponse;

class AccountAgeWitnessGrpcServiceTest {
    @Test
    void dateOnlyAuthorizationEndpointFailsClosed() {
        AccountAgeWitnessGrpcService service = new AccountAgeWitnessGrpcService(mock(AccountAgeWitnessService.class));
        @SuppressWarnings("unchecked")
        StreamObserver<AccountAgeWitnessDateResponse> observer = mock(StreamObserver.class);

        service.requestAccountAgeWitnessDate(
                AccountAgeWitnessDateRequest.newBuilder().setHashAsHex("12".repeat(20)).build(), observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.FAILED_PRECONDITION, Status.fromThrowable(error.getValue()).getCode());
        verify(observer, never()).onNext(any());
    }

    @Test
    void verifiedOwnershipReturnsTheStoredWitnessDate() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        long date = System.currentTimeMillis() - 1000;
        AccountAgeWitness witness = new AccountAgeWitness(new byte[20], date);
        when(accountAgeWitnessService.verifyAccountAgeWitnessOwnership(any())).thenReturn(witness);
        AccountAgeWitnessGrpcService service = new AccountAgeWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<AccountAgeWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifyAccountAgeWitnessOwnership(validStructure(), observer);

        ArgumentCaptor<AccountAgeWitnessOwnershipResponse> response =
                ArgumentCaptor.forClass(AccountAgeWitnessOwnershipResponse.class);
        verify(observer).onNext(response.capture());
        verify(observer).onCompleted();
        assertEquals(date, response.getValue().getDate());
    }

    @Test
    void invalidOwnershipIsARequestError() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        when(accountAgeWitnessService.verifyAccountAgeWitnessOwnership(any()))
                .thenThrow(new IllegalArgumentException("invalid proof"));
        AccountAgeWitnessGrpcService service = new AccountAgeWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<AccountAgeWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifyAccountAgeWitnessOwnership(validStructure(), observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(error.getValue()).getCode());
    }

    @ParameterizedTest
    @MethodSource("oversizedByteFields")
    void oversizedByteFieldIsRejectedBeforeProofVerification(AccountAgeWitnessOwnershipRequest request) {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        AccountAgeWitnessGrpcService service = new AccountAgeWitnessGrpcService(accountAgeWitnessService);
        @SuppressWarnings("unchecked")
        StreamObserver<AccountAgeWitnessOwnershipResponse> observer = mock(StreamObserver.class);

        service.verifyAccountAgeWitnessOwnership(request, observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(error.getValue()).getCode());
        verifyNoInteractions(accountAgeWitnessService);
    }

    private static Stream<AccountAgeWitnessOwnershipRequest> oversizedByteFields() {
        return Stream.of(
                validStructure().toBuilder()
                        .setWitnessHash(ByteString.copyFrom(new byte[21]))
                        .build(),
                validStructure().toBuilder()
                        .setAccountInputDataWithSalt(ByteString.copyFrom(
                                new byte[AccountAgeWitnessOwnershipProof.MAX_ACCOUNT_INPUT_LENGTH + 1]))
                        .build(),
                validStructure().toBuilder()
                        .setOwnerPublicKey(ByteString.copyFrom(new byte[601]))
                        .build(),
                validStructure().toBuilder()
                        .setSignature(ByteString.copyFrom(new byte[61]))
                        .build());
    }

    private static AccountAgeWitnessOwnershipRequest validStructure() {
        return AccountAgeWitnessOwnershipRequest.newBuilder()
                .setProtocolVersion(AccountAgeWitnessOwnershipProof.VERSION)
                .setProfileId("12".repeat(20))
                .setWitnessHash(ByteString.copyFrom(new byte[20]))
                .setAccountInputDataWithSalt(ByteString.copyFromUtf8("input"))
                .setOwnerPublicKey(ByteString.copyFrom(new byte[400]))
                .setSignature(ByteString.copyFrom(new byte[40]))
                .build();
    }
}
