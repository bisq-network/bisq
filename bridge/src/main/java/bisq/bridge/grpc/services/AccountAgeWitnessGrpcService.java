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

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.protobuf.AccountAgeWitnessDateRequest;
import bisq.bridge.protobuf.AccountAgeWitnessDateResponse;
import bisq.bridge.protobuf.AccountAgeWitnessGrpcServiceGrpc;

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
        try {
            String hashAsHex = request.getHashAsHex();
            Optional<Long> date = accountAgeWitnessService.getWitnessByHashAsHex(hashAsHex)
                    .map(AccountAgeWitness::getDate);
            if (date.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("No account age witness found for the provided hash " + hashAsHex)
                        .asRuntimeException());
                return;
            }

            log.info("Account age for hash {}: {} ({})", hashAsHex, date, new Date(date.get()));
            var response = AccountAgeWitnessDateResponse.newBuilder().setDate(date.get()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("requestAccountAgeWitnessData failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal server error")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
