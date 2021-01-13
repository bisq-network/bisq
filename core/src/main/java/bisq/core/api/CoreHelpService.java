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

package bisq.core.api;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.out;

@Singleton
@Slf4j
class CoreHelpService {

    private static final String PARAM_DESCRIPTION_FORMAT = "%-30s%-28s%s";

    private final String exampleCliBase;
    private final String examplePaymentAccountId;
    private final String exampleOfferId;

    @Inject
    public CoreHelpService() {
        this.exampleCliBase = "./bisq-cli --password=xyz --port=9998";
        this.examplePaymentAccountId = UUID.randomUUID().toString();
        this.exampleOfferId = UUID.randomUUID().toString();
    }

    public String getMethodHelp(String methodName) {
        switch (methodName) {
            case "getversion":
                return noArgMethodHelp(methodName, "Returns the server version.");
            case "getfundingaddresses":
                return noArgMethodHelp(methodName, "Returns a list of receiving BTC addresses.");
            case "getunusedbsqaddress":
                return noArgMethodHelp(methodName, "Returns an unused BSQ receiving address.");
            case "gettxfeerate":
                return noArgMethodHelp(methodName,
                        "Returns the most recent bitcoin network transaction fee the Bisq server could find.");
            case "unsettxfeerate":
                return noArgMethodHelp(methodName, "Unsets the tx fee rate user preference.");
            case "getpaymentmethods":
                return noArgMethodHelp(methodName, "Returns a list of currently supported fiat payment methods.");
            case "getpaymentaccts":
                return noArgMethodHelp(methodName, "Returns a list of fiat payment accounts.");
            case "lockwallet":
                return noArgMethodHelp(methodName,
                        "Locks an unlocked wallet before an unlockwallet timeout expires.");
            case "takeoffer":
                return takeOfferHelp();
            default:
                throw new IllegalStateException("no help found for " + methodName);
        }
    }

    private String takeOfferHelp() {
        String exampleDescription = format("To take an offer with ID %s using matching"
                        + " payment account with ID %s, paying the Bisq trading fee in BSQ:",
                exampleOfferId,
                examplePaymentAccountId);
        String exampleCommand = format("%s takeoffer  -o=%s  -p=%s  -c=bsq",
                exampleCliBase,
                exampleOfferId,
                examplePaymentAccountId);
        return "takeoffer" + "\n"
                + "\n"
                + "Takes an existing offer for a matching payment method."
                + "  The Bisq trade fee can be paid in BSQ or BTC." + "\n"
                + "\n"
                + "Usage: takeoffer -o=offer-id -p=payment-acct-id [-c=taker-fee-currency-code = bsq|btc]" + "\n"
                + "\n"
                + "Parameters:" + "\n"
                + requiredParamDesc("-o=offer-id", "The ID of the offer being taken.") + "\n"
                + requiredParamDesc("-i=payment-acct-id", "The ID of the payment account to be used in the trade.") + "\n"
                + optionalParamDesc("-c=taker-fee-currency-code", "BTC", "The Bisq trade taker fee currency (BSQ or BTC).") + "\n"
                + "\n"
                + "Example:" + "\n"
                + exampleDescription + "\n"
                + exampleCommand + "\n"
                + "\n";
    }

    private String noArgMethodHelp(String methodName, String description) {
        return methodName + "\n"
                + "\n" + description
                + "\n"
                + "\n" + "Usage: " + methodName
                + "\n"
                + "\n" + "Example: " + exampleCliBase + " " + methodName;
    }

    private String requiredParamDesc(String paramName,
                                     String description) {
        return paramDesc(paramName,
                "(required)",
                description);
    }

    private String optionalParamDesc(String paramName,
                                     String defaultParamValue,
                                     String description) {
        return paramDesc(paramName,
                "(optional, default=" + defaultParamValue + ")",
                description);
    }

    private String paramDesc(Object... parameterDescriptors) {
        return format(PARAM_DESCRIPTION_FORMAT, parameterDescriptors);
    }

    // Main method is for viewing help text without running the server.
    public static void main(String[] args) {
        CoreHelpService coreHelpService = new CoreHelpService();
        // out.println(coreHelpService.getMethodHelp("getversion"));
        out.println(coreHelpService.getMethodHelp("takeoffer"));
        // out.println(coreHelpService.getMethodHelp("createoffer"));
    }
}
