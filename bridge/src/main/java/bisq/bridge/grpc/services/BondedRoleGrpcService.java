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

import bisq.core.dao.SignVerifyService;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.state.DaoStateService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.security.SignatureException;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.protobuf.BondedRoleGrpcServiceGrpc;
import bisq.bridge.protobuf.BondedRoleVerificationRequest;
import bisq.bridge.protobuf.BondedRoleVerificationResponse;

@Slf4j
public class BondedRoleGrpcService extends BondedRoleGrpcServiceGrpc.BondedRoleGrpcServiceImplBase {
    private final DaoStateService daoStateService;
    private final BondedRolesRepository bondedRolesRepository;
    private final SignVerifyService signVerifyService;

    @Inject
    public BondedRoleGrpcService(DaoStateService daoStateService,
                                 BondedRolesRepository bondedRolesRepository,
                                 SignVerifyService signVerifyService) {
        this.daoStateService = daoStateService;
        this.bondedRolesRepository = bondedRolesRepository;
        this.signVerifyService = signVerifyService;
    }

    @Override
    public void requestBondedRoleVerification(BondedRoleVerificationRequest request,
                                              StreamObserver<BondedRoleVerificationResponse> responseObserver) {
        try {
            String bondUserName = request.getBondUserName();
            String roleType = request.getRoleType();
            String profileId = request.getProfileId();
            String signatureBase64 = request.getSignatureBase64();

            log.info("Received request for verifying a bonded role. bondUserName={}, roleType={}, profileId={}, signatureBase64={}",
                    bondUserName, roleType, profileId, signatureBase64);

            Optional<String> errorMessage = bondedRolesRepository.getAcceptedBonds().stream()
                    .filter(bondedRole -> bondedRole.getBondedAsset().getBondedRoleType().name().equals(roleType))
                    .filter(bondedRole -> bondedRole.getBondedAsset().getName().equals(bondUserName))
                    .filter(bondedRole -> bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED)
                    .flatMap(bondedRole -> daoStateService.getTx(bondedRole.getLockupTxId()).stream())
                    .flatMap(tx -> tx.getTxInputs().stream().findFirst().stream())
                    .map(txInput -> {
                        try {
                            signVerifyService.verify(profileId, txInput.getPubKey(), signatureBase64);
                            log.info("Successfully verified bonded role");
                            return Optional.<String>empty();
                        } catch (SignatureException e) {
                            return Optional.of("Signature verification failed.");
                        }
                    })
                    .findAny()
                    .orElseGet(() -> {
                        String message = "Did not find a bonded role matching the parameters";
                        log.warn(message);
                        return Optional.of(message);
                    });

            BondedRoleVerificationResponse.Builder builder = BondedRoleVerificationResponse.newBuilder();
            errorMessage.ifPresent(builder::setErrorMessage);
            BondedRoleVerificationResponse response = builder.build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("requestBondedRoleVerification failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error at bonded role verification")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
