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

import bisq.proto.grpc.AvailabilityResultWithDescription;
import bisq.proto.grpc.TakeOfferReply;

import protobuf.AvailabilityResult;

import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;

import lombok.Getter;

import static bisq.proto.grpc.TradesGrpc.getTakeOfferMethod;
import static java.lang.String.format;
import static java.util.Arrays.stream;

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

            if (takeOfferWasCalled()) {
                handleTakeOfferError(errorMessage);
            } else {
                exceptionHandler.handleErrorMessage(log,
                        errorMessage,
                        responseObserver);
            }
        }
    }

    private void handleTakeOfferError(String errorMessage) {
        // If the errorMessage originated from a UI purposed TaskRunner, it should
        // contain an AvailabilityResult enum name.  If it does, derive the
        // AvailabilityResult enum from the errorMessage, wrap it in a new
        // AvailabilityResultWithDescription enum, then send the
        // AvailabilityResultWithDescription to the client instead of throwing
        // an exception.  The client should use the grpc reply object's
        // AvailabilityResultWithDescription field if reply.hasTrade = false, and the
        // client can decide to throw an exception with the client friendly error
        // description, or take some other action based on the AvailabilityResult enum.
        // (Some offer availability problems are not fatal, and retries are appropriate.)
        try {
            var failureReason = getAvailabilityResultWithDescription(errorMessage);
            var reply = TakeOfferReply.newBuilder()
                    .setFailureReason(failureReason)
                    .build();
            @SuppressWarnings("unchecked")
            var takeOfferResponseObserver = (StreamObserver<TakeOfferReply>) responseObserver;
            takeOfferResponseObserver.onNext(reply);
            takeOfferResponseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            log.error("", ex);
            exceptionHandler.handleErrorMessage(log,
                    errorMessage,
                    responseObserver);
        }
    }

    private AvailabilityResultWithDescription getAvailabilityResultWithDescription(String errorMessage) {
        AvailabilityResult proto = getAvailabilityResult(errorMessage);
        String description = getAvailabilityResultDescription(proto);
        return AvailabilityResultWithDescription.newBuilder()
                .setAvailabilityResult(proto)
                .setDescription(description)
                .build();
    }

    private AvailabilityResult getAvailabilityResult(String errorMessage) {
        return stream(AvailabilityResult.values())
                .filter((e) -> errorMessage.toUpperCase().contains(e.name()))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException(
                                format("Could not find an AvailabilityResult in error message:%n%s", errorMessage)));
    }

    private String getAvailabilityResultDescription(AvailabilityResult proto) {
        return bisq.core.offer.availability.AvailabilityResult.fromProto(proto).description();
    }

    private boolean takeOfferWasCalled() {
        return fullMethodName.equals(getTakeOfferMethod().getFullMethodName());
    }
}
