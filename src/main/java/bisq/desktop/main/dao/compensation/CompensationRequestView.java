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

package bisq.desktop.main.dao.compensation;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.blockchain.BsqBlockChainListener;
import bisq.core.dao.request.compensation.CompensationRequest;
import bisq.core.dao.request.compensation.CompensationRequestManager;

import bisq.common.UserThread;
import bisq.common.locale.Res;

import javax.inject.Inject;

import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.stream.Collectors;

@FxmlView
public abstract class CompensationRequestView extends ActivatableView<GridPane, Void> implements BsqBlockChainListener {

    protected final CompensationRequestManager compensationRequestManger;
    protected final BsqBlockChain bsqBlockChain;
    protected final ObservableList<CompensationRequestListItem> observableList = FXCollections.observableArrayList();
    protected TableView<CompensationRequestListItem> tableView;
    protected final BsqWalletService bsqWalletService;
    protected final BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher;
    protected final BsqFormatter bsqFormatter;
    protected SortedList<CompensationRequestListItem> sortedList = new SortedList<>(observableList);
    protected Subscription selectedCompensationRequestSubscription;
    protected CompensationRequestDisplay compensationRequestDisplay;
    protected int gridRow = 0;
    protected GridPane detailsGridPane, gridPane;
    protected SplitPane compensationRequestPane;
    protected CompensationRequestListItem selectedCompensationRequest;
    protected ChangeListener<Number> chainHeightChangeListener;
    protected ListChangeListener<CompensationRequest> compensationRequestListChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    protected CompensationRequestView(CompensationRequestManager compensationRequestManger,
                                      BsqWalletService bsqWalletService,
                                      BsqBlockChain bsqBlockChain,
                                      BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                      BsqFormatter bsqFormatter) {
        this.compensationRequestManger = compensationRequestManger;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockChain = bsqBlockChain;
        this.bsqBlockChainChangeDispatcher = bsqBlockChainChangeDispatcher;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        selectedCompensationRequestSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectCompensationRequest);

        bsqWalletService.getChainHeightProperty().addListener(chainHeightChangeListener);
        bsqBlockChainChangeDispatcher.addBsqBlockChainListener(this);
        compensationRequestManger.getAllRequests().addListener(compensationRequestListChangeListener);
        updateList();
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();

        selectedCompensationRequestSubscription.unsubscribe();

        bsqWalletService.getChainHeightProperty().removeListener(chainHeightChangeListener);
        bsqBlockChainChangeDispatcher.removeBsqBlockChainListener(this);
        compensationRequestManger.getAllRequests().removeListener(compensationRequestListChangeListener);

        observableList.forEach(CompensationRequestListItem::cleanup);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBsqBlockChainChanged() {
        // Need delay otherwise we modify list while dispatching  and cause a ConcurrentModificationException
        UserThread.execute(this::updateList);
    }

    abstract protected void updateList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void doUpdateList(FilteredList<CompensationRequest> list) {
        observableList.forEach(CompensationRequestListItem::cleanup);

        observableList.setAll(list.stream()
                .map(e -> new CompensationRequestListItem(e, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, bsqFormatter))
                .collect(Collectors.toSet()));

        if (list.isEmpty() && compensationRequestDisplay != null)
            compensationRequestDisplay.removeAllFields();
    }

    protected void onSelectCompensationRequest(CompensationRequestListItem item) {
        selectedCompensationRequest = item;
        if (item != null) {
            final CompensationRequest compensationRequest = item.getCompensationRequest();
            compensationRequestDisplay.removeAllFields();
            compensationRequestDisplay.createAllFields(Res.get("dao.compensation.selectedRequest"), Layout.GROUP_DISTANCE);
            compensationRequestDisplay.setAllFieldsEditable(false);
            compensationRequestDisplay.fillWithData(compensationRequest.getPayload());
        }
    }
}

