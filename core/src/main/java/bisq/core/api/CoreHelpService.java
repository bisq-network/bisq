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

    private static final String PARAM_DESCRIPTION_FORMAT = "%-4s%-26s%-28s%s";

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
                return getVersionHelp();
            default:
                throw new IllegalStateException("No help found for method " + methodName);
        }
    }

    private String getVersionHelp() {
        return "getversion" + "\n"
                + "\n" + "Returns the server version."
                + "\n"
                + "\n" + "Usage: getversion";
    }

    private String requiredParamDesc(int paramNum,
                                     String paramName,
                                     String description) {
        return paramDesc(paramNum + ".",
                paramName,
                "(required)",
                description);
    }

    private String optionalParamDesc(int paramNum,
                                     String paramName,
                                     String defaultParamValue,
                                     String description) {
        return paramDesc(paramNum + ".",
                paramName,
                "(optional, default=" + defaultParamValue + ")",
                description);
    }

    private String paramDesc(Object... parameterDescriptors) {
        return format(PARAM_DESCRIPTION_FORMAT, parameterDescriptors);
    }

    public static void main(String[] args) {
        CoreHelpService coreHelpService = new CoreHelpService();
        // out.println(coreHelpService.getMethodHelp("takeoffer"));
        out.println(coreHelpService.getMethodHelp("createoffer"));
    }
}
