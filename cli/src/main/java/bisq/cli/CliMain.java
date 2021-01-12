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
import bisq.proto.grpc.GetMethodHelpRequest;
import bisq.proto.grpc.GetMyOfferRequest;
import bisq.proto.grpc.GetMyOffersRequest;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.GetPaymentAccountFormRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetPaymentMethodsRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.GetTransactionRequest;
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

import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.CurrencyFormat.formatTxFeeRateInfo;
import static bisq.cli.CurrencyFormat.toSatoshis;
import static bisq.cli.CurrencyFormat.toSecurityDepositAsPct;
import static bisq.cli.TableFormat.*;
import static bisq.proto.grpc.HelpGrpc.HelpBlockingStub;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.util.Collections.singletonList;
import static joptsimple.internal.Strings.EMPTY;



import bisq.cli.opts.ArgumentList;
import bisq.cli.opts.CancelOfferOptionParser;
import bisq.cli.opts.CreateOfferOptionParser;
import bisq.cli.opts.CreatePaymentAcctOptionParser;
import bisq.cli.opts.GetAddressBalanceOptionParser;
import bisq.cli.opts.GetBalanceOptionParser;
import bisq.cli.opts.GetOfferOptionParser;
import bisq.cli.opts.GetOffersOptionParser;
import bisq.cli.opts.GetPaymentAcctFormOptionParser;
import bisq.cli.opts.GetTradeOptionParser;
import bisq.cli.opts.GetTransactionOptionParser;
import bisq.cli.opts.RegisterDisputeAgentOptionParser;
import bisq.cli.opts.RemoveWalletPasswordOptionParser;
import bisq.cli.opts.SendBsqOptionParser;
import bisq.cli.opts.SendBtcOptionParser;
import bisq.cli.opts.SetTxFeeRateOptionParser;
import bisq.cli.opts.SetWalletPasswordOptionParser;
import bisq.cli.opts.TakeOfferOptionParser;
import bisq.cli.opts.UnlockWalletOptionParser;
import bisq.cli.opts.WithdrawFundsOptionParser;

/**
 * A command-line client for the Bisq gRPC API.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
public class CliMain {

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

        var hostOpt = parser.accepts("host", "rpc server hostname or ip")
                .withRequiredArg()
                .defaultsTo("localhost");

        var portOpt = parser.accepts("port", "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);

        var passwordOpt = parser.accepts("password", "rpc server password")
                .withRequiredArg();

        var methodOpt = parser.accepts("m", "rpc method")
                .withRequiredArg()
                .defaultsTo(EMPTY);

        // Parse the CLI opts host, port, password, and help.  The help opt may
        // indicate the user is asking for method level help, and will be excluded
        // from the parsed options if a method opt is present in String[] args.
        OptionSet options = parser.parse(new ArgumentList(args).getCLIArguments());

        if (options.has(helpOpt)) {
            printHelp(parser, out);
            return;
        }

        if (!options.has(methodOpt)) {
            printHelp(parser, out);
            return;
        }

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        var methodName = options.valueOf(methodOpt);
        Method method;
        try {
            method = getMethodFromCmd(methodName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' is not a supported method", methodName));
        }

        GrpcStubs grpcStubs = new GrpcStubs(host, port, password);
        var disputeAgentsService = grpcStubs.disputeAgentsService;
        var helpService = grpcStubs.helpService;
        var offersService = grpcStubs.offersService;
        var paymentAccountsService = grpcStubs.paymentAccountsService;
        var tradesService = grpcStubs.tradesService;
        var versionService = grpcStubs.versionService;
        var walletsService = grpcStubs.walletsService;

        try {
            switch (method) {
                case getversion: {
                    // TODO supply method help even though no opts parser is needed.
                    var request = GetVersionRequest.newBuilder().build();
                    var version = versionService.getVersion(request).getVersion();
                    out.println(version);
                    return;
                }
                case getbalance: {
                    var opts = new GetBalanceOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var currencyCode = opts.getCurrencyCode();
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
                    var opts = new GetAddressBalanceOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var address = opts.getAddress();
                    var request = GetAddressBalanceRequest.newBuilder()
                            .setAddress(address).build();
                    var reply = walletsService.getAddressBalance(request);
                    out.println(formatAddressBalanceTbl(singletonList(reply.getAddressBalanceInfo())));
                    return;
                }
                case getfundingaddresses: {
                    // TODO supply method help even though no opts parser is needed.
                    var request = GetFundingAddressesRequest.newBuilder().build();
                    var reply = walletsService.getFundingAddresses(request);
                    out.println(formatAddressBalanceTbl(reply.getAddressBalanceInfoList()));
                    return;
                }
                case getunusedbsqaddress: {
                    // TODO supply method help even though no opts parser is needed.
                    var request = GetUnusedBsqAddressRequest.newBuilder().build();
                    var reply = walletsService.getUnusedBsqAddress(request);
                    out.println(reply.getAddress());
                    return;
                }
                case sendbsq: {
                    var opts = new SendBsqOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var address = opts.getAddress();
                    var amount = opts.getAmount();
                    verifyStringIsValidDecimal(amount);

                    var txFeeRate = opts.getFeeRate();
                    if (txFeeRate.isEmpty())
                        verifyStringIsValidLong(txFeeRate);

                    var request = SendBsqRequest.newBuilder()
                            .setAddress(address)
                            .setAmount(amount)
                            .setTxFeeRate(txFeeRate)
                            .build();
                    var reply = walletsService.sendBsq(request);
                    TxInfo txInfo = reply.getTxInfo();
                    out.printf("%s bsq sent to %s in tx %s%n",
                            amount,
                            address,
                            txInfo.getTxId());
                    return;
                }
                case sendbtc: {
                    var opts = new SendBtcOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var address = opts.getAddress();
                    var amount = opts.getAmount();
                    verifyStringIsValidDecimal(amount);

                    var txFeeRate = opts.getFeeRate();
                    if (txFeeRate.isEmpty())
                        verifyStringIsValidLong(txFeeRate);

                    var memo = opts.getMemo();
                    var request = SendBtcRequest.newBuilder()
                            .setAddress(address)
                            .setAmount(amount)
                            .setTxFeeRate(txFeeRate)
                            .setMemo(memo)
                            .build();
                    var reply = walletsService.sendBtc(request);
                    TxInfo txInfo = reply.getTxInfo();
                    out.printf("%s btc sent to %s in tx %s%n",
                            amount,
                            address,
                            txInfo.getTxId());
                    return;
                }
                case gettxfeerate: {
                    // TODO supply method help even though no opts parser is needed.
                    var request = GetTxFeeRateRequest.newBuilder().build();
                    var reply = walletsService.getTxFeeRate(request);
                    out.println(formatTxFeeRateInfo(reply.getTxFeeRateInfo()));
                    return;
                }
                case settxfeerate: {
                    var opts = new SetTxFeeRateOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var txFeeRate = toLong(opts.getFeeRate());
                    var request = SetTxFeeRatePreferenceRequest.newBuilder()
                            .setTxFeeRatePreference(txFeeRate)
                            .build();
                    var reply = walletsService.setTxFeeRatePreference(request);
                    out.println(formatTxFeeRateInfo(reply.getTxFeeRateInfo()));
                    return;
                }
                case unsettxfeerate: {
                    // TODO supply method help even though no opts parser is needed.
                    var request = UnsetTxFeeRatePreferenceRequest.newBuilder().build();
                    var reply = walletsService.unsetTxFeeRatePreference(request);
                    out.println(formatTxFeeRateInfo(reply.getTxFeeRateInfo()));
                    return;
                }
                case gettransaction: {
                    var opts = new GetTransactionOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var txId = opts.getTxId();
                    var request = GetTransactionRequest.newBuilder()
                            .setTxId(txId)
                            .build();
                    var reply = walletsService.getTransaction(request);
                    out.println(TransactionFormat.format(reply.getTxInfo()));
                    return;
                }
                case createoffer: {
                    var opts = new CreateOfferOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var paymentAcctId = opts.getPaymentAccountId();
                    var direction = opts.getDirection();
                    var currencyCode = opts.getCurrencyCode();
                    var amount = toSatoshis(opts.getAmount());
                    var minAmount = toSatoshis(opts.getMinAmount());
                    var useMarketBasedPrice = opts.isUsingMktPriceMargin();
                    var fixedPrice = opts.getFixedPrice();
                    var marketPriceMargin = opts.getMktPriceMarginAsBigDecimal();
                    var securityDeposit = toSecurityDepositAsPct(opts.getSecurityDeposit());
                    var makerFeeCurrencyCode = opts.getMakerFeeCurrencyCode();
                    var request = CreateOfferRequest.newBuilder()
                            .setDirection(direction)
                            .setCurrencyCode(currencyCode)
                            .setAmount(amount)
                            .setMinAmount(minAmount)
                            .setUseMarketBasedPrice(useMarketBasedPrice)
                            .setPrice(fixedPrice)
                            .setMarketPriceMargin(marketPriceMargin.doubleValue())
                            .setBuyerSecurityDeposit(securityDeposit)
                            .setTriggerPrice(0) // TODO Support optional trigger price.
                            .setPaymentAccountId(paymentAcctId)
                            .setMakerFeeCurrencyCode(makerFeeCurrencyCode)
                            .build();
                    var reply = offersService.createOffer(request);
                    out.println(formatOfferTable(singletonList(reply.getOffer()), currencyCode));
                    return;
                }
                case canceloffer: {
                    var opts = new CancelOfferOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var offerId = opts.getOfferId();
                    var request = CancelOfferRequest.newBuilder()
                            .setId(offerId)
                            .build();
                    offersService.cancelOffer(request);
                    out.println("offer canceled and removed from offer book");
                    return;
                }
                case getoffer: {
                    var opts = new GetOfferOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var offerId = opts.getOfferId();
                    var request = GetOfferRequest.newBuilder()
                            .setId(offerId)
                            .build();
                    var reply = offersService.getOffer(request);
                    out.println(formatOfferTable(singletonList(reply.getOffer()),
                            reply.getOffer().getCounterCurrencyCode()));
                    return;
                }
                case getmyoffer: {
                    var opts = new GetOfferOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var offerId = opts.getOfferId();
                    var request = GetMyOfferRequest.newBuilder()
                            .setId(offerId)
                            .build();
                    var reply = offersService.getMyOffer(request);
                    out.println(formatOfferTable(singletonList(reply.getOffer()),
                            reply.getOffer().getCounterCurrencyCode()));
                    return;
                }
                case getoffers: {
                    var opts = new GetOffersOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var direction = opts.getDirection();
                    var currencyCode = opts.getCurrencyCode();
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
                case getmyoffers: {
                    var opts = new GetOffersOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var direction = opts.getDirection();
                    var currencyCode = opts.getCurrencyCode();
                    var request = GetMyOffersRequest.newBuilder()
                            .setDirection(direction)
                            .setCurrencyCode(currencyCode)
                            .build();
                    var reply = offersService.getMyOffers(request);

                    List<OfferInfo> offers = reply.getOffersList();
                    if (offers.isEmpty())
                        out.printf("no %s %s offers found%n", direction, currencyCode);
                    else
                        out.println(formatOfferTable(reply.getOffersList(), currencyCode));

                    return;
                }
                case takeoffer: {
                    var opts = new TakeOfferOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var offerId = opts.getOfferId();
                    var paymentAccountId = opts.getPaymentAccountId();
                    var takerFeeCurrencyCode = opts.getTakerFeeCurrencyCode();
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
                    // TODO make short-id a valid argument?
                    var opts = new GetTradeOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var tradeId = opts.getTradeId();
                    var showContract = opts.getShowContract();
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
                    var opts = new GetTradeOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var tradeId = opts.getTradeId();
                    var request = ConfirmPaymentStartedRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    tradesService.confirmPaymentStarted(request);
                    out.printf("trade %s payment started message sent%n", tradeId);
                    return;
                }
                case confirmpaymentreceived: {
                    var opts = new GetTradeOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var tradeId = opts.getTradeId();
                    var request = ConfirmPaymentReceivedRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    tradesService.confirmPaymentReceived(request);
                    out.printf("trade %s payment received message sent%n", tradeId);
                    return;
                }
                case keepfunds: {
                    var opts = new GetTradeOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var tradeId = opts.getTradeId();
                    var request = KeepFundsRequest.newBuilder()
                            .setTradeId(tradeId)
                            .build();
                    tradesService.keepFunds(request);
                    out.printf("funds from trade %s saved in bisq wallet%n", tradeId);
                    return;
                }
                case withdrawfunds: {
                    var opts = new WithdrawFundsOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var tradeId = opts.getTradeId();
                    var address = opts.getAddress();
                    // Multi-word memos must be double quoted.
                    var memo = opts.getMemo();
                    var request = WithdrawFundsRequest.newBuilder()
                            .setTradeId(tradeId)
                            .setAddress(address)
                            .setMemo(memo)
                            .build();
                    tradesService.withdrawFunds(request);
                    out.printf("trade %s funds sent to btc address %s%n", tradeId, address);
                    return;
                }
                case getpaymentmethods: {
                    // TODO supply method help even though no opts parser is needed.
                    var request = GetPaymentMethodsRequest.newBuilder().build();
                    var reply = paymentAccountsService.getPaymentMethods(request);
                    reply.getPaymentMethodsList().forEach(p -> out.println(p.getId()));
                    return;
                }
                case getpaymentacctform: {
                    var opts = new GetPaymentAcctFormOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var paymentMethodId = opts.getPaymentMethodId();
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
                    var opts = new CreatePaymentAcctOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var paymentAccountForm = opts.getPaymentAcctForm();
                    String jsonString;
                    try {
                        jsonString = new String(Files.readAllBytes(paymentAccountForm));
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                format("could not read %s", paymentAccountForm.toString()));
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
                    // TODO supply method help even though no opts parser is needed.
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
                    // TODO supply method help even though no opts parser is needed.
                    var request = LockWalletRequest.newBuilder().build();
                    walletsService.lockWallet(request);
                    out.println("wallet locked");
                    return;
                }
                case unlockwallet: {
                    var opts = new UnlockWalletOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var walletPassword = opts.getPassword();
                    var timeout = opts.getUnlockTimeout();
                    var request = UnlockWalletRequest.newBuilder()
                            .setPassword(walletPassword)
                            .setTimeout(timeout).build();
                    walletsService.unlockWallet(request);
                    out.println("wallet unlocked");
                    return;
                }
                case removewalletpassword: {
                    var opts = new RemoveWalletPasswordOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var walletPassword = opts.getPassword();
                    var request = RemoveWalletPasswordRequest.newBuilder()
                            .setPassword(walletPassword).build();
                    walletsService.removeWalletPassword(request);
                    out.println("wallet decrypted");
                    return;
                }
                case setwalletpassword: {
                    var opts = new SetWalletPasswordOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var walletPassword = opts.getPassword();
                    var newWalletPassword = opts.getNewPassword();
                    var requestBuilder = SetWalletPasswordRequest.newBuilder()
                            .setPassword(walletPassword)
                            .setNewPassword(newWalletPassword);
                    walletsService.setWalletPassword(requestBuilder.build());
                    out.println("wallet encrypted" + (!newWalletPassword.isEmpty() ? " with new password" : ""));
                    return;
                }
                case registerdisputeagent: {
                    var opts = new RegisterDisputeAgentOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        out.println(getMethodHelp(helpService, method));
                        return;
                    }
                    var disputeAgentType = opts.getDisputeAgentType();
                    var registrationKey = opts.getRegistrationKey();
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

    private static void printHelp(OptionParser parser, @SuppressWarnings("SameParameterValue") PrintStream stream) {
        try {
            stream.println("Bisq RPC Client");
            stream.println();
            stream.println("Usage: bisq-cli [options] <method> [params]");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
            String rowFormat = "%-24s%-52s%s%n";
            stream.format(rowFormat, "Method", "Params", "Description");
            stream.format(rowFormat, "------", "------", "------------");
            stream.format(rowFormat, "getversion", "", "Get server version");
            stream.format(rowFormat, "getbalance", "[currency code = bsq|btc]", "Get server wallet balances");
            stream.format(rowFormat, "getaddressbalance", "address", "Get server wallet address balance");
            stream.format(rowFormat, "getfundingaddresses", "", "Get BTC funding addresses");
            stream.format(rowFormat, "getunusedbsqaddress", "", "Get unused BSQ address");
            stream.format(rowFormat, "sendbsq", "address, amount [,tx fee rate (sats/byte)]", "Send BSQ");
            stream.format(rowFormat, "sendbtc", "address, amount [,tx fee rate (sats/byte), \"memo\"]", "Send BTC");
            stream.format(rowFormat, "gettxfeerate", "", "Get current tx fee rate in sats/byte");
            stream.format(rowFormat, "settxfeerate", "satoshis (per byte)", "Set custom tx fee rate in sats/byte");
            stream.format(rowFormat, "unsettxfeerate", "", "Unset custom tx fee rate");
            stream.format(rowFormat, "gettransaction", "transaction id", "Get transaction with id");
            stream.format(rowFormat, "createoffer", "payment acct id, buy | sell, currency code, \\", "Create and place an offer");
            stream.format(rowFormat, "", "amount (btc), min amount, use mkt based price, \\", "");
            stream.format(rowFormat, "", "fixed price (btc) | mkt price margin (%), security deposit (%) \\", "");
            stream.format(rowFormat, "", "[,maker fee currency code = bsq|btc]", "");
            stream.format(rowFormat, "canceloffer", "offer id", "Cancel offer with id");
            stream.format(rowFormat, "getoffer", "offer id", "Get current offer with id");
            stream.format(rowFormat, "getmyoffer", "offer id", "Get my current offer with id");
            stream.format(rowFormat, "getoffers", "buy | sell, currency code", "Get current offers");
            stream.format(rowFormat, "getmyoffers", "buy | sell, currency code", "Get my current offers");
            stream.format(rowFormat, "takeoffer", "offer id [,taker fee currency code = bsq|btc]", "Take offer with id");
            stream.format(rowFormat, "gettrade", "trade id [,showcontract = true|false]", "Get trade summary or full contract");
            stream.format(rowFormat, "confirmpaymentstarted", "trade id", "Confirm payment started");
            stream.format(rowFormat, "confirmpaymentreceived", "trade id", "Confirm payment received");
            stream.format(rowFormat, "keepfunds", "trade id", "Keep received funds in Bisq wallet");
            stream.format(rowFormat, "withdrawfunds", "trade id, bitcoin wallet address  [,\"memo\"]",
                    "Withdraw received funds to external wallet address");
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

    private static String getMethodHelp(HelpBlockingStub helpService, Method method) {
        var request = GetMethodHelpRequest.newBuilder().setMethodName(method.name()).build();
        var reply = helpService.getMethodHelp(request);
        return reply.getMethodHelp();
    }
}
