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

import bisq.core.api.exception.AlreadyExistsException;
import bisq.core.api.exception.FailedPreconditionException;
import bisq.core.api.exception.NotAvailableException;
import bisq.core.api.exception.NotFoundException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;

import static io.grpc.Status.*;

/**
 * The singleton instance of this class handles any expected core api Throwable by
 * wrapping its message in a gRPC StatusRuntimeException and sending it to the client.
 * An unexpected Throwable's message will be replaced with an 'unexpected' error message.
 */
@Singleton
class GrpcExceptionHandler {

    private static final String CORE_API_EXCEPTION_PKG_NAME = NotFoundException.class.getPackage().getName();

    /**
     * Returns true if Throwable is a custom core api exception instance,
     * or one of the following native Java exception instances:
     * <p>
     * <pre>
     * IllegalArgumentException
     * IllegalStateException
     * UnsupportedOperationException
     * </pre>
     * </p>
     */
    private final Predicate<Throwable> isExpectedException = (t) ->
            t.getClass().getPackage().getName().equals(CORE_API_EXCEPTION_PKG_NAME)
                    || t instanceof IllegalArgumentException
                    || t instanceof IllegalStateException
                    || t instanceof UnsupportedOperationException;

    @SuppressWarnings("unused")
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
        // Check if a custom core.api.exception was thrown, so we can map it to a more
        // meaningful io.grpc.Status, something more useful to gRPC clients than UNKNOWN.
        if (t instanceof AlreadyExistsException)
            return ALREADY_EXISTS.withDescription(description);
        else if (t instanceof FailedPreconditionException)
            return FAILED_PRECONDITION.withDescription(description);
        else if (t instanceof NotFoundException)
            return NOT_FOUND.withDescription(description);
        else if (t instanceof NotAvailableException)
            return UNAVAILABLE.withDescription(description);

        // If the above checks did not return an io.grpc.Status.Code, we map
        // the native Java exception to an io.grpc.Status.
        if (t instanceof IllegalArgumentException)
            return INVALID_ARGUMENT.withDescription(description);
        else if (t instanceof IllegalStateException)
            return UNKNOWN.withDescription(description);
        else if (t instanceof UnsupportedOperationException)
            return UNIMPLEMENTED.withDescription(description);
        else
            return UNKNOWN.withDescription(description);
    }
}

