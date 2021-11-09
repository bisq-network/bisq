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
import bisq.core.payment.NationalBankAccount;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.util.Utilities;

import bisq.proto.grpc.BalancesInfo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import javax.annotation.Nullable;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestRateMeterInterceptorConfig.getTestRateMeterInterceptorConfig;
import static bisq.cli.table.builder.TableType.BSQ_BALANCE_TBL;
import static bisq.cli.table.builder.TableType.BTC_BALANCE_TBL;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.ApiTestCase;
import bisq.apitest.linux.BashCommand;
import bisq.cli.GrpcClient;
import bisq.cli.table.builder.TableBuilder;

public class MethodTest extends ApiTestCase {

    protected static final CoreProtoResolver CORE_PROTO_RESOLVER = new CoreProtoResolver();

    private static final Function<Enum<?>[], String> toNameList = (enums) ->
            stream(enums).map(Enum::name).collect(Collectors.joining(","));

    public static void startSupportingApps(File callRateMeteringConfigFile,
                                           boolean generateBtcBlock,
                                           boolean startSupportingAppsInDebugMode,
                                           Enum<?>... supportingApps) {
        try {
            setUpScaffold(new String[]{
                    "--supportingApps", toNameList.apply(supportingApps),
                    "--callRateMeteringConfigPath", callRateMeteringConfigFile.getAbsolutePath(),
                    "--enableBisqDebugging", startSupportingAppsInDebugMode ? "true" : "false"
            });
            doPostStartup(generateBtcBlock);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    public static void startSupportingApps(boolean generateBtcBlock,
                                           boolean startSupportingAppsInDebugMode,
                                           Enum<?>... supportingApps) {
        try {
            // Disable call rate metering where there is no callRateMeteringConfigFile.
            File callRateMeteringConfigFile = getTestRateMeterInterceptorConfig();
            setUpScaffold(new String[]{
                    "--supportingApps", toNameList.apply(supportingApps),
                    "--callRateMeteringConfigPath", callRateMeteringConfigFile.getAbsolutePath(),
                    "--enableBisqDebugging", startSupportingAppsInDebugMode ? "true" : "false"
            });
            doPostStartup(generateBtcBlock);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    protected static void doPostStartup(boolean generateBtcBlock) {
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


    protected bisq.core.payment.PaymentAccount createDummyBRLAccount(GrpcClient grpcClient,
                                                                     String holderName,
                                                                     String nationalAccountId,
                                                                     String holderTaxId) {
        String nationalBankAccountJsonString = "{\n" +
                "  \"_COMMENTS_\": [ \"Dummy Account\" ],\n" +
                "  \"paymentMethodId\": \"NATIONAL_BANK\",\n" +
                "  \"accountName\": \"Banco do Brasil\",\n" +
                "  \"country\": \"BR\",\n" +
                "  \"bankName\": \"Banco do Brasil\",\n" +
                "  \"branchId\": \"456789-10\",\n" +
                "  \"holderName\": \"" + holderName + "\",\n" +
                "  \"accountNr\": \"456789-87\",\n" +
                "  \"nationalAccountId\": \"" + nationalAccountId + "\",\n" +
                "  \"holderTaxId\": \"" + holderTaxId + "\"\n" +
                "}\n";
        NationalBankAccount nationalBankAccount =
                (NationalBankAccount) createPaymentAccount(grpcClient, nationalBankAccountJsonString);
        return nationalBankAccount;
    }

    protected final bisq.core.payment.PaymentAccount createPaymentAccount(GrpcClient grpcClient, String jsonString) {
        // Normally, we do asserts on the protos from the gRPC service, but in this
        // case we need a bisq.core.payment.PaymentAccount so it can be cast to its
        // sub-type.
        var paymentAccount = grpcClient.createPaymentAccount(jsonString);
        return bisq.core.payment.PaymentAccount.fromProto(paymentAccount, CORE_PROTO_RESOLVER);
    }

    public static String formatBalancesTbls(BalancesInfo allBalances) {
        StringBuilder balances = new StringBuilder(BTC).append("\n");
        balances.append(new TableBuilder(BTC_BALANCE_TBL, allBalances.getBtc()).build());
        balances.append("\n");
        balances.append(BSQ).append("\n");
        balances.append(new TableBuilder(BSQ_BALANCE_TBL, allBalances.getBsq()).build());
        return balances.toString();
    }

    protected static String encodeToHex(String s) {
        return Utilities.bytesAsHexString(s.getBytes(UTF_8));
    }

    protected void verifyNoLoggedNodeExceptions() {
        var loggedExceptions = getNodeExceptionMessages();
        if (loggedExceptions != null) {
            String err = format("Exception(s) found in daemon log(s):%n%s", loggedExceptions);
            fail(err);
        }
    }

    protected void printNodeExceptionMessages(Logger log) {
        var loggedExceptions = getNodeExceptionMessages();
        if (loggedExceptions != null)
            log.error("Exception(s) found in daemon log(s):\n{}", loggedExceptions);
    }

    @Nullable
    protected static String getNodeExceptionMessages() {
        var nodeLogsSpec = config.rootAppDataDir.getAbsolutePath() + "/bisq-BTC_REGTEST_*_dao/bisq.log";
        var grep = "grep Exception " + nodeLogsSpec;
        var bashCommand = new BashCommand(grep);
        try {
            bashCommand.run();
        } catch (IOException | InterruptedException ex) {
            fail("Bash command execution error: " + ex);
        }
        if (bashCommand.getError() == null)
            return bashCommand.getOutput();
        else
            throw new IllegalStateException("Bash command execution error: " + bashCommand.getError());
    }
}
