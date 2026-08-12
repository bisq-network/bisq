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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.account.witness.SignedWitnessOwnershipProof;
import bisq.bridge.protobuf.SignedWitnessDateRequest;
import bisq.bridge.protobuf.SignedWitnessDateResponse;
import bisq.bridge.protobuf.SignedWitnessGrpcServiceGrpc;
import bisq.bridge.protobuf.SignedWitnessOwnershipRequest;
import bisq.bridge.protobuf.SignedWitnessOwnershipResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SignedWitnessGrpcService extends SignedWitnessGrpcServiceGrpc.SignedWitnessGrpcServiceImplBase {
    private final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public SignedWitnessGrpcService(AccountAgeWitnessService accountAgeWitnessService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    @Override
    public void requestSignedWitnessDate(SignedWitnessDateRequest request,
                                         StreamObserver<SignedWitnessDateResponse> responseObserver) {
        responseObserver.onError(Status.FAILED_PRECONDITION
                .withDescription("Signed-witness authorization requires an ownership proof")
                .asRuntimeException());
    }

    @Override
    public void verifySignedWitnessOwnership(SignedWitnessOwnershipRequest request,
                                             StreamObserver<SignedWitnessOwnershipResponse> responseObserver) {
        try {
            SignedWitnessOwnershipProof proof = new SignedWitnessOwnershipProof(
                    request.getProtocolVersion(),
                    request.getProfileId(),
                    request.getWitnessHash().toByteArray(),
                    request.getAccountInputDataWithSalt().toByteArray(),
                    request.getOwnerPublicKey().toByteArray(),
                    request.getSignature().toByteArray());
            long date = accountAgeWitnessService.verifySignedWitnessOwnership(proof);
            log.info("Verified signed-witness ownership for hash {}",
                    bisq.common.util.Hex.encode(proof.getWitnessHash()));
            var response = SignedWitnessOwnershipResponse.newBuilder().setDate(date).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid signed-witness ownership proof", e);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("verifySignedWitnessOwnership failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
