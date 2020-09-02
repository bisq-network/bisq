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

package bisq.core.trade.txproof.xmr;

import bisq.core.trade.txproof.AssetTxProofHttpClient;

import bisq.network.Socks5ProxyProvider;
import bisq.network.http.HttpClientImpl;

import bisq.common.app.DevEnv;

import javax.inject.Inject;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.trade.txproof.xmr.XmrTxProofParser.MAX_DATE_TOLERANCE;

/**
 * This should help to test error scenarios in dev testing the app. This is additional to unit test which test the
 * correct data but do not test the context of the results and how it behaves in the UI.
 *
 * You have to change the binding in TradeModule to
 * bind(AssetTxProofHttpClient.class).to(DevTestXmrTxProofHttpClient.class); to use that class.
 *
 * This class can be removed once done testing, but as multiple devs are testing its useful to share it for now.
 */
@Slf4j
public class DevTestXmrTxProofHttpClient extends HttpClientImpl implements AssetTxProofHttpClient {
    enum ApiInvalidDetails {
        EMPTY_JSON,
        MISSING_DATA,
        MISSING_STATUS,
        UNHANDLED_STATUS,
        MISSING_ADDRESS,
        MISSING_TX_ID,
        MISSING_VIEW_KEY,
        MISSING_TS,
        MISSING_CONF,
        EXCEPTION
    }

    @Inject
    public DevTestXmrTxProofHttpClient(@Nullable Socks5ProxyProvider socks5ProxyProvider) {
        super(socks5ProxyProvider);
    }

    private static int counter;

    @Override
    public String requestWithGET(String param,
                                 @Nullable String headerKey,
                                 @Nullable String headerValue) {

        XmrTxProofRequest.Result result = XmrTxProofRequest.Result.PENDING;
        XmrTxProofRequest.Detail detail = XmrTxProofRequest.Detail.TX_NOT_FOUND;
        ApiInvalidDetails apiInvalidDetails = ApiInvalidDetails.EXCEPTION;

        int delay = counter == 0 ? 2000 : 100;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (counter >= 2) {
            detail = XmrTxProofRequest.Detail.PENDING_CONFIRMATIONS.numConfirmations(counter - 2);
        }
        counter++;
        switch (result) {
            case PENDING:
                switch (detail) {
                    case TX_NOT_FOUND:
                        return validJson().replace("success",
                                "fail");
                    case PENDING_CONFIRMATIONS:
                        return validJson().replace("201287",
                                String.valueOf(detail.getNumConfirmations()));
                    default:
                        return null;
                }
            case SUCCESS:
                return validJson();
            case FAILED:
                switch (detail) {
                    case TX_HASH_INVALID:
                        return validJson().replace("5e665addf6d7c6300670e8a89564ed12b5c1a21c336408e2835668f9a6a0d802",
                                "-");
                    case TX_KEY_INVALID:
                        return validJson().replace("f3ce66c9d395e5e460c8802b2c3c1fff04e508434f9738ee35558aac4678c906",
                                "-");
                    case ADDRESS_INVALID:
                        return validJson().replace("590f7263428051068bb45cdfcf93407c15b6e291d20c92d0251fcfbf53cc745cdf53319f7d6d7a8e21ea39041aabf31d220a32a875e3ca2087a777f1201c0571",
                                "-");
                    case NO_MATCH_FOUND:
                        return validJson().replace("match\": true",
                                "match\": false");
                    case AMOUNT_NOT_MATCHING:
                        return validJson().replace("8902597360000",
                                "18902597360000");
                    case TRADE_DATE_NOT_MATCHING:
                        DevEnv.setDevMode(false);
                        long date = (new Date(1574922644 * 1000L).getTime() - (MAX_DATE_TOLERANCE * 1000L + 1)) / 1000;
                        return validJson().replace("1574922644",
                                String.valueOf(date));
                    default:
                        return null;
                }
            case ERROR:
                switch (detail) {
                    case CONNECTION_FAILURE:
                        // Not part of parser level testing
                        return null;
                    case API_INVALID:
                        switch (apiInvalidDetails) {
                            case EMPTY_JSON:
                                return null;
                            case MISSING_DATA:
                                return validJson().replace("data",
                                        "missing");
                            case MISSING_STATUS:
                                return validJson().replace("status",
                                        "missing");
                            case UNHANDLED_STATUS:
                                return validJson().replace("success",
                                        "missing");
                            case MISSING_ADDRESS:
                                return validJson().replace("address",
                                        "missing");
                            case MISSING_TX_ID:
                                return validJson().replace("tx_hash",
                                        "missing");
                            case MISSING_VIEW_KEY:
                                return validJson().replace("viewkey",
                                        "missing");
                            case MISSING_TS:
                                return validJson().replace("tx_timestamp",
                                        "missing");
                            case MISSING_CONF:
                                return validJson().replace("tx_confirmations",
                                        "missing");
                            case EXCEPTION:
                                return validJson().replace("} ",
                                        "");
                            default:
                                return null;
                        }

                    case NO_RESULTS_TIMEOUT:
                        // Not part of parser level testing
                        return null;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    private String validJson() {
        return "{\n" +
                "  \"data\": {\n" +
                "    \"address\": \"590f7263428051068bb45cdfcf93407c15b6e291d20c92d0251fcfbf53cc745cdf53319f7d6d7a8e21ea39041aabf31d220a32a875e3ca2087a777f1201c0571\",\n" +
                "    \"outputs\": [\n" +
                "      {\n" +
                "        \"amount\": 8902597360000,\n" +
                "        \"match\": true,\n" +
                "        \"output_idx\": 0,\n" +
                "        \"output_pubkey\": \"2b6d2296f2591c198cd1aa47de9a5d74270963412ed30bbcc63b8eff29f0d43e\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"amount\": 0,\n" +
                "        \"match\": false,\n" +
                "        \"output_idx\": 1,\n" +
                "        \"output_pubkey\": \"f53271624847507d80b746e91e689e88bc41678d55246275f5ad3c0f7e8a9ced\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"tx_confirmations\": 201287,\n" +
                "    \"tx_hash\": \"5e665addf6d7c6300670e8a89564ed12b5c1a21c336408e2835668f9a6a0d802\",\n" +
                "    \"tx_prove\": true,\n" +
                "    \"tx_timestamp\": 1574922644,\n" +
                "    \"viewkey\": \"f3ce66c9d395e5e460c8802b2c3c1fff04e508434f9738ee35558aac4678c906\"\n" +
                "  },\n" +
                "  \"status\": \"success\"\n" +
                "} ";
    }

}
