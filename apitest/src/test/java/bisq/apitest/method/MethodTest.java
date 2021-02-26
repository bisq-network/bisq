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

package bisq.apitest.method;

import bisq.core.api.model.PaymentAccountForm;
import bisq.core.payment.F2FAccount;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.util.Utilities;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.function.Function;
import java.util.stream.Collectors;

import static bisq.common.app.DevEnv.DEV_PRIVILEGE_PRIV_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.ApiTestCase;
import bisq.cli.GrpcClient;

public class MethodTest extends ApiTestCase {

    protected static final String ARBITRATOR = "arbitrator";
    protected static final String MEDIATOR = "mediator";
    protected static final String REFUND_AGENT = "refundagent";

    protected static final CoreProtoResolver CORE_PROTO_RESOLVER = new CoreProtoResolver();

    private static final Function<Enum<?>[], String> toNameList = (enums) ->
            stream(enums).map(Enum::name).collect(Collectors.joining(","));

    public static void startSupportingApps(File callRateMeteringConfigFile,
                                           boolean registerDisputeAgents,
                                           boolean generateBtcBlock,
                                           Enum<?>... supportingApps) {
        try {
            setUpScaffold(new String[]{
                    "--supportingApps", toNameList.apply(supportingApps),
                    "--callRateMeteringConfigPath", callRateMeteringConfigFile.getAbsolutePath(),
                    "--enableBisqDebugging", "false"
            });
            doPostStartup(registerDisputeAgents, generateBtcBlock);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    public static void startSupportingApps(boolean registerDisputeAgents,
                                           boolean generateBtcBlock,
                                           Enum<?>... supportingApps) {
        try {
            setUpScaffold(new String[]{
                    "--supportingApps", toNameList.apply(supportingApps),
                    "--enableBisqDebugging", "false"
            });
            doPostStartup(registerDisputeAgents, generateBtcBlock);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    protected static void doPostStartup(boolean registerDisputeAgents,
                                        boolean generateBtcBlock) {
        if (registerDisputeAgents) {
            registerDisputeAgents();
        }

        // Generate 1 regtest block for alice's and/or bob's wallet to
        // show 10 BTC balance, and allow time for daemons parse the new block.
        if (generateBtcBlock)
            genBtcBlocksThenWait(1, 1500);
    }

    protected final File getPaymentAccountForm(GrpcClient grpcClient, String paymentMethodId) {
        // We take seemingly unnecessary steps to get a File object, but the point is to
        // test the API, and we do not directly ask bisq.core.api.model.PaymentAccountForm
        // for an empty json form (file).
        String jsonString = grpcClient.getPaymentAcctFormAsJson(paymentMethodId);
        // Write the json string to a file here in the test case.
        File jsonFile = PaymentAccountForm.getTmpJsonFile(paymentMethodId);
        try (PrintWriter out = new PrintWriter(jsonFile, UTF_8)) {
            out.println(jsonString);
        } catch (IOException ex) {
            fail("Could not create tmp payment account form.", ex);
        }
        return jsonFile;
    }


    protected bisq.core.payment.PaymentAccount createDummyF2FAccount(GrpcClient grpcClient,
                                                                     String countryCode) {
        String f2fAccountJsonString = "{\n" +
                "  \"_COMMENTS_\": \"This is a dummy account.\",\n" +
                "  \"paymentMethodId\": \"F2F\",\n" +
                "  \"accountName\": \"Dummy " + countryCode.toUpperCase() + " F2F Account\",\n" +
                "  \"city\": \"Anytown\",\n" +
                "  \"contact\": \"Morse Code\",\n" +
                "  \"country\": \"" + countryCode.toUpperCase() + "\",\n" +
                "  \"extraInfo\": \"Salt Lick #213\"\n" +
                "}\n";
        F2FAccount f2FAccount = (F2FAccount) createPaymentAccount(grpcClient, f2fAccountJsonString);
        return f2FAccount;
    }

    protected final bisq.core.payment.PaymentAccount createPaymentAccount(GrpcClient grpcClient, String jsonString) {
        // Normally, we do asserts on the protos from the gRPC service, but in this
        // case we need a bisq.core.payment.PaymentAccount so it can be cast to its
        // sub type.
        var paymentAccount = grpcClient.createPaymentAccount(jsonString);
        return bisq.core.payment.PaymentAccount.fromProto(paymentAccount, CORE_PROTO_RESOLVER);
    }

    // Static conveniences for test methods and test case fixture setups.

    protected static void registerDisputeAgents() {
        arbClient.registerDisputeAgent(MEDIATOR, DEV_PRIVILEGE_PRIV_KEY);
        arbClient.registerDisputeAgent(REFUND_AGENT, DEV_PRIVILEGE_PRIV_KEY);
    }

    protected static String encodeToHex(String s) {
        return Utilities.bytesAsHexString(s.getBytes(UTF_8));
    }
}
