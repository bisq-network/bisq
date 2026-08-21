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

import javax.inject.Inject;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.protobuf.BondedRoleGrpcServiceGrpc;
import bisq.bridge.protobuf.BondedRoleVerificationRequest;
import bisq.bridge.protobuf.BondedRoleVerificationResponse;
import bisq.bridge.protobuf.BondedRolesVerificationRequest;
import bisq.bridge.protobuf.BondedRolesVerificationResponse;

@Slf4j
public class BondedRoleGrpcService extends BondedRoleGrpcServiceGrpc.BondedRoleGrpcServiceImplBase {
    static final int MAX_BATCH_SIZE = 1_000;

    private final DaoFacade daoFacade;
    private final BondedRolesRepository bondedRolesRepository;

    @Inject
    public BondedRoleGrpcService(DaoFacade daoFacade,
                                 BondedRolesRepository bondedRolesRepository) {
        this.daoFacade = daoFacade;
        this.bondedRolesRepository = bondedRolesRepository;
    }

    @Override
    public void requestBondedRoleVerification(BondedRoleVerificationRequest request,
                                              StreamObserver<BondedRoleVerificationResponse> responseObserver) {
        executeOnUserThread(
                "requestBondedRoleVerification",
                "Error at bonded role verification",
                responseObserver,
                () -> doRequestBondedRoleVerification(request, responseObserver));
    }

    private void doRequestBondedRoleVerification(BondedRoleVerificationRequest request,
                                                 StreamObserver<BondedRoleVerificationResponse> responseObserver) {
        if (!daoFacade.isDaoStateReadyAndInSync()) {
            log.warn("Bonded role verification rejected because the DAO state is not ready and in sync. " +
                    "chainHeight={}", daoFacade.getChainHeight());
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("DAO state is not ready and in sync")
                    .asRuntimeException());
            return;
        }

        BondedRoleRegistration registration = bisq.bridge.grpc.messages.BondedRoleVerificationRequest
                .fromProto(request)
                .getRegistration();
        log.info("Received request for verifying a bonded role. bondUserName={}, roleType={}, profileId={}",
                registration.bondUserName(), registration.roleType(), registration.profileId());
        responseObserver.onNext(verifyBondedRole(registration));
        responseObserver.onCompleted();
    }

    @Override
    public void requestBondedRoleBatchVerification(BondedRolesVerificationRequest request,
                                               StreamObserver<BondedRolesVerificationResponse> responseObserver) {
        executeOnUserThread(
                "requestBondedRoleBatchVerification",
                "Error at bonded roles verification",
                responseObserver,
                () -> doRequestBondedRoleBatchVerification(request, responseObserver));
    }

    private void doRequestBondedRoleBatchVerification(BondedRolesVerificationRequest request,
                                                  StreamObserver<BondedRolesVerificationResponse> responseObserver) {
        if (!daoFacade.isDaoStateReadyAndInSync()) {
            log.warn("Bonded role batch verification rejected because the DAO state is not ready and in sync. " +
                    "chainHeight={}", daoFacade.getChainHeight());
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("DAO state is not ready and in sync")
                    .asRuntimeException());
            return;
        }
        if (request.getRegistrationsCount() > MAX_BATCH_SIZE) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Bonded role verification batch exceeds " + MAX_BATCH_SIZE + " registrations")
                    .asRuntimeException());
            return;
        }

        log.info("Received request for verifying {} bonded roles", request.getRegistrationsCount());
        BondedRolesVerificationResponse.Builder responseBuilder = BondedRolesVerificationResponse.newBuilder();
        responseBuilder.setDaoStateBlockHeight(daoFacade.getChainHeight());
        request.getRegistrationsList().stream()
                .map(bisq.bridge.grpc.messages.BondedRoleVerificationRequest::fromProto)
                .map(bisq.bridge.grpc.messages.BondedRoleVerificationRequest::getRegistration)
                .map(this::verifyBondedRole)
                .forEach(responseBuilder::addVerifications);
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    // DAO block parsing and completed-block repository updates run on UserThread. Queuing the complete request there
    // prevents a new block from interleaving the height read with proposal, transaction, or bond-state lookups.
    private void executeOnUserThread(String operation,
                                     String errorDescription,
                                     StreamObserver<?> responseObserver,
                                     Runnable request) {
        try {
            UserThread.execute(() -> {
                try {
                    request.run();
                } catch (Exception e) {
                    notifyInternalError(operation, errorDescription, responseObserver, e);
                }
            });
        } catch (RejectedExecutionException e) {
            notifyInternalError(operation, errorDescription, responseObserver, e);
        }
    }

    private void notifyInternalError(String operation,
                                     String errorDescription,
                                     StreamObserver<?> responseObserver,
                                     Exception exception) {
        log.error("{} failed", operation, exception);
        responseObserver.onError(Status.INTERNAL
                .withDescription(errorDescription)
                .withCause(exception)
                .asRuntimeException());
    }

    private BondedRoleVerificationResponse verifyBondedRole(BondedRoleRegistration registration) {
        Optional<String> errorMessage = Optional.empty();
        try {
            bondedRolesRepository.verifyBondedRole(registration);
        } catch (IllegalArgumentException e) {
            errorMessage = Optional.of("Bonded role invalid. " + e.getMessage());
        }

        BondedRoleVerificationResponse.Builder builder = BondedRoleVerificationResponse.newBuilder();
        errorMessage.ifPresent(builder::setErrorMessage);
        return builder.build();
    }
}
