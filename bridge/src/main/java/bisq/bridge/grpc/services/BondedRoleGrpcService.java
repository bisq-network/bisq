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

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.protobuf.BondedRoleGrpcServiceGrpc;
import bisq.bridge.protobuf.BondedRoleVerificationRequest;
import bisq.bridge.protobuf.BondedRoleVerificationResponse;

@Slf4j
public class BondedRoleGrpcService extends BondedRoleGrpcServiceGrpc.BondedRoleGrpcServiceImplBase {
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
        try {
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
        } catch (Exception e) {
            log.error("requestBondedRoleVerification failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error at bonded role verification")
                    .withCause(e)
                    .asRuntimeException());
        }
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
