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
import bisq.core.offer.OpenOffer;

import bisq.proto.grpc.CancelOfferReply;
import bisq.proto.grpc.CancelOfferRequest;
import bisq.proto.grpc.CreateBsqSwapOfferReply;
import bisq.proto.grpc.CreateBsqSwapOfferRequest;
import bisq.proto.grpc.CreateOfferReply;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.EditOfferReply;
import bisq.proto.grpc.EditOfferRequest;
import bisq.proto.grpc.GetBsqSwapOfferReply;
import bisq.proto.grpc.GetBsqSwapOffersReply;
import bisq.proto.grpc.GetBsqSwapOffersRequest;
import bisq.proto.grpc.GetMyBsqSwapOfferReply;
import bisq.proto.grpc.GetMyBsqSwapOffersReply;
import bisq.proto.grpc.GetMyOfferReply;
import bisq.proto.grpc.GetMyOfferRequest;
import bisq.proto.grpc.GetMyOffersReply;
import bisq.proto.grpc.GetMyOffersRequest;
import bisq.proto.grpc.GetOfferCategoryReply;
import bisq.proto.grpc.GetOfferCategoryRequest;
import bisq.proto.grpc.GetOfferReply;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetOffersReply;
import bisq.proto.grpc.GetOffersRequest;

import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.api.model.OfferInfo.toMyOfferInfo;
import static bisq.core.api.model.OfferInfo.toMyPendingOfferInfo;
import static bisq.core.api.model.OfferInfo.toOfferInfo;
import static bisq.daemon.grpc.interceptor.GrpcServiceRateMeteringConfig.getCustomRateMeteringInterceptor;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.ALTCOIN;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.BSQ_SWAP;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.FIAT;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.UNKNOWN;
import static bisq.proto.grpc.GetOfferCategoryReply.newBuilder;
import static bisq.proto.grpc.OffersGrpc.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.daemon.grpc.interceptor.CallRateMeteringInterceptor;
import bisq.daemon.grpc.interceptor.GrpcCallRateMeter;

@Slf4j
class GrpcOffersService extends OffersImplBase {

    private final CoreApi coreApi;
    private final GrpcExceptionHandler exceptionHandler;

    @Inject
    public GrpcOffersService(CoreApi coreApi, GrpcExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void getOfferCategory(GetOfferCategoryRequest req,
                                 StreamObserver<GetOfferCategoryReply> responseObserver) {
        try {
            OfferCategory category = getOfferCategory(req.getId(), req.getIsMyOffer());
            var reply = newBuilder()
                    .setOfferCategory(category)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getBsqSwapOffer(GetOfferRequest req,
                                StreamObserver<GetBsqSwapOfferReply> responseObserver) {
        try {
            Offer offer = coreApi.getOffer(req.getId());
            var reply = GetBsqSwapOfferReply.newBuilder()
                    .setBsqSwapOffer(toOfferInfo(offer).toProtoMessage())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getOffer(GetOfferRequest req,
                         StreamObserver<GetOfferReply> responseObserver) {
        try {
            String offerId = req.getId();
            Optional<OpenOffer> myOpenOffer = coreApi.findMyOpenOffer(offerId);
            OfferInfo offerInfo = myOpenOffer.isPresent()
                    ? toMyOfferInfo(myOpenOffer.get())
                    : toOfferInfo(coreApi.getOffer(offerId));
            var reply = GetOfferReply.newBuilder()
                    .setOffer(offerInfo.toProtoMessage())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getMyBsqSwapOffer(GetMyOfferRequest req,
                                  StreamObserver<GetMyBsqSwapOfferReply> responseObserver) {
        try {
            Offer offer = coreApi.getMyBsqSwapOffer(req.getId());
            var reply = GetMyBsqSwapOfferReply.newBuilder()
                    .setBsqSwapOffer(toOfferInfo(offer /* TODO support triggerPrice */).toProtoMessage())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Deprecated // Since 27-Dec-2021.
    // Endpoint to be removed from future version.  Use getOffer service method instead.
    @Override
    public void getMyOffer(GetMyOfferRequest req,
                           StreamObserver<GetMyOfferReply> responseObserver) {
        try {
            OpenOffer openOffer = coreApi.getMyOffer(req.getId());
            var reply = GetMyOfferReply.newBuilder()
                    .setOffer(toMyOfferInfo(openOffer).toProtoMessage())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getBsqSwapOffers(GetBsqSwapOffersRequest req,
                                 StreamObserver<GetBsqSwapOffersReply> responseObserver) {
        try {
            List<OfferInfo> result = coreApi.getBsqSwapOffers(req.getDirection())
                    .stream().map(OfferInfo::toOfferInfo)
                    .collect(Collectors.toList());
            var reply = GetBsqSwapOffersReply.newBuilder()
                    .addAllBsqSwapOffers(result.stream()
                            .map(OfferInfo::toProtoMessage)
                            .collect(Collectors.toList()))
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getOffers(GetOffersRequest req,
                          StreamObserver<GetOffersReply> responseObserver) {
        try {
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
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getMyBsqSwapOffers(GetBsqSwapOffersRequest req,
                                   StreamObserver<GetMyBsqSwapOffersReply> responseObserver) {
        try {
            List<OfferInfo> result = coreApi.getMyBsqSwapOffers(req.getDirection())
                    .stream().map(OfferInfo::toOfferInfo)
                    .collect(Collectors.toList());
            var reply = GetMyBsqSwapOffersReply.newBuilder()
                    .addAllBsqSwapOffers(result.stream()
                            .map(OfferInfo::toProtoMessage)
                            .collect(Collectors.toList()))
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getMyOffers(GetMyOffersRequest req,
                            StreamObserver<GetMyOffersReply> responseObserver) {
        try {
            List<OfferInfo> result = coreApi.getMyOffers(req.getDirection(), req.getCurrencyCode())
                    .stream()
                    .map(OfferInfo::toMyOfferInfo)
                    .collect(Collectors.toList());
            var reply = GetMyOffersReply.newBuilder()
                    .addAllOffers(result.stream()
                            .map(OfferInfo::toProtoMessage)
                            .collect(Collectors.toList()))
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void createBsqSwapOffer(CreateBsqSwapOfferRequest req,
                                   StreamObserver<CreateBsqSwapOfferReply> responseObserver) {
        try {
            coreApi.createAndPlaceBsqSwapOffer(
                    req.getDirection(),
                    req.getAmount(),
                    req.getMinAmount(),
                    req.getPrice(),
                    offer -> {
                        OfferInfo offerInfo = toMyPendingOfferInfo(offer);
                        CreateBsqSwapOfferReply reply = CreateBsqSwapOfferReply.newBuilder()
                                .setBsqSwapOffer(offerInfo.toProtoMessage())
                                .build();
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    });
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void createOffer(CreateOfferRequest req,
                            StreamObserver<CreateOfferReply> responseObserver) {
        try {
            coreApi.createAndPlaceOffer(
                    req.getCurrencyCode(),
                    req.getDirection(),
                    req.getPrice(),
                    req.getUseMarketBasedPrice(),
                    req.getMarketPriceMargin(),
                    req.getAmount(),
                    req.getMinAmount(),
                    req.getBuyerSecurityDeposit(),
                    req.getTriggerPrice(),
                    req.getPaymentAccountId(),
                    req.getMakerFeeCurrencyCode(),
                    offer -> {
                        // This result handling consumer's accept operation will return
                        // the new offer to the gRPC client after async placement is done.
                        OfferInfo offerInfo = toMyPendingOfferInfo(offer);
                        CreateOfferReply reply = CreateOfferReply.newBuilder()
                                .setOffer(offerInfo.toProtoMessage())
                                .build();
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    });
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void editOffer(EditOfferRequest req,
                          StreamObserver<EditOfferReply> responseObserver) {
        try {
            coreApi.editOffer(req.getId(),
                    req.getPrice(),
                    req.getUseMarketBasedPrice(),
                    req.getMarketPriceMargin(),
                    req.getTriggerPrice(),
                    req.getEnable(),
                    req.getEditType());
            var reply = EditOfferReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
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
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    final ServerInterceptor[] interceptors() {
        Optional<ServerInterceptor> rateMeteringInterceptor = rateMeteringInterceptor();
        return rateMeteringInterceptor.map(serverInterceptor ->
                new ServerInterceptor[]{serverInterceptor}).orElseGet(() -> new ServerInterceptor[0]);
    }

    final Optional<ServerInterceptor> rateMeteringInterceptor() {
        return getCustomRateMeteringInterceptor(coreApi.getConfig().appDataDir, this.getClass())
                .or(() -> Optional.of(CallRateMeteringInterceptor.valueOf(
                        new HashMap<>() {{
                            put(getGetOfferCategoryMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getGetOfferMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getGetMyOfferMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getGetOffersMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getGetMyOffersMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getCreateOfferMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                            put(getEditOfferMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                            put(getCancelOfferMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                        }}
                )));
    }

    private OfferCategory getOfferCategory(String offerId, boolean isMyOffer) {
        if (coreApi.isAltcoinOffer(offerId, isMyOffer))
            return ALTCOIN;
        else if (coreApi.isFiatOffer(offerId, isMyOffer))
            return FIAT;
        else if (coreApi.isBsqSwapOffer(offerId, isMyOffer))
            return BSQ_SWAP;
        else
            return UNKNOWN;
    }
}
