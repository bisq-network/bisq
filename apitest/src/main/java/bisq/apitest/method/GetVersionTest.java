package bisq.apitest.method;

import bisq.proto.grpc.GetVersionRequest;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.app.Version.VERSION;



import bisq.apitest.GrpcStubs;

@Slf4j
public class GetVersionTest extends MethodTest {

    public GetVersionTest(GrpcStubs grpcStubs) {
        super(grpcStubs);
    }

    public void setUp() {
        log.info("{} ...", this.getClass().getSimpleName());
    }

    public void run() {
        setUp();
        testGetVersion();
        report();
        tearDown();
    }

    public void testGetVersion() {
        if (isSkipped("testGetVersion"))
            return;

        var version = grpcStubs.versionService.getVersion(GetVersionRequest.newBuilder().build()).getVersion();
        if (version.equals(VERSION)) {
            log.info("{} testGetVersion passed", CHECK);
            countPassedTestCases++;
        } else {
            log.info("{} testGetVersion failed, expected {} actual {}", CROSS_MARK, VERSION, version);
            countFailedTestCases++;
        }
    }

    public void report() {
        log.info(reportString());
    }

    public void tearDown() {
    }
}
