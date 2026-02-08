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

import bisq.common.util.Hex;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.protobuf.AccountTimestampGrpcServiceGrpc;
import bisq.bridge.protobuf.AccountTimestampRequest;
import bisq.bridge.protobuf.AccountTimestampResponse;

@Slf4j
public class AccountTimestampGrpcService extends AccountTimestampGrpcServiceGrpc.AccountTimestampGrpcServiceImplBase {
    private final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public AccountTimestampGrpcService(AccountAgeWitnessService accountAgeWitnessService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    @Override
    public void requestAccountTimestamp(AccountTimestampRequest request,
                                        StreamObserver<AccountTimestampResponse> responseObserver) {
        try {
            byte[] hash = request.getHash().toByteArray();
            Optional<Long> date = accountAgeWitnessService.getWitnessByHash(hash)
                    .map(AccountAgeWitness::getDate);
            if (date.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("No account age witness found for the provided hash " + Hex.encode(hash))
                        .asRuntimeException());
                return;
            }

            log.info("Account age for hash {}: {} ({})", Hex.encode(hash), date.get(), new Date(date.get()));
            var response = AccountTimestampResponse.newBuilder().setDate(date.get()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("requestAccountTimestampData failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal server error")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
