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

package bisq.daemon.grpc;

import bisq.core.api.CoreApi;
import bisq.core.api.model.OfferInfo;
import bisq.core.offer.Offer;

import bisq.proto.grpc.CancelOfferReply;
import bisq.proto.grpc.CancelOfferRequest;
import bisq.proto.grpc.CreateOfferReply;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.GetOfferReply;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetOffersReply;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OffersGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.api.model.OfferInfo.toOfferInfo;

@Slf4j
class GrpcOffersService extends OffersGrpc.OffersImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcOffersService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void getOffer(GetOfferRequest req,
                         StreamObserver<GetOfferReply> responseObserver) {
        try {
            Offer offer = coreApi.getOffer(req.getId());
            var reply = GetOfferReply.newBuilder()
                    .setOffer(toOfferInfo(offer).toProtoMessage())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException | IllegalArgumentException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void getOffers(GetOffersRequest req,
                          StreamObserver<GetOffersReply> responseObserver) {
        List<OfferInfo> result = coreApi.getOffers(req.getDirection(), req.getCurrencyCode())
                .stream().map(OfferInfo::toOfferInfo)
                .collect(Collectors.toList());
        var reply = GetOffersReply.newBuilder()
                .addAllOffers(result.stream()
                        .map(OfferInfo::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void createOffer(CreateOfferRequest req,
                            StreamObserver<CreateOfferReply> responseObserver) {
        try {
            coreApi.createAnPlaceOffer(
                    req.getCurrencyCode(),
                    req.getDirection(),
                    req.getPrice(),
                    req.getUseMarketBasedPrice(),
                    req.getMarketPriceMargin(),
                    req.getAmount(),
                    req.getMinAmount(),
                    req.getBuyerSecurityDeposit(),
                    req.getPaymentAccountId(),
                    offer -> {
                        // This result handling consumer's accept operation will return
                        // the new offer to the gRPC client after async placement is done.
                        OfferInfo offerInfo = toOfferInfo(offer);
                        CreateOfferReply reply = CreateOfferReply.newBuilder()
                                .setOffer(offerInfo.toProtoMessage())
                                .build();
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    });
        } catch (IllegalStateException | IllegalArgumentException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void cancelOffer(CancelOfferRequest req,
                            StreamObserver<CancelOfferReply> responseObserver) {
        try {
            coreApi.cancelOffer(req.getId());
            var reply = CancelOfferReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException | IllegalArgumentException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
