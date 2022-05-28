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

package bisq.daonodeOld.web.jdk.handler;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.arraycopy;



import com.sun.net.httpserver.HttpExchange;

/**
 * REST request URI parser to extract parameter names and values from an HttpExchange.
 *
 * Splits the HttpExchange's request URI into a String[] of pathElements identifying a
 * (REST) service name, a resource name, and any parameter/value pairs.
 *
 * This class is limited to URIs adhering a specific pattern:
 * pathElements[0] = service-name, e.g., daonode
 * pathElements[1] = resource-name, e.g., proofofburn
 * pathElements[2, 3...N, N+1] = param-name/value pairs.
 *
 * For example, request URL http://localhost:8080/daonode/proofofburn/blockheight/731270
 * identifies service-name "daonode", resource-name "proofofburn", and one parameter
 * "blockheight" with value 731270.
 */
@Getter
@Slf4j
public class RequestSpec {

    private final HttpExchange httpExchange;
    private final String method;
    private final URI requestURI;
    private final String[] pathElements;
    private final String serviceName;
    private final String resourceName;
    private final Map<String, String> parametersByName;

    public RequestSpec(HttpExchange httpExchange) {
        this.httpExchange = httpExchange;
        this.method = httpExchange.getRequestMethod();
        this.requestURI = httpExchange.getRequestURI();
        this.pathElements = toPathElements.apply(requestURI);
        this.serviceName = pathElements[0];
        this.resourceName = pathElements[1];
        try {
            this.parametersByName = getParametersByName();
        } catch (URISyntaxException ex) {
            // OK to throw ex in this constructor?
            log.error(ex.toString());
            throw new IllegalArgumentException(ex.toString());
        }
    }

    public boolean isRequestingResource(String resourceName) {
        return this.resourceName.equalsIgnoreCase(resourceName);
    }

    public String getStringParam(String paramName) {
        if (parametersByName.containsKey(paramName))
            return parametersByName.get(paramName);
        else
            throw new IllegalArgumentException(format("Parameter '%s' not found.", paramName));
    }

    public int getIntParam(String paramName) {
        if (parametersByName.containsKey(paramName)) {
            var value = parametersByName.get(paramName);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        format("Parameter '%s' value '%s' is not a number.",
                                paramName,
                                value));
            }
        } else {
            throw new IllegalArgumentException(format("Parameter '%s' not found.", paramName));
        }
    }

    private final Function<URI, String[]> toPathElements = (uri) -> {
        String[] raw = uri.getPath().split("/");
        String[] elements = new String[raw.length - 1];
        arraycopy(raw, 1, elements, 0, elements.length);
        return elements;
    };

    private Map<String, String> getParametersByName() throws URISyntaxException {
        Map<String, String> params = new HashMap<>();
        if (pathElements.length == 2)
            return params; // There are no parameter name/value pairs in url.

        // All pathElements beyond index 1 should be param-name/value pairs, and
        // a param-value must follow each param-name.
        Predicate<Integer> paramValueExists = (i) -> (i + 1) < pathElements.length;
        for (int i = 2; i < pathElements.length; i++) {
            String name = pathElements[i];
            if (paramValueExists.test(i))
                params.put(name, pathElements[++i]);
            else
                throw new URISyntaxException(requestURI.getPath(),
                        format("No value found for parameter with name '%s'.", name),
                        -1);
        }
        return params;
    }
}
