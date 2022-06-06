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

package bisq.daoNode.error;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;



import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Slf4j
@Provider
public class StatusException extends RuntimeException {

    @Getter
    @Setter
    protected Response.Status httpStatus;

    public StatusException() {
    }

    public StatusException(Response.Status httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public static class StatusExceptionMapper implements ExceptionMapper<StatusException> {
        @Override
        public Response toResponse(StatusException exception) {
            log.error("", exception);
            return Response.status(exception.getHttpStatus())
                    .entity(new ErrorMessage(exception.getMessage()))
                    .build();
        }
    }
}
