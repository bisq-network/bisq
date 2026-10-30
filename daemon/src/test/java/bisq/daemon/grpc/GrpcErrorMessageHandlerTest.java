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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.daemon.grpc;

import bisq.proto.grpc.TakeOfferReply;

import protobuf.AvailabilityResult;

import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static bisq.proto.grpc.TradesGrpc.getTakeOfferMethod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GrpcErrorMessageHandlerTest {
    @Test
    @SuppressWarnings("unchecked")
    void theAvailabilityResultOfATakeOfferErrorIsFoundAlsoInATurkishDefaultLocale() {
        Locale defaultLocale = Locale.getDefault();
        try {
            // In Turkish, the i of price becomes a dotted capital I when it is upper-cased
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            StreamObserver<TakeOfferReply> responseObserver = mock(StreamObserver.class);
            GrpcExceptionHandler exceptionHandler = mock(GrpcExceptionHandler.class);
            GrpcErrorMessageHandler errorMessageHandler = new GrpcErrorMessageHandler(
                    getTakeOfferMethod().getFullMethodName(),
                    responseObserver,
                    exceptionHandler,
                    mock(Logger.class));

            errorMessageHandler.handleErrorMessage("offer availability check failed: price_out_of_tolerance");

            ArgumentCaptor<TakeOfferReply> reply = ArgumentCaptor.forClass(TakeOfferReply.class);
            verify(responseObserver).onNext(reply.capture());
            assertEquals(AvailabilityResult.PRICE_OUT_OF_TOLERANCE,
                    reply.getValue().getFailureReason().getAvailabilityResult());
            verifyNoInteractions(exceptionHandler);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
