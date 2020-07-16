package bisq.apitest;

import lombok.extern.slf4j.Slf4j;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import static java.lang.String.format;

@Slf4j
public class JUnitHelper {

    public static void runTests(Class... testClasses) {
        JUnitCore jUnitCore = new JUnitCore();
        jUnitCore.addListener(new RunListener() {
            public void testStarted(Description description) {
                log.info("{}", description);
            }

            public void testIgnored(Description description) {
                log.info("Ignored {}", description);
            }

            public void testFailure(Failure failure) {
                log.error("Failed {}", failure.getTrace());
            }
        });
        Result result = jUnitCore.run(testClasses);
        printTestResults(result);
    }

    public static void printTestResults(Result result) {
        log.info("Total tests: {},  Failed: {},  Ignored: {}",
                result.getRunCount(),
                result.getFailureCount(),
                result.getIgnoreCount());

        if (result.wasSuccessful()) {
            log.info("All {} tests passed", result.getRunCount());
        } else if (result.getFailureCount() > 0) {
            log.error("{} test(s) failed", result.getFailureCount());
            result.getFailures().iterator().forEachRemaining(f -> log.error(format("%s.%s()%n\t%s",
                    f.getDescription().getTestClass().getName(),
                    f.getDescription().getMethodName(),
                    f.getTrace())));
        }
    }
}
