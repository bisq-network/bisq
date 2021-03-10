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

import bisq.common.handlers.ErrorMessageHandler;

import bisq.proto.grpc.TakeOfferReply;

import protobuf.AvailabilityResult;

import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;

import lombok.Getter;

import static bisq.proto.grpc.TradesGrpc.getTakeOfferMethod;

/**
 * An implementation of bisq.common.handlers.ErrorMessageHandler that avoids
 * an exception loop with the UI's bisq.common.taskrunner framework.
 *
 * The legacy ErrorMessageHandler is for reporting error messages only to the UI, but
 * some core api tasks (takeoffer) require one.  This implementation works around
 * the problem of Task ErrorMessageHandlers not throwing exceptions to the gRPC client.
 *
 * Extra care is needed because exceptions thrown by an ErrorMessageHandler inside
 * a Task may be thrown back to the GrpcService object, and if a gRPC ErrorMessageHandler
 * responded by throwing another exception, the loop may only stop after the gRPC
 * stream is closed.
 *
 * A unique instance should be used for a single gRPC call.
 */
public class GrpcErrorMessageHandler implements ErrorMessageHandler {

    @Getter
    private boolean isErrorHandled = false;

    private final String fullMethodName;
    private final StreamObserver<?> responseObserver;
    private final GrpcExceptionHandler exceptionHandler;
    private final Logger log;

    public GrpcErrorMessageHandler(String fullMethodName,
                                   StreamObserver<?> responseObserver,
                                   GrpcExceptionHandler exceptionHandler,
                                   Logger log) {
        this.fullMethodName = fullMethodName;
        this.exceptionHandler = exceptionHandler;
        this.responseObserver = responseObserver;
        this.log = log;
    }

    @Override
    public void handleErrorMessage(String errorMessage) {
        // A task runner may call handleErrorMessage(String) more than once.
        // Throw only one exception if that happens, to avoid looping until the
        // grpc stream is closed
        if (!isErrorHandled) {
            this.isErrorHandled = true;
            log.error(errorMessage);

            if (isTakeOfferError()) {
                handleTakeOfferError();
            } else {
                exceptionHandler.handleErrorMessage(log,
                        errorMessage,
                        responseObserver);
            }
        }
    }

    private void handleTakeOfferError() {
        // Send the AvailabilityResult to the client instead of throwing an exception.
        // The client should look at the grpc reply object's AvailabilityResult
        // field if reply.hasTrade = false, and use it give the user a human readable msg.
        StreamObserver<TakeOfferReply> takeOfferResponseObserver = (StreamObserver<TakeOfferReply>) responseObserver;
        var availabilityResult = AvailabilityResult.valueOf("MAKER_DENIED_API_USER");
        var reply = TakeOfferReply.newBuilder()
                .setAvailabilityResult(availabilityResult)
                .build();
        takeOfferResponseObserver.onNext(reply);
        takeOfferResponseObserver.onCompleted();
    }

    private boolean isTakeOfferError() {
        return fullMethodName.equals(getTakeOfferMethod().getFullMethodName());
    }
}
