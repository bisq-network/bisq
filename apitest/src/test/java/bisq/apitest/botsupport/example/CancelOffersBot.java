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

package bisq.apitest.botsupport.example;

import bisq.proto.grpc.OfferInfo;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.botsupport.protocol.BotProtocol.BTC;



import bisq.apitest.botsupport.BotClient;
import bisq.cli.GrpcClient;

/**
 * Be careful when using this on mainnet.  Offer fees are forfeited.
 */
@Slf4j
public class CancelOffersBot {
    // TODO refactor BaseMarketMakerBot -> BaseBot, which includes grpc client ctr args.

    protected final String host;
    protected final int port;
    protected final String password;
    protected final BotClient botClient;

    public CancelOffersBot(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.botClient = new BotClient(new GrpcClient(host, port, password));
    }

    public void cancelAllBsqOffers() {
        var myOffers = botClient.getMyOffersSortedByDate(BTC);
        for (OfferInfo myOffer : myOffers) {
            log.info("Removing offer {} from offer book.", myOffer.getId());
            botClient.cancelOffer(myOffer);
        }
        log.info("Removed {} offers.", myOffers.size());
    }
}
