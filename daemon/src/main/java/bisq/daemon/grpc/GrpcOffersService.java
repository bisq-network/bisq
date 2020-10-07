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

import bisq.proto.grpc.CreateOfferReply;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.GetOffersReply;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OffersGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class GrpcOffersService extends OffersGrpc.OffersImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcOffersService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void getOffers(GetOffersRequest req,
                          StreamObserver<GetOffersReply> responseObserver) {
        List<OfferInfo> result = coreApi.getOffers(req.getDirection(), req.getCurrencyCode())
                .stream().map(this::toOfferInfo)
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

    // The client cannot see bisq.core.Offer or its fromProto method.
    // We use the lighter weight OfferInfo proto wrapper instead, containing just
    // enough fields to view and create offers.
    private OfferInfo toOfferInfo(Offer offer) {
        return new OfferInfo.OfferInfoBuilder()
                .withId(offer.getId())
                .withDirection(offer.getDirection().name())
                .withPrice(Objects.requireNonNull(offer.getPrice()).getValue())
                .withUseMarketBasedPrice(offer.isUseMarketBasedPrice())
                .withMarketPriceMargin(offer.getMarketPriceMargin())
                .withAmount(offer.getAmount().value)
                .withMinAmount(offer.getMinAmount().value)
                .withVolume(Objects.requireNonNull(offer.getVolume()).getValue())
                .withMinVolume(Objects.requireNonNull(offer.getMinVolume()).getValue())
                .withBuyerSecurityDeposit(offer.getBuyerSecurityDeposit().value)
                .withPaymentAccountId(offer.getMakerPaymentAccountId())
                .withPaymentMethodId(offer.getPaymentMethod().getId())
                .withPaymentMethodShortName(offer.getPaymentMethod().getShortName())
                .withBaseCurrencyCode(offer.getOfferPayload().getBaseCurrencyCode())
                .withCounterCurrencyCode(offer.getOfferPayload().getCounterCurrencyCode())
                .withDate(offer.getDate().getTime())
                .build();
    }
}
