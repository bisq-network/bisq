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

import bisq.proto.grpc.CancelOfferRequest;
import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetAddressBalanceRequest;
import bisq.proto.grpc.GetBalancesRequest;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.GetPaymentAccountFormRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetPaymentMethodsRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.GetTxFeeRateRequest;
import bisq.proto.grpc.GetUnusedBsqAddressRequest;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.KeepFundsRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SendBsqRequest;
import bisq.proto.grpc.SendBtcRequest;
import bisq.proto.grpc.SetTxFeeRatePreferenceRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TxInfo;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.UnsetTxFeeRatePreferenceRequest;
import bisq.proto.grpc.WithdrawFundsRequest;

import protobuf.PaymentAccount;

import io.grpc.StatusRuntimeException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import java.math.BigDecimal;

import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.CurrencyFormat.formatTxFeeRateInfo;
import static bisq.cli.CurrencyFormat.toSatoshis;
import static bisq.cli.NegativeNumberOptions.hasNegativeNumberOptions;
import static bisq.cli.TableFormat.*;
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
        canceloffer,
        getoffer,
        getoffers,
        takeoffer,
        gettrade,
        confirmpaymentstarted,
        confirmpaymentreceived,
        keepfunds,
        withdrawfunds,
        getpaymentmethods,
        getpaymentacctform,
        createpaymentacct,
        getpaymentaccts,
        getversion,
        getbalance,
        getaddressbalance,
        getfundingaddresses,
        getunusedbsqaddress,
        sendbsq,
        sendbtc,
        gettxfeerate,
        settxfeerate,
        unsettxfeerate,
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
        var offersService = grpcStubs.offersService;
        var paymentAccountsService = grpcStubs.paymentAccountsService;
        var tradesService = grpcStubs.tradesService;
        var versionService = grpcStubs.versionService;
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
                    var currencyCode = nonOptionArgs.size() == 2
                            ? nonOptionArgs.get(1)
                            : "";
                    var request = GetBalancesRequest.newBuilder()
                            .setCurrencyCode(currencyCode)
                            .build();
                    var reply = walletsService.getBalances(request);
                    switch (currencyCode.toUpperCase()) {
                        case "BSQ":
                            out.println(formatBsqBalanceInfoTbl(reply.getBalances().getBsq()));
                            break;
                        case "BTC":
                            out.println(formatBtcBalanceInfoTbl(reply.getBalances().getBtc()));
                            break;
                        case "":
                        default:
                            out.println(formatBalancesTbls(reply.getBalances()));
                            break;
                    }
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
                case getunusedbsqaddress: {
                    var request = GetUnusedBsqAddressRequest.newBuilder().build();
                    var reply = walletsService.getUnusedBsqAddress(request);
                    out.println(reply.getAddress());
                    return;
                }
                case sendbsq: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no bsq address specified");

                    var address = nonOptionArgs.get(1);

                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("no bsq amount specified");

                    var amount = nonOptionArgs.get(2);
                    verifyStringIsValidDecimal(amount);

                    var txFeeRate = nonOptionArgs.size() == 4 ? nonOptionArgs.get(3) : "";
                    if (!txFeeRate.isEmpty())
                        verifyStringIsValidLong(txFeeRate);

                    var request = SendBsqRequest.newBuilder()
                            .setAddress(address)
                            .setAmount(amount)
                            .setTxFeeRate(txFeeRate)
                            .build();
                    var reply = walletsService.sendBsq(request);
                    TxInfo txInfo = reply.getTxInfo();
                    out.printf("%s bsq sent to %s in tx %s%n", amount, address, txInfo.getId());
                    return;
                }
                case sendbtc: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no btc address specified");

                    var address = nonOptionArgs.get(1);

                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("no btc amount specified");

                    var amount = nonOptionArgs.get(2);
                    verifyStringIsValidDecimal(amount);

                    var txFeeRate = nonOptionArgs.size() == 4 ? nonOptionArgs.get(3) : "";
                    if (!txFeeRate.isEmpty())
                        verifyStringIsValidLong(txFeeRate);

                    var request = SendBtcRequest.newBuilder()
                            .setAddress(address)
                            .setAmount(amount)
                            .setTxFeeRate(txFeeRate)
                            .build();
                    var reply = walletsService.sendBtc(request);
                    TxInfo txInfo = reply.getTxInfo();
                    out.printf("%s btc sent to %s in tx %s%n", amount, address, txInfo.getId());
                    return;
                }
                case gettxfeerate: {
                    var request = GetTxFeeRateRequest.newBuilder().build();
                    var reply = walletsService.getTxFeeRate(request);
                    out.println(formatTxFeeRateInfo(reply.getTxFeeRateInfo()));
                    return;
                }
                case settxfeerate: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no tx fee rate specified");

                    var txFeeRate = toLong(nonOptionArgs.get(2));
                    var request = SetTxFeeRatePreferenceRequest.newBuilder()
                            .setTxFeeRatePreference(txFeeRate)
                            .build();
                    var reply = walletsService.setTxFeeRatePreference(request);
                    out.println(formatTxFeeRateInfo(reply.getTxFeeRateInfo()));
                    return;
                }
                case unsettxfeerate: {
                    var request = UnsetTxFeeRatePreferenceRequest.newBuilder().build();
                    var reply = walletsService.unsetTxFeeRatePreference(request);
                    out.println(formatTxFeeRateInfo(reply.getTxFeeRateInfo()));
                    return;
                }
                case createoffer: {
                    if (nonOptionArgs.size() < 9)
                        throw new IllegalArgumentException("incorrect parameter count,"
                                + " expecting payment acct id, buy | sell, currency code, amount, min amount,"
                                + " use-market-based-price, fixed-price | mkt-price-margin, security-deposit"
                                + " [,maker-fee-currency-code = bsq|btc]");

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
                    var makerFeeCurrencyCode = nonOptionArgs.size() == 10
                            ? nonOptionArgs.get(9)
                            : "btc";

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
                            .setMakerFeeCurrencyCode(makerFeeCurrencyCode)
                            .build();
                    var reply = offersService.createOffer(request);
                    out.println(formatOfferTable(singletonList(reply.getOffer()), currencyCode));
                    return;
                }
                case canceloffer: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, expecting offer id");

                    var offerId = nonOptionArgs.get(1);
                    var request = CancelOfferRequest.newBuilder()
                            .setId(offerId)
                            .build();
                    offersService.cancelOffer(request);
                    out.println("offer canceled and removed from offer book");
                    return;
                }
                case getoffer: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, expecting offer id");

                    var offerId = nonOptionArgs.get(1);
                    var request = GetOfferRequest.newBuilder()
                            .setId(offerId)
                            .build();
                    var reply = offersService.getOffer(request);
                    out.println(formatOfferTable(singletonList(reply.getOffer()),
                            reply.getOffer().getCounterCurrencyCode()));
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

                    List<OfferInfo> offers = reply.getOffersList();
                    if (offers.isEmpty())
                        out.printf("no %s %s offers found%n", direction, currencyCode);
                    else
                        out.println(formatOfferTable(reply.getOffersList(), currencyCode));

                    return;
                }
                case takeoffer: {
                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("incorrect parameter count, " +
                                " expecting offer id, payment acct id [,taker fee currency code = bsq|btc]");

                    var offerId = nonOptionArgs.get(1);
                    var paymentAccountId = nonOptionArgs.get(2);
                    var takerFeeCurrencyCode = nonOptionArgs.size() == 4
                            ? nonOptionArgs.get(3)
                            : "btc";

                    var request = TakeOfferRequest.newBuilder()
                            .setOfferId(offerId)
                            .setPaymentAccountId(paymentAccountId)
                            .setTakerFeeCurrencyCode(takerFeeCurrencyCode)
                            .build();
                    var reply = tradesService.takeOffer(request);
                    out.printf("trade %s successfully taken%n", reply.getTrade().getTradeId());
                    return;
                }
                case gettrade: {
                    // TODO make short-id a valid argument
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, "
                                + " expecting trade id [,showcontract = true|false]");

                    var tradeId = nonOptionArgs.get(1);
                    var showContract = false;
                    if (nonOptionArgs.size() == 3)
                        showContract = Boolean.getBoolean(nonOptionArgs.get(2));

                    var request = GetTradeRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    var reply = tradesService.getTrade(request);
                    if (showContract)
                        out.println(reply.getTrade().getContractAsJson());
                    else
                        out.println(TradeFormat.format(reply.getTrade()));
                    return;
                }
                case confirmpaymentstarted: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, expecting trade id");

                    var tradeId = nonOptionArgs.get(1);
                    var request = ConfirmPaymentStartedRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    tradesService.confirmPaymentStarted(request);
                    out.printf("trade %s payment started message sent%n", tradeId);
                    return;
                }
                case confirmpaymentreceived: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, expecting trade id");

                    var tradeId = nonOptionArgs.get(1);
                    var request = ConfirmPaymentReceivedRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    tradesService.confirmPaymentReceived(request);
                    out.printf("trade %s payment received message sent%n", tradeId);
                    return;
                }
                case keepfunds: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, expecting trade id");

                    var tradeId = nonOptionArgs.get(1);
                    var request = KeepFundsRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    tradesService.keepFunds(request);
                    out.printf("funds from trade %s saved in bisq wallet%n", tradeId);
                    return;
                }
                case withdrawfunds: {
                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("incorrect parameter count, "
                                + " expecting trade id, bitcoin wallet address");

                    var tradeId = nonOptionArgs.get(1);
                    var address = nonOptionArgs.get(2);
                    var request = WithdrawFundsRequest.newBuilder()
                            .setTradeId(tradeId)
                            .setAddress(address)
                            .build();
                    tradesService.withdrawFunds(request);
                    out.printf("funds from trade %s sent to btc address %s%n", tradeId, address);
                    return;
                }
                case getpaymentmethods: {
                    var request = GetPaymentMethodsRequest.newBuilder().build();
                    var reply = paymentAccountsService.getPaymentMethods(request);
                    reply.getPaymentMethodsList().forEach(p -> out.println(p.getId()));
                    return;
                }
                case getpaymentacctform: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("incorrect parameter count, expecting payment method id");

                    var paymentMethodId = nonOptionArgs.get(1);
                    var request = GetPaymentAccountFormRequest.newBuilder()
                            .setPaymentMethodId(paymentMethodId)
                            .build();
                    String jsonString = paymentAccountsService.getPaymentAccountForm(request)
                            .getPaymentAccountFormJson();
                    File jsonFile = saveFileToDisk(paymentMethodId.toLowerCase(),
                            ".json",
                            jsonString);
                    out.printf("payment account form %s%nsaved to %s%n",
                            jsonString, jsonFile.getAbsolutePath());
                    out.println("Edit the file, and use as the argument to a 'createpaymentacct' command.");
                    return;
                }
                case createpaymentacct: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException(
                                "incorrect parameter count, expecting path to payment account form");

                    var paymentAccountFormPath = Paths.get(nonOptionArgs.get(1));
                    if (!paymentAccountFormPath.toFile().exists())
                        throw new IllegalStateException(
                                format("payment account form '%s' could not be found",
                                        paymentAccountFormPath.toString()));

                    String jsonString;
                    try {
                        jsonString = new String(Files.readAllBytes(paymentAccountFormPath));
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                format("could not read %s", paymentAccountFormPath.toString()));
                    }

                    var request = CreatePaymentAccountRequest.newBuilder()
                            .setPaymentAccountForm(jsonString)
                            .build();
                    var reply = paymentAccountsService.createPaymentAccount(request);
                    out.println("payment account saved");
                    out.println(formatPaymentAcctTbl(singletonList(reply.getPaymentAccount())));
                    return;
                }
                case getpaymentaccts: {
                    var request = GetPaymentAccountsRequest.newBuilder().build();
                    var reply = paymentAccountsService.getPaymentAccounts(request);

                    List<PaymentAccount> paymentAccounts = reply.getPaymentAccountsList();
                    if (paymentAccounts.size() > 0)
                        out.println(formatPaymentAcctTbl(paymentAccounts));
                    else
                        out.println("no payment accounts are saved");

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

                    var timeout = toLong(nonOptionArgs.get(2));
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

    private static void verifyStringIsValidDecimal(String param) {
        try {
            Double.parseDouble(param);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(format("'%s' is not a number", param));
        }
    }

    private static void verifyStringIsValidLong(String param) {
        try {
            Long.parseLong(param);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(format("'%s' is not a number", param));
        }
    }

    private static long toLong(String param) {
        try {
            return Long.parseLong(param);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(format("'%s' is not a number", param));
        }
    }

    private static File saveFileToDisk(String prefix,
                                       @SuppressWarnings("SameParameterValue") String suffix,
                                       String text) {
        String timestamp = Long.toUnsignedString(new Date().getTime());
        String relativeFileName = prefix + "_" + timestamp + suffix;
        try {
            Path path = Paths.get(relativeFileName);
            if (!Files.exists(path)) {
                try (PrintWriter out = new PrintWriter(path.toString())) {
                    out.println(text);
                }
                return path.toAbsolutePath().toFile();
            } else {
                throw new IllegalStateException(format("could not overwrite existing file '%s'", relativeFileName));
            }
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(format("could not create file '%s'", relativeFileName));
        }
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
            stream.format(rowFormat, "getbalance [,currency code = bsq|btc]", "", "Get server wallet balances");
            stream.format(rowFormat, "getaddressbalance", "address", "Get server wallet address balance");
            stream.format(rowFormat, "getfundingaddresses", "", "Get BTC funding addresses");
            stream.format(rowFormat, "getunusedbsqaddress", "", "Get unused BSQ address");
            stream.format(rowFormat, "sendbsq", "address, amount [,tx fee rate (sats/byte)]", "Send BSQ");
            stream.format(rowFormat, "sendbtc", "address, amount [,tx fee rate (sats/byte)]", "Send BTC");
            stream.format(rowFormat, "gettxfeerate", "", "Get current tx fee rate in sats/byte");
            stream.format(rowFormat, "settxfeerate", "satoshis (per byte)", "Set custom tx fee rate in sats/byte");
            stream.format(rowFormat, "unsettxfeerate", "", "Unset custom tx fee rate");
            stream.format(rowFormat, "createoffer", "payment acct id, buy | sell, currency code, \\", "Create and place an offer");
            stream.format(rowFormat, "", "amount (btc), min amount, use mkt based price, \\", "");
            stream.format(rowFormat, "", "fixed price (btc) | mkt price margin (%), security deposit (%) \\", "");
            stream.format(rowFormat, "", "[,maker fee currency code = bsq|btc]", "");
            stream.format(rowFormat, "canceloffer", "offer id", "Cancel offer with id");
            stream.format(rowFormat, "getoffer", "offer id", "Get current offer with id");
            stream.format(rowFormat, "getoffers", "buy | sell, currency code", "Get current offers");
            stream.format(rowFormat, "takeoffer", "offer id, [,taker fee currency code = bsq|btc]", "Take offer with id");
            stream.format(rowFormat, "gettrade", "trade id [,showcontract = true|false]", "Get trade summary or full contract");
            stream.format(rowFormat, "confirmpaymentstarted", "trade id", "Confirm payment started");
            stream.format(rowFormat, "confirmpaymentreceived", "trade id", "Confirm payment received");
            stream.format(rowFormat, "keepfunds", "trade id", "Keep received funds in Bisq wallet");
            stream.format(rowFormat, "withdrawfunds", "trade id, bitcoin wallet address", "Withdraw received funds to external wallet address");
            stream.format(rowFormat, "getpaymentmethods", "", "Get list of supported payment account method ids");
            stream.format(rowFormat, "getpaymentacctform", "payment method id", "Get a new payment account form");
            stream.format(rowFormat, "createpaymentacct", "path to payment account form", "Create a new payment account");
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
