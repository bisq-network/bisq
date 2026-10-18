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

import bisq.core.locale.Res;

import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static bisq.proto.grpc.TradesGrpc.getTakeOfferMethod;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GrpcErrorMessageHandlerTest {
    // The handler reads an AvailabilityResult from any enum name in the text of a take offer error. The refusal of
    // the trade tx fee must not contain one, or an API client gets it as the reason, for example AVAILABLE.
    @ParameterizedTest
    @ValueSource(strings = {"takeOffer.failed.tradeTxFeeTooHigh", "takeOffer.failed.tradeTxFeeTooLow"})
    void tradeTxFeeRefusalIsNoAvailabilityResult(String key) {
        Res.setup();
        String errorMessage = Res.get(key);
        @SuppressWarnings("unchecked")
        StreamObserver<Object> responseObserver = mock(StreamObserver.class);
        GrpcExceptionHandler exceptionHandler = mock(GrpcExceptionHandler.class);
        Logger log = mock(Logger.class);

        new GrpcErrorMessageHandler(getTakeOfferMethod().getFullMethodName(), responseObserver, exceptionHandler, log)
                .handleErrorMessage(errorMessage);

        verify(responseObserver, never()).onNext(any());
        verify(exceptionHandler).handleErrorMessage(log, errorMessage, responseObserver);
    }
}
