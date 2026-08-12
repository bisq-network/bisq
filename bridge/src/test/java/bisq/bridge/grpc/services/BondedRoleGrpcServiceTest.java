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

import bisq.common.UserThread;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;



import bisq.bridge.protobuf.BondedRoleVerificationRequest;
import bisq.bridge.protobuf.BondedRoleVerificationResponse;
import bisq.bridge.protobuf.BondedRolesVerificationRequest;
import bisq.bridge.protobuf.BondedRolesVerificationResponse;

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
    private Executor originalUserThreadExecutor;
    private ExecutorService userThreadExecutor;

    @BeforeEach
    public void setup() {
        originalUserThreadExecutor = UserThread.getExecutor();
        daoFacade = mock(DaoFacade.class);
        when(daoFacade.isDaoStateReadyAndInSync()).thenReturn(true);
        bondedRolesRepository = mock(BondedRolesRepository.class);
        service = new BondedRoleGrpcService(daoFacade, bondedRolesRepository);
    }

    @AfterEach
    public void tearDown() {
        UserThread.setExecutor(originalUserThreadExecutor);
        if (userThreadExecutor != null) {
            userThreadExecutor.shutdownNow();
        }
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
    public void missingProtocolVersionUsesTheLegacyRegistrationFormat() {
        BondedRoleVerificationRequest request = BondedRoleVerificationRequest.newBuilder()
                .setBondUserName(BOND_USER_NAME)
                .setRoleType(ROLE_TYPE)
                .setProfileId(PROFILE_ID)
                .setSignatureBase64(SIGNATURE)
                .build();
        assertFalse(request.hasProtocolVersion());
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRoleVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleVerification(request, responseObserver);

        ArgumentCaptor<BondedRoleVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRoleVerificationResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        assertFalse(responseCaptor.getValue().hasErrorMessage());
        verify(bondedRolesRepository).verifyBondedRole(BondedRoleRegistration.legacy(
                BOND_USER_NAME, ROLE_TYPE, PROFILE_ID, SIGNATURE));
    }

    @Test
    public void explicitVersionZeroRemainsUnsupported() {
        BondedRoleVerificationRequest request = BondedRoleVerificationRequest.newBuilder()
                .setProtocolVersion(0)
                .setBondUserName(BOND_USER_NAME)
                .setRoleType(ROLE_TYPE)
                .setProfileId(PROFILE_ID)
                .setSignatureBase64(SIGNATURE)
                .build();
        assertTrue(request.hasProtocolVersion());
        BondedRoleRegistration unsupportedRegistration = new BondedRoleRegistration(
                0, BOND_USER_NAME, ROLE_TYPE, "", "", PROFILE_ID, SIGNATURE);
        doThrow(new IllegalArgumentException("Unsupported bonded-role registration protocol version: 0"))
                .when(bondedRolesRepository)
                .verifyBondedRole(unsupportedRegistration);
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRoleVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleVerification(request, responseObserver);

        ArgumentCaptor<BondedRoleVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRoleVerificationResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        assertEquals("Bonded role invalid. Unsupported bonded-role registration protocol version: 0",
                responseCaptor.getValue().getErrorMessage());
    }

    @Test
    public void unsupportedProtocolVersionIsRejectedExplicitly() {
        BondedRoleVerificationRequest request = request().toBuilder()
                .setProtocolVersion(3)
                .build();
        BondedRoleRegistration unsupportedRegistration = new BondedRoleRegistration(
                3, BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, SIGNATURE);
        doThrow(new IllegalArgumentException("Unsupported bonded-role registration protocol version: 3"))
                .when(bondedRolesRepository)
                .verifyBondedRole(unsupportedRegistration);
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRoleVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleVerification(request, responseObserver);

        ArgumentCaptor<BondedRoleVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRoleVerificationResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        assertEquals("Bonded role invalid. Unsupported bonded-role registration protocol version: 3",
                responseCaptor.getValue().getErrorMessage());
    }

    @Test
    public void batchVerificationReturnsEveryResultAtOneDaoHeight() {
        when(daoFacade.getChainHeight()).thenReturn(941_123);
        BondedRoleVerificationRequest secondRequest = request().toBuilder()
                .setLockupTxId("secondLockupTxId")
                .build();
        BondedRoleRegistration secondRegistration = BondedRoleRegistration.current(
                BOND_USER_NAME, ROLE_TYPE, PROPOSAL_TX_ID, "secondLockupTxId", PROFILE_ID, SIGNATURE);
        doThrow(new IllegalArgumentException("Role lockup is not confirmed and unspent"))
                .when(bondedRolesRepository)
                .verifyBondedRole(secondRegistration);
        BondedRolesVerificationRequest request = BondedRolesVerificationRequest.newBuilder()
                .addRegistrations(request())
                .addRegistrations(secondRequest)
                .build();
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRolesVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleBatchVerification(request, responseObserver);

        ArgumentCaptor<BondedRolesVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRolesVerificationResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
        BondedRolesVerificationResponse response = responseCaptor.getValue();
        assertEquals(941_123, response.getDaoStateBlockHeight());
        assertEquals(2, response.getVerificationsCount());
        assertFalse(response.getVerifications(0).hasErrorMessage());
        assertEquals("Bonded role invalid. Role lockup is not confirmed and unspent",
                response.getVerifications(1).getErrorMessage());
        verify(bondedRolesRepository).verifyBondedRole(REGISTRATION);
        verify(bondedRolesRepository).verifyBondedRole(secondRegistration);
    }

    @Test
    public void unexpectedRepositoryFailureRejectsTheWholeBatch() {
        doThrow(new IllegalStateException("Repository state unavailable"))
                .when(bondedRolesRepository)
                .verifyBondedRole(REGISTRATION);
        BondedRolesVerificationRequest request = BondedRolesVerificationRequest.newBuilder()
                .addRegistrations(request())
                .build();
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRolesVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleBatchVerification(request, responseObserver);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(errorCaptor.capture());
        assertEquals(Status.Code.INTERNAL, Status.fromThrowable(errorCaptor.getValue()).getCode());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    public void batchWaitsForBlockProcessingAndUsesTheCompletedConfiscationState() throws Exception {
        userThreadExecutor = Executors.newSingleThreadExecutor();
        UserThread.setExecutor(userThreadExecutor);
        CountDownLatch blockProcessingStarted = new CountDownLatch(1);
        CountDownLatch completeBlockProcessing = new CountDownLatch(1);
        Future<?> blockProcessing = userThreadExecutor.submit(() -> {
            blockProcessingStarted.countDown();
            try {
                assertTrue(completeBlockProcessing.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            // Simulate the completed-block repository update after this registration's lockup is confiscated.
            when(daoFacade.getChainHeight()).thenReturn(941_124);
            doThrow(new IllegalArgumentException("Role lockup is not confirmed and unspent"))
                    .when(bondedRolesRepository)
                    .verifyBondedRole(REGISTRATION);
        });
        assertTrue(blockProcessingStarted.await(2, TimeUnit.SECONDS));
        BondedRolesVerificationRequest request = BondedRolesVerificationRequest.newBuilder()
                .addRegistrations(request())
                .build();
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRolesVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleBatchVerification(request, responseObserver);

        verify(responseObserver, after(100).never()).onNext(any());
        completeBlockProcessing.countDown();

        ArgumentCaptor<BondedRolesVerificationResponse> responseCaptor =
                ArgumentCaptor.forClass(BondedRolesVerificationResponse.class);
        verify(responseObserver, timeout(2_000)).onNext(responseCaptor.capture());
        verify(responseObserver, timeout(2_000)).onCompleted();
        BondedRolesVerificationResponse response = responseCaptor.getValue();
        assertEquals(941_124, response.getDaoStateBlockHeight());
        assertEquals("Bonded role invalid. Role lockup is not confirmed and unspent",
                response.getVerifications(0).getErrorMessage());
        blockProcessing.get(2, TimeUnit.SECONDS);
    }

    @Test
    public void oversizedBatchIsRejectedBeforeVerification() {
        BondedRolesVerificationRequest.Builder requestBuilder = BondedRolesVerificationRequest.newBuilder();
        for (int i = 0; i <= BondedRoleGrpcService.MAX_BATCH_SIZE; i++) {
            requestBuilder.addRegistrations(request());
        }
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRolesVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleBatchVerification(requestBuilder.build(), responseObserver);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(errorCaptor.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(errorCaptor.getValue()).getCode());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
        verifyNoInteractions(bondedRolesRepository);
    }

    @Test
    public void batchVerificationRequiresReadyAndSynchronizedDaoState() {
        when(daoFacade.isDaoStateReadyAndInSync()).thenReturn(false);
        BondedRolesVerificationRequest request = BondedRolesVerificationRequest.newBuilder()
                .addRegistrations(request())
                .build();
        @SuppressWarnings("unchecked")
        StreamObserver<BondedRolesVerificationResponse> responseObserver = mock(StreamObserver.class);

        service.requestBondedRoleBatchVerification(request, responseObserver);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(errorCaptor.capture());
        assertEquals(Status.Code.FAILED_PRECONDITION, Status.fromThrowable(errorCaptor.getValue()).getCode());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
        verifyNoInteractions(bondedRolesRepository);
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
