package io.bisq.core.trade.statistics;

import com.google.inject.name.Named;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.storage.JsonFileManager;
import io.bisq.common.storage.Storage;
import io.bisq.core.offer.OfferPayload;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

//migrate old trade statistics via json to new database
@Slf4j
public class TradeStatisticsMigrationTool {

    @Inject
    public TradeStatisticsMigrationTool(Storage<TradeStatisticsList> statisticsStorage,
                                        P2PService p2PService,
                                        KeyRing keyRing,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
        JsonFileManager jsonFileManager = new JsonFileManager(storageDir);

        // We use blocking to be sure to not start reading the persistedTradeStatisticsList before we have set it.
        // just for migrating the stats....
        Object fromDisc = jsonFileManager.readJsonFromDisc("trade_statistics");
        List<TradeStatistics> persistedTradeStatisticsList = new ArrayList<>();
        if (fromDisc instanceof JSONArray) {
            JSONArray array = (JSONArray) fromDisc;
            for (Object anArray : array) {
                JSONObject json = (JSONObject) anArray;
                String currency = (String) json.get("currency");
                OfferPayload.Direction direction = json.get("direction").equals("BUY") ? OfferPayload.Direction.BUY : OfferPayload.Direction.SELL;
                long tradePrice = (long) json.get("tradePrice");
                long tradeAmount = (long) json.get("tradeAmount");
                long tradeDate = (long) json.get("tradeDate");
                String paymentMethodId = (String) json.get("paymentMethod");
                long offerDate = (long) json.get("offerDate");
                boolean offerUseMarketBasedPrice = (boolean) json.get("useMarketBasedPrice");
                double offerMarketPriceMargin = (double) json.get("marketPriceMargin");
                long offerAmount = (long) json.get("offerAmount");
                long offerMinAmount = (long) json.get("offerMinAmount");
                String offerId = (String) json.get("offerId");
                String depositTxId = (String) json.get("depositTxId");

                String baseCurrency = CurrencyUtil.isCryptoCurrency(currency) ? currency : "BTC";
                String counterCurrency = CurrencyUtil.isFiatCurrency(currency) ? currency : "BTC";
                long price = CurrencyUtil.isFiatCurrency(currency) ? tradePrice : Math.max(1, (long) (1000000000000d / tradePrice));

                TradeStatistics tradeStatistics = new TradeStatistics(
                        direction,
                        baseCurrency,
                        counterCurrency,
                        paymentMethodId,
                        offerDate,
                        offerUseMarketBasedPrice,
                        offerMarketPriceMargin,
                        offerAmount,
                        offerMinAmount,
                        offerId,
                        price,
                        tradeAmount,
                        tradeDate,
                        depositTxId,
                        keyRing.getPubKeyRing().getSignaturePubKeyBytes(),
                        null);
                persistedTradeStatisticsList.add(tradeStatistics);
            }
            statisticsStorage.initAndGetPersistedWithFileName("TradeStatistics");
            statisticsStorage.queueUpForSave(new TradeStatisticsList(new ArrayList<>(persistedTradeStatisticsList)), 200);
        } else {
            log.warn("Unknown JOSN object " + fromDisc);
        }

        if (p2PService.isBootstrapped()) {
            persistedTradeStatisticsList.forEach(e -> p2PService.addData(e, true));
        } else {
            p2PService.addP2PServiceListener(new P2PServiceListener() {
                @Override
                public void onRequestingDataCompleted() {
                }

                @Override
                public void onNoSeedNodeAvailable() {
                }

                @Override
                public void onNoPeersAvailable() {
                }

                @Override
                public void onBootstrapComplete() {
                    if (persistedTradeStatisticsList != null)
                        persistedTradeStatisticsList.forEach(e -> p2PService.addData(e, true));
                }

                @Override
                public void onTorNodeReady() {
                }

                @Override
                public void onHiddenServicePublished() {
                }

                @Override
                public void onSetupFailed(Throwable throwable) {
                }
            });
        }
    }
}
