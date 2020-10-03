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

package bisq.cli;

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetAddressBalanceRequest;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletRequest;

import io.grpc.StatusRuntimeException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.io.PrintStream;

import java.math.BigDecimal;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.cli.CurrencyFormat.toSatoshis;
import static bisq.cli.NegativeNumberOptions.hasNegativeNumberOptions;
import static bisq.cli.TableFormat.formatAddressBalanceTbl;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.cli.TableFormat.formatPaymentAcctTbl;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.math.BigDecimal.ZERO;
import static java.util.Collections.singletonList;

/**
 * A command-line client for the Bisq gRPC API.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
public class CliMain {

    private enum Method {
        createoffer,
        getoffers,
        createpaymentacct,
        getpaymentaccts,
        getversion,
        getbalance,
        getaddressbalance,
        getfundingaddresses,
        lockwallet,
        unlockwallet,
        removewalletpassword,
        setwalletpassword,
        registerdisputeagent
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            err.println("Error: " + t.getMessage());
            exit(1);
        }
    }

    public static void run(String[] args) {
        var parser = new OptionParser();

        var helpOpt = parser.accepts("help", "Print this help text")
                .forHelp();

        var hostOpt = parser.accepts("host", "rpc server hostname or IP")
                .withRequiredArg()
                .defaultsTo("localhost");

        var portOpt = parser.accepts("port", "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);

        var passwordOpt = parser.accepts("password", "rpc server password")
                .withRequiredArg();

        var negativeNumberOpts = hasNegativeNumberOptions(args)
                ? new NegativeNumberOptions()
                : null;

        // Cache any negative number params that will not be accepted by the parser.
        if (negativeNumberOpts != null)
            args = negativeNumberOpts.removeNegativeNumberOptions(args);

        // Parse the options after temporarily removing any negative number params we
        // do not want the parser recognizing as invalid option arguments, e.g., -0.05.
        OptionSet options = parser.parse(args);

        if (options.has(helpOpt)) {
            printHelp(parser, out);
            return;
        }

        @SuppressWarnings("unchecked")
        var nonOptionArgs = (List<String>) options.nonOptionArguments();
        if (nonOptionArgs.isEmpty()) {
            printHelp(parser, err);
            throw new IllegalArgumentException("no method specified");
        }

        // Restore any cached negative number params to the nonOptionArgs list.
        if (negativeNumberOpts != null)
            nonOptionArgs = negativeNumberOpts.restoreNegativeNumberOptions(nonOptionArgs);

        var methodName = nonOptionArgs.get(0);
        Method method;
        try {
            method = getMethodFromCmd(methodName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' is not a supported method", methodName));
        }

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        GrpcStubs grpcStubs = new GrpcStubs(host, port, password);
        var disputeAgentsService = grpcStubs.disputeAgentsService;
        var versionService = grpcStubs.versionService;
        var offersService = grpcStubs.offersService;
        var paymentAccountsService = grpcStubs.paymentAccountsService;
        var walletsService = grpcStubs.walletsService;

        try {
            switch (method) {
                case getversion: {
                    var request = GetVersionRequest.newBuilder().build();
                    var version = versionService.getVersion(request).getVersion();
                    out.println(version);
                    return;
                }
                case getbalance: {
                    var request = GetBalanceRequest.newBuilder().build();
                    var reply = walletsService.getBalance(request);
                    var btcBalance = formatSatoshis(reply.getBalance());
                    out.println(btcBalance);
                    return;
                }
                case getaddressbalance: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no address specified");

                    var request = GetAddressBalanceRequest.newBuilder()
                            .setAddress(nonOptionArgs.get(1)).build();
                    var reply = walletsService.getAddressBalance(request);
                    out.println(formatAddressBalanceTbl(singletonList(reply.getAddressBalanceInfo())));
                    return;
                }
                case getfundingaddresses: {
                    var request = GetFundingAddressesRequest.newBuilder().build();
                    var reply = walletsService.getFundingAddresses(request);
                    out.println(formatAddressBalanceTbl(reply.getAddressBalanceInfoList()));
                    return;
                }
                case createoffer: {
                    if (nonOptionArgs.size() < 9)
                        throw new IllegalArgumentException("incorrect parameter count,"
                                + " expecting payment acct id, buy | sell, currency code, amount, min amount,"
                                + " use-market-based-price, fixed-price | mkt-price-margin, security-deposit");

                    var paymentAcctId = nonOptionArgs.get(1);
                    var direction = nonOptionArgs.get(2);
                    var currencyCode = nonOptionArgs.get(3);
                    var amount = toSatoshis(nonOptionArgs.get(4));
                    var minAmount = toSatoshis(nonOptionArgs.get(5));
                    var useMarketBasedPrice = Boolean.parseBoolean(nonOptionArgs.get(6));
                    var fixedPrice = ZERO.toString();
                    var marketPriceMargin = ZERO;
                    if (useMarketBasedPrice)
                        marketPriceMargin = new BigDecimal(nonOptionArgs.get(7));
                    else
                        fixedPrice = nonOptionArgs.get(7);
                    var securityDeposit = new BigDecimal(nonOptionArgs.get(8));

                    var request = CreateOfferRequest.newBuilder()
                            .setDirection(direction)
                            .setCurrencyCode(currencyCode)
                            .setAmount(amount)
                            .setMinAmount(minAmount)
                            .setUseMarketBasedPrice(useMarketBasedPrice)
                            .setPrice(fixedPrice)
                            .setMarketPriceMargin(marketPriceMargin.doubleValue())
                            .setBuyerSecurityDeposit(securityDeposit.doubleValue())
                            .setPaymentAccountId(paymentAcctId)
                            .build();
                    var reply = offersService.createOffer(request);
                    out.println(formatOfferTable(singletonList(reply.getOffer()), currencyCode));
                    return;
                }
                case getoffers: {
                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("incorrect parameter count,"
                                + " expecting direction (buy|sell), currency code");

                    var direction = nonOptionArgs.get(1);
                    var currencyCode = nonOptionArgs.get(2);

                    var request = GetOffersRequest.newBuilder()
                            .setDirection(direction)
                            .setCurrencyCode(currencyCode)
                            .build();
                    var reply = offersService.getOffers(request);
                    out.println(formatOfferTable(reply.getOffersList(), currencyCode));
                    return;
                }
                case createpaymentacct: {
                    if (nonOptionArgs.size() < 5)
                        throw new IllegalArgumentException(
                                "incorrect parameter count, expecting payment method id,"
                                        + " account name, account number, currency code");

                    var paymentMethodId = nonOptionArgs.get(1);
                    var accountName = nonOptionArgs.get(2);
                    var accountNumber = nonOptionArgs.get(3);
                    var currencyCode = nonOptionArgs.get(4);

                    var request = CreatePaymentAccountRequest.newBuilder()
                            .setPaymentMethodId(paymentMethodId)
                            .setAccountName(accountName)
                            .setAccountNumber(accountNumber)
                            .setCurrencyCode(currencyCode).build();
                    paymentAccountsService.createPaymentAccount(request);
                    out.printf("payment account %s saved", accountName);
                    return;
                }
                case getpaymentaccts: {
                    var request = GetPaymentAccountsRequest.newBuilder().build();
                    var reply = paymentAccountsService.getPaymentAccounts(request);
                    out.println(formatPaymentAcctTbl(reply.getPaymentAccountsList()));
                    return;
                }
                case lockwallet: {
                    var request = LockWalletRequest.newBuilder().build();
                    walletsService.lockWallet(request);
                    out.println("wallet locked");
                    return;
                }
                case unlockwallet: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("no unlock timeout specified");

                    long timeout;
                    try {
                        timeout = Long.parseLong(nonOptionArgs.get(2));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(format("'%s' is not a number", nonOptionArgs.get(2)));
                    }
                    var request = UnlockWalletRequest.newBuilder()
                            .setPassword(nonOptionArgs.get(1))
                            .setTimeout(timeout).build();
                    walletsService.unlockWallet(request);
                    out.println("wallet unlocked");
                    return;
                }
                case removewalletpassword: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    var request = RemoveWalletPasswordRequest.newBuilder()
                            .setPassword(nonOptionArgs.get(1)).build();
                    walletsService.removeWalletPassword(request);
                    out.println("wallet decrypted");
                    return;
                }
                case setwalletpassword: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    var requestBuilder = SetWalletPasswordRequest.newBuilder()
                            .setPassword(nonOptionArgs.get(1));
                    var hasNewPassword = nonOptionArgs.size() == 3;
                    if (hasNewPassword)
                        requestBuilder.setNewPassword(nonOptionArgs.get(2));
                    walletsService.setWalletPassword(requestBuilder.build());
                    out.println("wallet encrypted" + (hasNewPassword ? " with new password" : ""));
                    return;
                }
                case registerdisputeagent: {
                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException(
                                "incorrect parameter count, expecting dispute agent type, registration key");

                    var disputeAgentType = nonOptionArgs.get(1);
                    var registrationKey = nonOptionArgs.get(2);
                    var requestBuilder = RegisterDisputeAgentRequest.newBuilder()
                            .setDisputeAgentType(disputeAgentType).setRegistrationKey(registrationKey);
                    disputeAgentsService.registerDisputeAgent(requestBuilder.build());
                    out.println(disputeAgentType + " registered");
                    return;
                }
                default: {
                    throw new RuntimeException(format("unhandled method '%s'", method));
                }
            }
        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            throw new RuntimeException(message, ex);
        }
    }

    private static Method getMethodFromCmd(String methodName) {
        // TODO if we use const type for enum we need add some mapping.  Even if we don't
        //  change now it is handy to have flexibility in case we change internal code
        //  and don't want to break user commands.
        return Method.valueOf(methodName.toLowerCase());
    }

    private static void printHelp(OptionParser parser, PrintStream stream) {
        try {
            stream.println("Bisq RPC Client");
            stream.println();
            stream.println("Usage: bisq-cli [options] <method> [params]");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
            String rowFormat = "%-22s%-50s%s%n";
            stream.format(rowFormat, "Method", "Params", "Description");
            stream.format(rowFormat, "------", "------", "------------");
            stream.format(rowFormat, "getversion", "", "Get server version");
            stream.format(rowFormat, "getbalance", "", "Get server wallet balance");
            stream.format(rowFormat, "getaddressbalance", "address", "Get server wallet address balance");
            stream.format(rowFormat, "getfundingaddresses", "", "Get BTC funding addresses");
            stream.format(rowFormat, "createoffer", "payment acct id, buy | sell, currency code, \\", "Create and place an offer");
            stream.format(rowFormat, "", "amount (btc), min amount, use mkt based price, \\", "");
            stream.format(rowFormat, "", "fixed price (btc) | mkt price margin (%), \\", "");
            stream.format(rowFormat, "", "security deposit (%)", "");
            stream.format(rowFormat, "getoffers", "buy | sell, currency code", "Get current offers");
            stream.format(rowFormat, "createpaymentacct", "account name, account number, currency code", "Create PerfectMoney dummy account");
            stream.format(rowFormat, "getpaymentaccts", "", "Get user payment accounts");
            stream.format(rowFormat, "lockwallet", "", "Remove wallet password from memory, locking the wallet");
            stream.format(rowFormat, "unlockwallet", "password timeout",
                    "Store wallet password in memory for timeout seconds");
            stream.format(rowFormat, "setwalletpassword", "password [newpassword]",
                    "Encrypt wallet with password, or set new password on encrypted wallet");
            stream.println();
        } catch (IOException ex) {
            ex.printStackTrace(stream);
        }
    }
}
