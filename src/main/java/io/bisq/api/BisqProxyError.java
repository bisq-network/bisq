/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.api;

import lombok.Data;
import lombok.Getter;

import java.util.Optional;

@Data
public class BisqProxyError {
    @Getter
    String errorMessage;
    @Getter
    Optional<Throwable> optionalThrowable;

    public BisqProxyError() {
        this.errorMessage = "";
        this.optionalThrowable = Optional.empty();
    }

    public BisqProxyError(String errorMessage, Throwable throwable) {
        this.errorMessage = errorMessage;
        this.optionalThrowable = Optional.ofNullable(throwable);
    }

    static Optional<BisqProxyError> getOptional(String errorMessage) {
        return Optional.of(new BisqProxyError(errorMessage, null));
    }

    static Optional<BisqProxyError> getOptional(String errorMessage, Throwable throwable) {
        return Optional.of(new BisqProxyError(errorMessage, throwable));
    }
}
