package bisq.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.util.LinkedList;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.opts.OptLabel.OPT_HOST;
import static bisq.cli.opts.OptLabel.OPT_PASSWORD;
import static bisq.cli.opts.OptLabel.OPT_PORT;
import static org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Operation.DELETE;
import static org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Operation.INSERT;



import bisq.cli.opts.ArgumentList;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

/**
 * Parent class for CLI smoke tests.  Useful for examining the format of the console
 * output, and checking for diffs while making changes to console output formatters.
 *
 * Tests that create offers or trades should not be run on mainnet.
 */
@Slf4j
public abstract class AbstractCliTest {

    static final String PASSWORD_OPT = "--password=xyz";      // Both daemons' password.
    static final String ALICE_PORT_OPT = "--port=" + 9998;    // Alice's daemon port.
    static final String BOB_PORT_OPT = "--port=" + 9999;      // Bob's daemon port.
    static final String[] BASE_ALICE_CLIENT_OPTS = new String[]{PASSWORD_OPT, ALICE_PORT_OPT};
    static final String[] BASE_BOB_CLIENT_OPTS = new String[]{PASSWORD_OPT, BOB_PORT_OPT};

    protected final GrpcClient aliceClient;
    protected final GrpcClient bobClient;

    public AbstractCliTest() {
        this.aliceClient = getGrpcClient(BASE_ALICE_CLIENT_OPTS);
        this.bobClient = getGrpcClient(BASE_BOB_CLIENT_OPTS);
    }

    protected GrpcClient getGrpcClient(String[] args) {
        var parser = new OptionParser();
        var hostOpt = parser.accepts(OPT_HOST, "rpc server hostname or ip")
                .withRequiredArg()
                .defaultsTo("localhost");
        var portOpt = parser.accepts(OPT_PORT, "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);
        var passwordOpt = parser.accepts(OPT_PASSWORD, "rpc server password")
                .withRequiredArg();

        OptionSet options = parser.parse(new ArgumentList(args).getCLIArguments());
        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        return new GrpcClient(host, port, password);
    }

    protected void checkDiffsIgnoreWhitespace(String oldOutput, String newOutput) {
        Predicate<DiffMatchPatch.Operation> isInsertOrDelete = (operation) ->
                operation.equals(INSERT) || operation.equals(DELETE);
        Predicate<String> isWhitespace = (text) -> text.trim().isEmpty();
        boolean hasNonWhitespaceDiffs = false;
        if (!oldOutput.equals(newOutput)) {
            DiffMatchPatch dmp = new DiffMatchPatch();
            LinkedList<DiffMatchPatch.Diff> diff = dmp.diffMain(oldOutput, newOutput, true);
            for (DiffMatchPatch.Diff d : diff) {
                if (isInsertOrDelete.test(d.operation) && !isWhitespace.test(d.text)) {
                    hasNonWhitespaceDiffs = true;
                    log.error(">>> DIFF {}", d);
                }
            }
        }

        if (hasNonWhitespaceDiffs)
            log.error("FAIL: There were diffs");
        else
            log.info("PASS:  No diffs");
    }

    protected void printOldTbl(String tbl) {
        log.info("OLD Console OUT:\n{}", tbl);
    }

    protected void printNewTbl(String tbl) {
        log.info("NEW Console OUT:\n{}", tbl);
    }
}
