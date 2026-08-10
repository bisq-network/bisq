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

package bisq.bridge.grpc.services;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.role.BondedRoleRegistration;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;



import bisq.bridge.protobuf.BondedRoleVerificationRequest;
import bisq.bridge.protobuf.BondedRoleVerificationResponse;

public class BondedRoleGrpcServiceTest {
    private static final String BOND_USER_NAME = "alice";
    private static final String ROLE_TYPE = "NETLAYER_MAINTAINER";
    private static final String PROFILE_ID = "profileId";
    private static final String SIGNATURE = "signature";
    private static final String PROPOSAL_TX_ID = "proposalTxId";
    private static final String LOCKUP_TX_ID = "lockupTxId";
    private static final BondedRoleRegistration REGISTRATION = BondedRoleRegistration.current(
            BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, SIGNATURE);

    private DaoFacade daoFacade;
    private BondedRolesRepository bondedRolesRepository;
    private BondedRoleGrpcService service;

    @BeforeEach
    public void setup() {
        daoFacade = mock(DaoFacade.class);
        when(daoFacade.isDaoStateReadyAndInSync()).thenReturn(true);
        bondedRolesRepository = mock(BondedRolesRepository.class);
        service = new BondedRoleGrpcService(daoFacade, bondedRolesRepository);
    }

    @Test
    public void successfulRepositoryVerificationReturnsSuccess() {
        BondedRoleVerificationResponse response = requestVerification();

        assertFalse(response.hasErrorMessage());
        verify(bondedRolesRepository).verifyBondedRole(REGISTRATION);
    }

    @Test
    public void rejectedRepositoryVerificationReturnsTheFailure() {
        doThrow(new IllegalArgumentException("Invalid signature"))
                .when(bondedRolesRepository)
                .verifyBondedRole(REGISTRATION);

        BondedRoleVerificationResponse response = requestVerification();

        assertEquals("Bonded role invalid. Invalid signature", response.getErrorMessage());
    }

    @Test
    public void daoStateWhichIsNotReadyAndInSyncIsRejected() {
        when(daoFacade.isDaoStateReadyAndInSync()).thenReturn(false);
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRoleVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleVerification(request(), responseObserver);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(errorCaptor.capture());
        assertEquals(Status.Code.FAILED_PRECONDITION,
                Status.fromThrowable(errorCaptor.getValue()).getCode());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
        verifyNoInteractions(bondedRolesRepository);
    }

    @Test
    public void missingProtocolVersionIsRejectedExplicitly() {
        BondedRoleVerificationRequest request = request().toBuilder()
                .clearProtocolVersion()
                .build();
        doThrow(new IllegalArgumentException("Unsupported bonded-role registration protocol version: 0"))
                .when(bondedRolesRepository)
                .verifyBondedRole(new BondedRoleRegistration(
                        0, BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, SIGNATURE));
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRoleVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleVerification(request, responseObserver);

        ArgumentCaptor<BondedRoleVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRoleVerificationResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        assertEquals("Bonded role invalid. Unsupported bonded-role registration protocol version: 0",
                responseCaptor.getValue().getErrorMessage());
    }

    private BondedRoleVerificationResponse requestVerification() {
        BondedRoleVerificationRequest request = request();
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRoleVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleVerification(request, responseObserver);

        ArgumentCaptor<BondedRoleVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRoleVerificationResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
        return responseCaptor.getValue();
    }

    private BondedRoleVerificationRequest request() {
        return BondedRoleVerificationRequest.newBuilder()
                .setProtocolVersion(BondedRoleRegistration.CURRENT_PROTOCOL_VERSION)
                .setBondUserName(BOND_USER_NAME)
                .setRoleType(ROLE_TYPE)
                .setProfileId(PROFILE_ID)
                .setSignatureBase64(SIGNATURE)
                .setProposalTxId(PROPOSAL_TX_ID)
                .setLockupTxId(LOCKUP_TX_ID)
                .build();
    }
}
