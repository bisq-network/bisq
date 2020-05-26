package bisq.core.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;

import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

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

        var paramTokens = getParamTokens(params);
        var methodName = paramTokens.get(0);
        final Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            throw new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription(
                            format("'%s' is not a supported method", methodName)));
        }

        try {
            // If the wallet or balance is unavailable, you can't do anything.
            coreApi.getBalance();
        } catch (IllegalStateException ex) {
            String reason = ex.getMessage();
            if (reason.equals("wallet is not yet available") || reason.equals("balance is not yet available")) {
                throw new StatusRuntimeException(
                        Status.UNAVAILABLE.withDescription("server not available"));
            }
        }

        // Call the CoreApi method.  Catch and wrap exceptions in a gRPC
        // StatusRuntimeException (so REST clients receive a proper HTTP status).
        // If the params came from the HTTP 1.1 proxy, wrap the response in json.
        try {
            switch (method) {
                case help: {
                    String cmd = (paramTokens.size() > 1) ? paramTokens.get(1) : null;
                    if (cmd != null) {
                        try {
                            return formatResponse(coreApi.getHelp(Method.valueOf(cmd)), isGatewayRequest);
                        } catch (IllegalArgumentException ex) {
                            throw new StatusRuntimeException(
                                    Status.INVALID_ARGUMENT.withDescription(
                                            format("'%s\n\n%s' is not a supported method", cmd, coreApi.getHelp(null))));
                        }
                    } else {
                        return formatResponse(coreApi.getHelp(null), isGatewayRequest);
                    }
                }
                case getversion: {
                    return formatResponse(coreApi.getVersion(), isGatewayRequest);
                }
                case getbalance: {
                    try {
                        var satoshiBalance = coreApi.getBalance();
                        var satoshiDivisor = new BigDecimal(100000000);
                        var btcFormat = new DecimalFormat("###,##0.00000000");
                        @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
                        var btcBalance = btcFormat.format(BigDecimal.valueOf(satoshiBalance).divide(satoshiDivisor));
                        return formatResponse(btcBalance, isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        throw new StatusRuntimeException(
                                Status.UNKNOWN.withDescription(ex.getMessage()));
                    }
                }
                case lockwallet: {
                    try {
                        coreApi.lockWallet();
                        return formatResponse("wallet locked", isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        throw new StatusRuntimeException(
                                Status.UNKNOWN.withDescription(ex.getMessage()));
                    }
                }
                case unlockwallet: {
                    if (paramTokens.size() < 2)
                        throw new StatusRuntimeException(
                                Status.INVALID_ARGUMENT.withDescription("no password specified"));

                    var password = paramTokens.get(1);

                    if (paramTokens.size() < 3)
                        throw new StatusRuntimeException(
                                Status.INVALID_ARGUMENT.withDescription("no unlock timeout specified"));

                    long timeout;
                    try {
                        timeout = Long.parseLong(paramTokens.get(2));
                    } catch (NumberFormatException ex) {
                        throw new StatusRuntimeException(
                                Status.INVALID_ARGUMENT
                                        .withDescription(format("'%s' is not a number", paramTokens.get(2))));
                    }

                    try {
                        coreApi.unlockWallet(password, timeout);
                        return formatResponse("wallet unlocked", isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        throw new StatusRuntimeException(
                                Status.UNKNOWN.withDescription(ex.getMessage()));
                    }
                }
                case setwalletpassword: {
                    if (paramTokens.size() < 2)
                        throw new StatusRuntimeException(
                                Status.INVALID_ARGUMENT.withDescription("no password specified"));

                    var password = paramTokens.get(1);
                    var newPassword = paramTokens.size() == 3 ? paramTokens.get(2).trim() : "";
                    try {
                        coreApi.setWalletPassword(password, newPassword);
                        return formatResponse("wallet encrypted"
                                        + (!newPassword.isEmpty() ? " with new password" : ""),
                                isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        throw new StatusRuntimeException(
                                Status.UNKNOWN.withDescription(ex.getMessage()));
                    }
                }
                case removewalletpassword: {
                    if (paramTokens.size() < 2)
                        throw new StatusRuntimeException(
                                Status.INVALID_ARGUMENT.withDescription("no password specified"));

                    var password = paramTokens.get(1);
                    try {
                        coreApi.removeWalletPassword(password);
                        return formatResponse("wallet decrypted", isGatewayRequest);
                    } catch (IllegalStateException ex) {
                        throw new StatusRuntimeException(
                                Status.UNKNOWN.withDescription(ex.getMessage()));
                    }
                }
                default: {
                    throw new StatusRuntimeException(
                            Status.INVALID_ARGUMENT.withDescription(
                                    format("unhandled method '%s'", method)));
                }
            }
        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            throw new RuntimeException(message, ex);
        }
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
