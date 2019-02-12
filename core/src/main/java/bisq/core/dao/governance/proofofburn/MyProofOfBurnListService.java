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

package bisq.core.dao.governance.proofofburn;

import bisq.core.dao.DaoSetupService;

import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the persistence of MyProofOfBurn objects.
 */
@Slf4j
public class MyProofOfBurnListService implements PersistedDataHost, DaoSetupService {

    private final Storage<MyProofOfBurnList> storage;
    private final MyProofOfBurnList myProofOfBurnList = new MyProofOfBurnList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyProofOfBurnListService(Storage<MyProofOfBurnList> storage) {
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (DevEnv.isDaoActivated()) {
            MyProofOfBurnList persisted = storage.initAndGetPersisted(myProofOfBurnList, 100);
            if (persisted != null) {
                myProofOfBurnList.clear();
                myProofOfBurnList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMyProofOfBurn(MyProofOfBurn myProofOfBurn) {
        if (!myProofOfBurnList.contains(myProofOfBurn)) {
            myProofOfBurnList.add(myProofOfBurn);
            persist();
        }
    }

    public List<MyProofOfBurn> getMyProofOfBurnList() {
        return myProofOfBurnList.getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persist() {
        storage.queueUpForSave(20);
    }
}
