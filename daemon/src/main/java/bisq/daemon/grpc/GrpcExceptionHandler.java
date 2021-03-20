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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;

import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.UNKNOWN;

/**
 * The singleton instance of this class handles any expected core api Throwable by
 * wrapping its message in a gRPC StatusRuntimeException and sending it to the client.
 * An unexpected Throwable's message will be replaced with an 'unexpected' error message.
 */
@Singleton
class GrpcExceptionHandler {

    private final Predicate<Throwable> isExpectedException = (t) ->
            t instanceof IllegalStateException || t instanceof IllegalArgumentException;

    @Inject
    public GrpcExceptionHandler() {
    }

    public void handleException(Logger log,
                                Throwable t,
                                StreamObserver<?> responseObserver) {
        // Log the core api error (this is last chance to do that), wrap it in a new
        // gRPC StatusRuntimeException, then send it to the client in the gRPC response.
        log.error("", t);
        var grpcStatusRuntimeException = wrapException(t);
        responseObserver.onError(grpcStatusRuntimeException);
        throw grpcStatusRuntimeException;
    }

    public void handleExceptionAsWarning(Logger log,
                                         String calledMethod,
                                         Throwable t,
                                         StreamObserver<?> responseObserver) {
        // Just log a warning instead of an error with full stack trace.
        log.warn(calledMethod + " -> " + t.getMessage());
        var grpcStatusRuntimeException = wrapException(t);
        responseObserver.onError(grpcStatusRuntimeException);
        throw grpcStatusRuntimeException;
    }

    public void handleErrorMessage(Logger log,
                                   String errorMessage,
                                   StreamObserver<?> responseObserver) {
        // This is used to wrap Task errors from the ErrorMessageHandler
        // interface, an interface that is not allowed to throw exceptions.
        log.error(errorMessage);
        var grpcStatusRuntimeException = new StatusRuntimeException(
                UNKNOWN.withDescription(cliStyleErrorMessage.apply(errorMessage)));
        responseObserver.onError(grpcStatusRuntimeException);
        throw grpcStatusRuntimeException;
    }

    private StatusRuntimeException wrapException(Throwable t) {
        // We want to be careful about what kinds of exception messages we send to the
        // client.  Expected core exceptions should be wrapped in an IllegalStateException
        // or IllegalArgumentException, with a consistently styled and worded error
        // message.  But only a small number of the expected error types are currently
        // handled this way;  there is much work to do to handle the variety of errors
        // that can occur in the api.  In the meantime, we take care to not pass full,
        // unexpected error messages to the client.  If the exception type is unexpected,
        // we omit details from the gRPC exception sent to the client.
        if (isExpectedException.test(t)) {
            if (t.getCause() != null)
                return new StatusRuntimeException(mapGrpcErrorStatus(t.getCause(), t.getCause().getMessage()));
            else
                return new StatusRuntimeException(mapGrpcErrorStatus(t, t.getMessage()));
        } else {
            return new StatusRuntimeException(mapGrpcErrorStatus(t, "unexpected error on server"));
        }
    }

    private final Function<String, String> cliStyleErrorMessage = (e) -> {
        String[] line = e.split("\\r?\\n");
        int lastLine = line.length;
        return line[lastLine - 1].toLowerCase();
    };

    private Status mapGrpcErrorStatus(Throwable t, String description) {
        // We default to the UNKNOWN status, except were the mapping of a core api
        // exception to a gRPC Status is obvious.  If we ever use a gRPC reverse-proxy
        // to support RESTful clients, we will need to have more specific mappings
        // to support correct HTTP 1.1. status codes.
        //noinspection SwitchStatementWithTooFewBranches
        switch (t.getClass().getSimpleName()) {
            // We go ahead and use a switch statement instead of if, in anticipation
            // of more, specific exception mappings.
            case "IllegalArgumentException":
                return INVALID_ARGUMENT.withDescription(description);
            default:
                return UNKNOWN.withDescription(description);
        }
    }
}
