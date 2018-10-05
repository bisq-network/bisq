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

package bisq.core.dao.governance.asset;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.state.BsqStateService;
import bisq.core.locale.CurrencyUtil;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssetService implements PersistedDataHost {


    private final Storage<RemovedAssetsList> storage;
    private final BsqStateService bsqStateService;
    @Getter
    private final RemovedAssetsList removedAssetsList = new RemovedAssetsList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AssetService(Storage<RemovedAssetsList> storage, BsqStateService bsqStateService) {
        this.storage = storage;
        this.bsqStateService = bsqStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            RemovedAssetsList persisted = storage.initAndGetPersisted(removedAssetsList, 100);
            if (persisted != null) {
                removedAssetsList.clear();
                removedAssetsList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addToRemovedAssetsListByVoting(String tickerSymbol) {
        log.info("Asset '{}' was removed by DAO voting", CurrencyUtil.getNameAndCode(tickerSymbol));
        removedAssetsList.add(new RemovedAsset(tickerSymbol, RemoveReason.VOTING));
        persist();
    }

    public boolean hasPaidBSQFee(String tickerSymbol) {
        //TODO
        return false;
    }


    public boolean isAssetRemoved(String tickerSymbol) {
        boolean isRemoved = removedAssetsList.getList().stream()
                .anyMatch(removedAsset -> removedAsset.getTickerSymbol().equals(tickerSymbol));
        if (isRemoved)
            log.info("Asset '{}' was removed", CurrencyUtil.getNameAndCode(tickerSymbol));

        return isRemoved;
    }

    public boolean isAssetRemovedByVoting1(String tickerSymbol) {
        boolean isRemoved = getRemovedAssetsByRemoveReason(RemoveReason.VOTING).stream()
                .anyMatch(removedAsset -> removedAsset.getTickerSymbol().equals(tickerSymbol));
        if (isRemoved)
            log.info("Asset '{}' was removed by DAO voting", CurrencyUtil.getNameAndCode(tickerSymbol));

        return isRemoved;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<RemovedAsset> getRemovedAssetsByRemoveReason(RemoveReason removeReason) {
        return removedAssetsList.getList().stream()
                .filter(e -> e.getRemoveReason() == removeReason)
                .collect(Collectors.toList());
    }

    private void persist() {
        storage.queueUpForSave(20);
    }
}
