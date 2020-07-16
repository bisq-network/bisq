package bisq.apitest.scenario;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.JUnitHelper.runTests;

/**
 * Driver for running API scenario tests.
 *
 * This may not seem necessary, but test cases are contained in the apitest sub
 * project's main sources, not its test sources.  An IDE may not automatically configure
 * JUnit test launchers, and a gradle build will not automatically run the test cases.
 *
 * However, it is easy to manually configure an IDE launcher to run all, some or one
 * JUnit test, and new gradle tasks should be provided to run all, some, or one test.
 */
@Slf4j
public class ScenarioTestMain {

    public static void main(String[] args) {
        runTests(FundWalletScenarioTest.class);
    }
}
