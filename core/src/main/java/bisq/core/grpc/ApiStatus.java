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

import io.grpc.Status;

/**
 * Maps meaningful CoreApi error messages to more general purpose gRPC error Status codes.
 * This keeps gRPC api out of CoreApi, and ensures the correct gRPC Status is sent to the
 * client.
 *
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/statuscodes.md">gRPC Status Codes</a>
 */
enum ApiStatus {

    INCORRECT_OLD_WALLET_PASSWORD(Status.INVALID_ARGUMENT, "incorrect old password"),
    INCORRECT_WALLET_PASSWORD(Status.INVALID_ARGUMENT, "incorrect password"),

    INTERNAL(Status.INTERNAL, "internal server error"),

    OK(Status.OK, null),

    UNKNOWN(Status.UNKNOWN, "unknown"),

    WALLET_ALREADY_LOCKED(Status.FAILED_PRECONDITION, "wallet is already locked"),

    WALLET_ENCRYPTER_NOT_AVAILABLE(Status.FAILED_PRECONDITION, "wallet encrypter is not available"),

    WALLET_IS_ENCRYPTED(Status.FAILED_PRECONDITION, "wallet is encrypted with a password"),
    WALLET_IS_ENCRYPTED_WITH_UNLOCK_INSTRUCTION(Status.FAILED_PRECONDITION,
            "wallet is encrypted with a password; unlock it with the 'unlock \"password\" timeout' command"),

    WALLET_NOT_ENCRYPTED(Status.FAILED_PRECONDITION, "wallet is not encrypted with a password"),
    WALLET_NOT_AVAILABLE(Status.UNAVAILABLE, "wallet is not available");


    private final Status grpcStatus;
    private final String description;

    ApiStatus(Status grpcStatus, String description) {
        this.grpcStatus = grpcStatus;
        this.description = description;
    }

    Status getGrpcStatus() {
        return this.grpcStatus;
    }

    String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return "ApiStatus{" +
                "grpcStatus=" + grpcStatus +
                ", description='" + description + '\'' +
                '}';
    }
}

