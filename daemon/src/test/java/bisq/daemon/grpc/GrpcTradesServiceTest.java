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
import bisq.core.locale.Res;
import bisq.core.offer.Offer;

import bisq.common.handlers.ErrorMessageHandler;

import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TakeOfferRequest;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.bitcoinj.core.Coin;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrpcTradesServiceTest {
    // At a fee rate of 1 sat/vbyte the trade tx fee of an API taker is below 250 sat, and the core api refuses it
    // before it returns. The call gets one answer, with the reason, and the service does not throw.
    @Test
    void refusalBeforeTheCoreApiReturnsAnswersTheCallOnce() {
        Res.setup();
        String refusal = Res.get("takeOffer.failed.tradeTxFeeTooLow");
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("offerId");
        when(offer.getAmount()).thenReturn(Coin.valueOf(100_000));
        CoreApi coreApi = mock(CoreApi.class);
        when(coreApi.getOffer("offerId")).thenReturn(offer);
        doAnswer(invocation -> {
            invocation.<ErrorMessageHandler>getArgument(5).handleErrorMessage(refusal);
            return null;
        }).when(coreApi).takeOffer(anyString(), anyString(), anyString(), anyLong(), any(), any());
        @SuppressWarnings("unchecked")
        StreamObserver<TakeOfferReply> responseObserver = mock(StreamObserver.class);
        GrpcTradesService service = new GrpcTradesService(coreApi, new GrpcExceptionHandler());

        assertDoesNotThrow(() -> service.takeOffer(TakeOfferRequest.newBuilder().setOfferId("offerId").build(),
                responseObserver));

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(error.capture());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
        String description = ((StatusRuntimeException) error.getValue()).getStatus().getDescription();
        assertTrue(description.contains("below the range that makers accept"), description);
    }
}
