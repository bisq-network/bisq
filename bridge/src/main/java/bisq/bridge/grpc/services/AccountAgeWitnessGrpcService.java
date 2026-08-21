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

import bisq.bridge.protobuf.AccountAgeWitnessDateRequest;
import bisq.bridge.protobuf.AccountAgeWitnessDateResponse;
import bisq.bridge.protobuf.AccountAgeWitnessGrpcServiceGrpc;
import bisq.bridge.protobuf.AccountAgeWitnessOwnershipRequest;
import bisq.bridge.protobuf.AccountAgeWitnessOwnershipResponse;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessOwnershipProof;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.account.witness.WitnessReputationPrivacy;
import bisq.core.account.witness.WitnessOwnershipProof;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountAgeWitnessGrpcService extends AccountAgeWitnessGrpcServiceGrpc.AccountAgeWitnessGrpcServiceImplBase {
    private final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public AccountAgeWitnessGrpcService(AccountAgeWitnessService accountAgeWitnessService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    @Override
    public void requestAccountAgeWitnessDate(AccountAgeWitnessDateRequest request,
                                             StreamObserver<AccountAgeWitnessDateResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Account age authorization requires an ownership proof")
                .asRuntimeException());
    }

    @Override
    public void verifyAccountAgeWitnessOwnership(AccountAgeWitnessOwnershipRequest request,
                                                 StreamObserver<AccountAgeWitnessOwnershipResponse> responseObserver) {
        try {
            WitnessOwnershipProof.validateByteArrayLengths(
                    request.getWitnessHash().size(),
                    request.getAccountInputDataWithSalt().size(),
                    request.getOwnerPublicKey().size(),
                    request.getSignature().size());
            AccountAgeWitnessOwnershipProof proof = new AccountAgeWitnessOwnershipProof(
                    request.getProtocolVersion(),
                    request.getProfileId(),
                    request.getWitnessHash().toByteArray(),
                    request.getAccountInputDataWithSalt().toByteArray(),
                    request.getOwnerPublicKey().toByteArray(),
                    request.getSignature().toByteArray());
            AccountAgeWitness witness = accountAgeWitnessService.verifyAccountAgeWitnessOwnership(proof);
            long dateBucket = WitnessReputationPrivacy.toDateBucket(witness.getDate());
            byte[] witnessNullifier = WitnessReputationPrivacy.deriveNullifier(proof);
            log.info("Verified account age ownership proof");
            var response = AccountAgeWitnessOwnershipResponse.newBuilder()
                    .setDateBucket(dateBucket)
                    .setWitnessNullifier(ByteString.copyFrom(witnessNullifier))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid account age witness ownership proof", e);
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("verifyAccountAgeWitnessOwnership failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal server error")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
