package bisq.core.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * The bridge between the single gRPC service implemented by
 * {@code GrpcCallService} and the {@code CoreApi}.
 * <p>
 * Its single entry point {@code call(params, isGatewayRequest)} is responsible for
 * parsing the full parameter string from the client, calling a {@code CoreApi} method
 * and returning a response in the form of a String, or, throwing a gRPC
 * {@code StatusRuntimeException}.
 * <p>
 * Responses will be wrapped in json if request is from a REST client,
 * if {@code isGatewayRequest == true}.
 * <p>
 * {@code StatusRuntimeException}s will contain an appropriate {@code io.grpc.Status} so
 * the grpc-gateway can translate gRPC status codes into HTTP status codes.
 */
@Slf4j
class GrpcCoreBridge {

    private final CoreApi coreApi;

    private final Gson gson = new GsonBuilder().create();

    // Used by a regex matcher to split command tokens by space, excepting those enclosed
    // in dbl quotes.
    private final Pattern paramsPattern = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    @Inject
    public GrpcCoreBridge(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    public String call(String params, boolean isGatewayRequest) {
        log.info("RPC request: '{}'", params);

        if (params.isEmpty()) {
            throw new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("no method specified"));
        }

        return "TODO";
    }

    private List<String> getParamTokens(String params) {
        List<String> paramTokens = new ArrayList<>();
        Matcher m = paramsPattern.matcher(params);
        while (m.find()) {
            String rawToken = m.group(1);
            // We only want to strip leading and trailing dbl quotes from the token,
            // and allow passwords to contain quotes.
            if (rawToken.length() >= 2 && rawToken.charAt(0) == '"' && rawToken.charAt(rawToken.length() - 1) == '"')
                rawToken = rawToken.substring(1, rawToken.length() - 1);
            paramTokens.add(rawToken);
        }
        return paramTokens;
    }

    private String formatResponse(String data, boolean isGatewayRequest) {
        return isGatewayRequest ? toJson(data) : data;
    }

    private String toJson(String data) {
        Map<String, String> map = new HashMap<>() {{
            put("data", data);
        }};
        return gson.toJson(map, Map.class);
    }
}
