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
import bisq.core.trade.handlers.TransactionResultHandler;

import bisq.proto.grpc.CreateOfferReply;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.GetOffersReply;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OffersGrpc;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.List;
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
        // The client cannot see bisq.core.Offer or its fromProto method.
        // We use the lighter weight OfferInfo proto wrapper instead, containing just
        // enough fields to view and create offers.
        List<OfferInfo> result = coreApi.getOffers(req.getDirection(), req.getCurrencyCode())
                .stream().map(offer -> new OfferInfo.OfferInfoBuilder()
                        .withId(offer.getId())
                        .withDirection(offer.getDirection().name())
                        .withPrice(offer.getPrice().getValue())
                        .withUseMarketBasedPrice(offer.isUseMarketBasedPrice())
                        .withMarketPriceMargin(offer.getMarketPriceMargin())
                        .withAmount(offer.getAmount().value)
                        .withMinAmount(offer.getMinAmount().value)
                        .withVolume(offer.getVolume().getValue())
                        .withMinVolume(offer.getMinVolume().getValue())
                        .withBuyerSecurityDeposit(offer.getBuyerSecurityDeposit().value)
                        .withPaymentAccountId("")  // only used when creating offer (?)
                        .withPaymentMethodId(offer.getPaymentMethod().getId())
                        .withPaymentMethodShortName(offer.getPaymentMethod().getShortName())
                        .withBaseCurrencyCode(offer.getOfferPayload().getBaseCurrencyCode())
                        .withCounterCurrencyCode(offer.getOfferPayload().getCounterCurrencyCode())
                        .withDate(offer.getDate().getTime())
                        .build())
                .collect(Collectors.toList());

        var reply = GetOffersReply.newBuilder()
                .addAllOffers(
                        result.stream()
                                .map(OfferInfo::toProtoMessage)
                                .collect(Collectors.toList()))
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void createOffer(CreateOfferRequest req,
                            StreamObserver<CreateOfferReply> responseObserver) {
        TransactionResultHandler resultHandler = transaction -> {
            CreateOfferReply reply = CreateOfferReply.newBuilder().setResult(true).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        };
        coreApi.createOffer(
                req.getCurrencyCode(),
                req.getDirection(),
                req.getPrice(),
                req.getUseMarketBasedPrice(),
                req.getMarketPriceMargin(),
                req.getAmount(),
                req.getMinAmount(),
                req.getBuyerSecurityDeposit(),
                req.getPaymentAccountId(),
                resultHandler);
    }
}
