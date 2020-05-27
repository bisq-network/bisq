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

package bisq.core.grpc;

import bisq.proto.grpc.CallServiceGrpc;
import bisq.proto.grpc.Params;
import bisq.proto.grpc.Response;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import static bisq.core.grpc.PasswordAuthInterceptor.HTTP1_REQUEST_CTX_KEY;

public class GrpcCallService extends CallServiceGrpc.CallServiceImplBase {

    private final GrpcCoreBridge bridge;

    @Inject
    public GrpcCallService(GrpcCoreBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void call(Params req, StreamObserver<Response> responseObserver) {
        try {
            boolean isGatewayRequest = HTTP1_REQUEST_CTX_KEY.get(Context.current());
            String result = bridge.call(req.getParams(), isGatewayRequest);
            var reply = Response.newBuilder().setResult(result).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException ex) {
            responseObserver.onError(ex);
            throw ex;
        } catch (RuntimeException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
