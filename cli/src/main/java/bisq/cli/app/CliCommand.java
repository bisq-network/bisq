package bisq.cli.app;

import bisq.grpc.GetBalanceGrpc;
import bisq.grpc.GetBalanceRequest;
import bisq.grpc.GetVersionGrpc;
import bisq.grpc.GetVersionRequest;
import bisq.grpc.StopServerGrpc;
import bisq.grpc.StopServerRequest;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import static java.lang.System.currentTimeMillis;

@Slf4j
final class CliCommand {

    private final GetBalanceGrpc.GetBalanceBlockingStub getBalanceStub;
    private final GetVersionGrpc.GetVersionBlockingStub getVersionStub;
    private final StopServerGrpc.StopServerBlockingStub stopServerStub;

    private final DecimalFormat btcFormat = new DecimalFormat("###,##0.00000000");
    private final BigDecimal satoshiDivisor = new BigDecimal(100000000);
    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    final Function<Long, String> prettyBalance = (sats) -> btcFormat.format(BigDecimal.valueOf(sats).divide(satoshiDivisor));

    final Function<Long, String> responseTime = (t0) -> "(response time:  " + (currentTimeMillis() - t0) + " ms)";

    CliCommand(ManagedChannel channel) {
        getBalanceStub = GetBalanceGrpc.newBlockingStub(channel);
        getVersionStub = GetVersionGrpc.newBlockingStub(channel);
        stopServerStub = StopServerGrpc.newBlockingStub(channel);
    }

    String getVersion() {
        GetVersionRequest request = GetVersionRequest.newBuilder().build();
        try {
            return getVersionStub.getVersion(request).getVersion();
        } catch (StatusRuntimeException e) {
            return "RPC failed: " + e.getStatus();
        }
    }

    long getBalance() {
        GetBalanceRequest request = GetBalanceRequest.newBuilder().build();
        try {
            return getBalanceStub.getBalance(request).getBalance();
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
            return -1;
        }
    }

    void stopServer() {
        StopServerRequest request = StopServerRequest.newBuilder().build();
        try {
            stopServerStub.stopServer(request);
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
        }
    }
}
