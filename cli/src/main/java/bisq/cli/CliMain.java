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

import bisq.proto.grpc.AddressBalanceInfo;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetAddressBalanceRequest;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.OffersGrpc;
import bisq.proto.grpc.PaymentAccountsGrpc;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.WalletsGrpc;

import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import java.io.IOException;
import java.io.PrintStream;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.util.Collections.singletonList;

/**
 * A command-line client for the Bisq gRPC API.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
public class CliMain {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);
    private static final BigDecimal SATOSHI_DIVISOR = new BigDecimal(100000000);
    private static final DecimalFormat BTC_FORMAT = new DecimalFormat("###,##0.00000000");
    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    private static final Function<Long, String> formatSatoshis = (sats) ->
            BTC_FORMAT.format(BigDecimal.valueOf(sats).divide(SATOSHI_DIVISOR));

    private enum Method {
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
        setwalletpassword
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

        var methodName = nonOptionArgs.get(0);
        final Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' is not a supported method", methodName));
        }

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        var credentials = new PasswordCallCredentials(password);

        var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));

        var versionService = GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        var offersService = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        var paymentAccountsService = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        var walletsService = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);

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
                    var btcBalance = formatSatoshis.apply(reply.getBalance());
                    out.println(btcBalance);
                    return;
                }
                case getaddressbalance: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no address specified");

                    var request = GetAddressBalanceRequest.newBuilder()
                            .setAddress(nonOptionArgs.get(1)).build();
                    var reply = walletsService.getAddressBalance(request);
                    out.println(formatTable(singletonList(reply.getAddressBalanceInfo())));
                    return;
                }
                case getfundingaddresses: {
                    var request = GetFundingAddressesRequest.newBuilder().build();
                    var reply = walletsService.getFundingAddresses(request);
                    out.println(formatTable(reply.getAddressBalanceInfoList()));
                    return;
                }
                case getoffers: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no buy/sell direction specified");

                    var direction = nonOptionArgs.get(1).toUpperCase();
                    if (!direction.equals("BUY") && !direction.equals("SELL"))
                        throw new IllegalArgumentException("no buy/sell direction specified");

                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("no fiat currency specified");

                    var fiatCurrency = nonOptionArgs.get(2).toUpperCase();

                    var request = GetOffersRequest.newBuilder()
                            .setDirection(direction)
                            .setFiatCurrencyCode(fiatCurrency)
                            .build();
                    var reply = offersService.getOffers(request);

                    // TODO Calculate these format specifiers on the fly?
                    out.println(format("%-8s Price in %s for 1 BTC  %s  %-23s %-14s %-24s  %s",
                            "Buy/Sell", fiatCurrency, "BTC(min - max)", "        " + fiatCurrency + "(min - max)",
                            "Payment Method", "Creation Date", "ID"));
                    out.println(reply.getOffersList().stream()
                            .map(o -> format("%-8s %22s  %-25s %12s  %-14s %-24s  %s",
                                    o.getDirection().equals("BUY") ? "SELL" : "BUY",
                                    formatPrice(o.getPrice()),
                                    o.getMinAmount() != o.getAmount() ? formatSatoshis.apply(o.getMinAmount())
                                            + " - " + formatSatoshis.apply(o.getAmount())
                                            : formatSatoshis.apply(o.getAmount()),
                                    o.getMinVolume() != o.getVolume() ? formatVolume(o.getMinVolume())
                                            + " - " + formatVolume(o.getVolume())
                                            : formatVolume(o.getVolume()),
                                    o.getPaymentMethodShortName(),
                                    formatDateTime(o.getDate(), true),
                                    o.getId()))
                            .collect(Collectors.joining("\n")));
                    return;
                }
                case createpaymentacct: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no account name specified");

                    var accountName = nonOptionArgs.get(1);

                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("no account number specified");

                    var accountNumber = nonOptionArgs.get(2);

                    if (nonOptionArgs.size() < 4)
                        throw new IllegalArgumentException("no fiat currency specified");

                    var fiatCurrencyCode = nonOptionArgs.get(3).toUpperCase();

                    var request = CreatePaymentAccountRequest.newBuilder()
                            .setAccountName(accountName)
                            .setAccountNumber(accountNumber)
                            .setFiatCurrencyCode(fiatCurrencyCode).build();
                    paymentAccountsService.createPaymentAccount(request);
                    out.println(format("payment account %s saved", accountName));
                    return;
                }
                case getpaymentaccts: {
                    var request = GetPaymentAccountsRequest.newBuilder().build();
                    var reply = paymentAccountsService.getPaymentAccounts(request);
                    var columnFormatSpec = "%-41s %-25s %-14s %s";
                    out.println(format(columnFormatSpec, "ID", "Name", "Currency", "Payment Method"));
                    out.println(reply.getPaymentAccountsList().stream()
                            .map(a -> format(columnFormatSpec,
                                    a.getId(),
                                    a.getAccountName(),
                                    a.getSelectedTradeCurrency().getCode(),
                                    a.getPaymentMethod().getId()))
                            .collect(Collectors.joining("\n")));
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

                    var request = RemoveWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1)).build();
                    walletsService.removeWalletPassword(request);
                    out.println("wallet decrypted");
                    return;
                }
                case setwalletpassword: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    var requestBuilder = SetWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1));
                    var hasNewPassword = nonOptionArgs.size() == 3;
                    if (hasNewPassword)
                        requestBuilder.setNewPassword(nonOptionArgs.get(2));
                    walletsService.setWalletPassword(requestBuilder.build());
                    out.println("wallet encrypted" + (hasNewPassword ? " with new password" : ""));
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

    private static void printHelp(OptionParser parser, PrintStream stream) {
        try {
            stream.println("Bisq RPC Client");
            stream.println();
            stream.println("Usage: bisq-cli [options] <method> [params]");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
            stream.format("%-22s%-50s%s%n", "Method", "Params", "Description");
            stream.format("%-22s%-50s%s%n", "------", "------", "------------");
            stream.format("%-22s%-50s%s%n", "getversion", "", "Get server version");
            stream.format("%-22s%-50s%s%n", "getbalance", "", "Get server wallet balance");
            stream.format("%-22s%-50s%s%n", "getaddressbalance", "address", "Get server wallet address balance");
            stream.format("%-22s%-50s%s%n", "getfundingaddresses", "", "Get BTC funding addresses");
            stream.format("%-22s%-50s%s%n", "getoffers", "buy | sell, fiat currency code", "Get current offers");
            stream.format("%-22s%-50s%s%n", "createpaymentacct", "account name, account number, currency code", "Create PerfectMoney dummy account");
            stream.format("%-22s%-50s%s%n", "getpaymentaccts", "", "Get user payment accounts");
            stream.format("%-22s%-50s%s%n", "lockwallet", "", "Remove wallet password from memory, locking the wallet");
            stream.format("%-22s%-50s%s%n", "unlockwallet", "password timeout",
                    "Store wallet password in memory for timeout seconds");
            stream.format("%-22s%-50s%s%n", "setwalletpassword", "password [newpassword]",
                    "Encrypt wallet with password, or set new password on encrypted wallet");
            stream.println();
        } catch (IOException ex) {
            ex.printStackTrace(stream);
        }
    }

    private static String formatTable(List<AddressBalanceInfo> addressBalanceInfo) {
        return format("%-35s %13s  %s%n", "Address", "Balance", "Confirmations")
                + addressBalanceInfo.stream()
                .map(info -> format("%-35s %13s %14d",
                        info.getAddress(),
                        formatSatoshis.apply(info.getBalance()),
                        info.getNumConfirmations()))
                .collect(Collectors.joining("\n"));
    }

    // TODO Find a proper home for these formatting methods, with minimum duplication
    //  of the :core and :desktop utils they were copied from.

    // Copied from bisq.core.util.FormattingUtils (pass formatted date as well to client)
    private static String formatDateTime(long timestamp, boolean useLocaleAndLocalTimezone) {
        Date date = new Date(timestamp);
        Locale locale = useLocaleAndLocalTimezone ? Locale.getDefault() : Locale.US;
        DateFormat dateInstance = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
        DateFormat timeInstance = DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
        if (!useLocaleAndLocalTimezone) {
            dateInstance.setTimeZone(TimeZone.getTimeZone("UTC"));
            timeInstance.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return formatDateTime(date, dateInstance, timeInstance);
    }

    // Copied from bisq.core.util.FormattingUtils (pass formatted date as well to client)
    private static String formatDateTime(Date date, DateFormat dateFormatter, DateFormat timeFormatter) {
        if (date != null) {
            return dateFormatter.format(date) + " " + timeFormatter.format(date);
        } else {
            return "";
        }
    }

    private static String formatPrice(long price) {
        NUMBER_FORMAT.setMaximumFractionDigits(4);
        NUMBER_FORMAT.setMinimumFractionDigits(4);
        NUMBER_FORMAT.setRoundingMode(RoundingMode.UNNECESSARY);
        return NUMBER_FORMAT.format((double) price / 10000);
    }

    private static String formatVolume(long volume) {
        NUMBER_FORMAT.setMaximumFractionDigits(0);
        NUMBER_FORMAT.setRoundingMode(RoundingMode.UNNECESSARY);
        return NUMBER_FORMAT.format((double) volume / 10000);
    }
}
