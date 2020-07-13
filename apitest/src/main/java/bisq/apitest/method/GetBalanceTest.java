package bisq.apitest.method;

import bisq.proto.grpc.GetBalanceRequest;

import lombok.extern.slf4j.Slf4j;



import bisq.apitest.GrpcStubs;

@Slf4j
public class GetBalanceTest extends MethodTest {

    public GetBalanceTest(GrpcStubs grpcStubs) {
        super(grpcStubs);
    }

    public void setUp() {
        log.info("{} ...", this.getClass().getSimpleName());
    }

    public void run() {
        setUp();
        testGetBalance();
        report();
        tearDown();
    }

    public void testGetBalance() {
        if (isSkipped("testGetBalance"))
            return;

        var balance = grpcStubs.walletsService.getBalance(GetBalanceRequest.newBuilder().build()).getBalance();
        if (balance == 1000000000) {
            log.info("{} testGetBalance passed", CHECK);
            countPassedTestCases++;
        } else {
            log.info("{} testGetBalance failed, expected {} actual {}", CROSS_MARK, 1000000000, balance);
            countFailedTestCases++;
        }
    }

    public void report() {
        log.info(reportString());
    }

    public void tearDown() {
    }
}
