package io.bisq.api.service;


import io.bisq.api.BisqProxyError;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Optional;

public final class ResourceHelper {

    private ResourceHelper() {
    }

    public static boolean handleBisqProxyError(Optional<BisqProxyError> optionalBisqProxyError, Response.Status status) {

        if (optionalBisqProxyError.isPresent()) {
            BisqProxyError bisqProxyError = optionalBisqProxyError.get();
            if (bisqProxyError.getOptionalThrowable().isPresent()) {
                throw new WebApplicationException(bisqProxyError.getErrorMessage(), bisqProxyError.getOptionalThrowable().get());
            } else {
                throw new WebApplicationException(bisqProxyError.getErrorMessage());
            }
        } else if (optionalBisqProxyError == null) {
            throw new WebApplicationException("Unknow error.");
        }

        return true;
    }

    public static boolean handleBisqProxyError(Optional<BisqProxyError> optionalBisqProxyError) {
        return handleBisqProxyError(optionalBisqProxyError, Response.Status.INTERNAL_SERVER_ERROR);
    }
}
