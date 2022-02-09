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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.PeerInfoIconTrading;
import bisq.desktop.components.list.FilterBox;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.main.portfolio.presentation.PortfolioUtil;
import bisq.desktop.main.shared.ChatView;
import bisq.desktop.util.CssTheme;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.FormBuilder;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.traderchat.TradeChatSession;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.VolumeUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXBadge;
import com.jfoenix.controls.JFXButton;

import javafx.fxml.FXML;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@FxmlView
public class PendingTradesView extends ActivatableViewAndModel<VBox, PendingTradesViewModel> {
    public interface ChatCallback {
        void onOpenChat(Trade trade);
    }

    private final TradeDetailsWindow tradeDetailsWindow;
    private final Navigation navigation;
    private final KeyRing keyRing;
    private final PrivateNotificationManager privateNotificationManager;
    private final boolean useDevPrivilegeKeys;
    private final boolean useDevModeHeader;
    private final Preferences preferences;
    @FXML
    FilterBox filterBox;
    @FXML
    TableView<PendingTradesListItem> tableView;
    @FXML
    TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, volumeColumn, amountColumn, avatarColumn,
            marketColumn, roleColumn, paymentMethodColumn, tradeIdColumn, dateColumn, chatColumn, moveTradeToFailedColumn;
    private FilteredList<PendingTradesListItem> filteredList;
    private SortedList<PendingTradesListItem> sortedList;
    private TradeSubView selectedSubView;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;
    private Subscription selectedTableItemSubscription;
    private Subscription selectedItemSubscription;
    private Stage chatPopupStage;
    private ListChangeListener<PendingTradesListItem> tradesListChangeListener;
    private final Map<String, Long> newChatMessagesByTradeMap = new HashMap<>();
    private String tradeIdOfOpenChat;
    private double chatPopupStageXPosition = -1;
    private double chatPopupStageYPosition = -1;
    private ChangeListener<Number> xPositionListener;
    private ChangeListener<Number> yPositionListener;

    private final Map<String, Button> buttonByTrade = new HashMap<>();
    private final Map<String, JFXBadge> badgeByTrade = new HashMap<>();
    private final Map<String, ListChangeListener<ChatMessage>> listenerByTrade = new HashMap<>();
    private ChangeListener<Trade.DisputeState> disputeStateListener;
    private ChangeListener<MediationResultState> mediationResultStateListener;
    private ChangeListener<Number> getMempoolStatusListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesView(PendingTradesViewModel model,
                             TradeDetailsWindow tradeDetailsWindow,
                             Navigation navigation,
                             KeyRing keyRing,
                             PrivateNotificationManager privateNotificationManager,
                             Preferences preferences,
                             @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys,
                             @Named(Config.USE_DEV_MODE_HEADER) boolean useDevModeHeader) {
        super(model);
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.navigation = navigation;
        this.keyRing = keyRing;
        this.privateNotificationManager = privateNotificationManager;
        this.preferences = preferences;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        this.useDevModeHeader = useDevModeHeader;
    }

    @Override
    public void initialize() {
        priceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.price")));
        amountColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())));
        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amount")));
        marketColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.market")));
        roleColumn.setGraphic(new AutoTooltipLabel(Res.get("portfolio.pending.role")));
        dateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.dateTime")));
        tradeIdColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.tradeId")));
        paymentMethodColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.paymentMethod")));
        avatarColumn.setText("");
        chatColumn.setText("");
        moveTradeToFailedColumn.setText("");

        setTradeIdColumnCellFactory();
        setDateColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setPaymentMethodColumnCellFactory();
        setMarketColumnCellFactory();
        setRoleColumnCellFactory();
        setAvatarColumnCellFactory();
        setChatColumnCellFactory();
        setRemoveTradeColumnCellFactory();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.openTrades"))));
        tableView.setMinHeight(100);

        tradeIdColumn.setComparator(Comparator.comparing(o -> o.getTrade().getId()));
        dateColumn.setComparator(Comparator.comparing(o -> o.getTrade().getDate()));
        volumeColumn.setComparator(Comparator.comparing(o -> o.getTrade().getVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
        amountColumn.setComparator(Comparator.comparing(o -> o.getTrade().getAmount(), Comparator.nullsFirst(Comparator.naturalOrder())));
        priceColumn.setComparator(Comparator.comparing(PendingTradesListItem::getPrice));
        paymentMethodColumn.setComparator(Comparator.comparing(
                item -> item.getTrade().getOffer() != null ?
                        Res.get(item.getTrade().getOffer().getPaymentMethod().getId()) :
                        null,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        marketColumn.setComparator(Comparator.comparing(PendingTradesListItem::getMarketDescription));
        roleColumn.setComparator(Comparator.comparing(model::getMyRole));
        avatarColumn.setComparator(Comparator.comparing(
                o -> model.getNumPastTrades(o.getTrade()),
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        tableView.setRowFactory(
                tableView -> {
                    final TableRow<PendingTradesListItem> row = new TableRow<>();
                    final ContextMenu rowMenu = new ContextMenu();
                    MenuItem duplicateItem = new MenuItem(Res.get("portfolio.context.offerLikeThis"));
                    duplicateItem.setOnAction((event) -> {
                        try {
                            OfferPayload offerPayload = row.getItem().getTrade().getOffer().getOfferPayload().orElseThrow();
                            if (offerPayload.getPubKeyRing().equals(keyRing.getPubKeyRing())) {
                                PortfolioUtil.duplicateOffer(navigation, offerPayload);
                            } else {
                                new Popup().warning(Res.get("portfolio.context.notYourOffer")).show();
                            }
                        } catch (NullPointerException e) {
                            log.warn("Unable to get offerPayload - {}", e.toString());
                        }
                    });
                    rowMenu.getItems().add(duplicateItem);
                    row.contextMenuProperty().bind(
                            Bindings.when(Bindings.isNotNull(row.itemProperty()))
                                    .then(rowMenu)
                                    .otherwise((ContextMenu) null));
                    return row;
                });

        // we use a hidden emergency shortcut to open support ticket
        keyEventEventHandler = keyEvent -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.O, keyEvent)) {
                Popup popup = new Popup();
                popup.headLine(Res.get("portfolio.pending.openSupportTicket.headline"))
                        .message(Res.get("portfolio.pending.openSupportTicket.msg"))
                        .actionButtonText(Res.get("portfolio.pending.openSupportTicket.headline"))
                        .onAction(model.dataModel::onOpenSupportTicket)
                        .closeButtonText(Res.get("shared.cancel"))
                        .onClose(popup::hide)
                        .show();
            }
        };

        tradesListChangeListener = c -> onListChanged();

        getMempoolStatusListener = (observable, oldValue, newValue) -> {
            // -1 status is unknown
            // 0 status is FAIL
            // 1 status is PASS
            if (newValue.longValue() >= 0) {
                log.info("Taker fee validation returned {}", newValue.longValue());
            }
        };
    }

    @Override
    protected void activate() {
        ObservableList<PendingTradesListItem> list = model.dataModel.list;
        filteredList = new FilteredList<>(list);
        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        filterBox.initialize(filteredList, tableView); // here because filteredList is instantiated here
        filterBox.activate();

        updateMoveTradeToFailedColumnState();

        scene = root.getScene();
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }

        selectedItemSubscription = EasyBind.subscribe(model.dataModel.selectedItemProperty, selectedItem -> {
            if (selectedItem != null) {
                if (selectedSubView != null)
                    selectedSubView.deactivate();

                if (selectedItem.getTrade() != null) {
                    selectedSubView = model.dataModel.tradeManager.isBuyer(model.dataModel.getOffer()) ?
                            new BuyerSubView(model) : new SellerSubView(model);

                    selectedSubView.setMinHeight(460);
                    VBox.setVgrow(selectedSubView, Priority.ALWAYS);
                    if (root.getChildren().size() == 2)
                        root.getChildren().add(selectedSubView);
                    else if (root.getChildren().size() == 3)
                        root.getChildren().set(2, selectedSubView);

                    // create and register a callback so we can be notified when the subview
                    // wants to open the chat window
                    ChatCallback chatCallback = this::openChat;
                    selectedSubView.setChatCallback(chatCallback);
                }

                updateTableSelection();
            } else {
                removeSelectedSubView();
            }

            model.onSelectedItemChanged(selectedItem);

            if (selectedSubView != null && selectedItem != null)
                selectedSubView.activate();
        });

        selectedTableItemSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(),
                selectedItem -> {
                    if (selectedItem != null && !selectedItem.equals(model.dataModel.selectedItemProperty.get()))
                        model.dataModel.onSelectItem(selectedItem);
                });

        updateTableSelection();

        list.addListener(tradesListChangeListener);
        updateNewChatMessagesByTradeMap();
        model.getMempoolStatus().addListener(getMempoolStatusListener);
    }

    @Override
    protected void deactivate() {
        filterBox.deactivate();
        sortedList.comparatorProperty().unbind();
        selectedItemSubscription.unsubscribe();
        selectedTableItemSubscription.unsubscribe();

        removeSelectedSubView();

        model.dataModel.list.removeListener(tradesListChangeListener);
        model.getMempoolStatus().removeListener(getMempoolStatusListener);

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    private void removeSelectedSubView() {
        if (selectedSubView != null) {
            selectedSubView.deactivate();
            root.getChildren().remove(selectedSubView);
            selectedSubView = null;
        }
    }

    private void updateMoveTradeToFailedColumnState() {
        moveTradeToFailedColumn.setVisible(model.dataModel.list.stream().anyMatch(item -> isMaybeInvalidTrade(item.getTrade())));
    }

    private boolean isMaybeInvalidTrade(Trade trade) {
        return trade.hasErrorMessage() ||
                (Trade.Phase.DEPOSIT_PUBLISHED.ordinal() <= trade.getTradePhase().ordinal() && trade.isTxChainInvalid());
    }

    private void onMoveInvalidTradeToFailedTrades(Trade trade) {
        String msg = trade.isTxChainInvalid() ?
                Res.get("portfolio.pending.failedTrade.txChainInvalid.moveToFailed",
                        getInvalidTradeDetails(trade)) :
                Res.get("portfolio.pending.failedTrade.txChainValid.moveToFailed",
                        getInvalidTradeDetails(trade));
        new Popup().width(900).attention(msg)
                .onAction(() -> {
                    model.dataModel.onMoveInvalidTradeToFailedTrades(trade);
                    updateMoveTradeToFailedColumnState();
                })
                .actionButtonText(Res.get("shared.yes"))
                .closeButtonText(Res.get("shared.no"))
                .show();
    }

    private void onShowInfoForInvalidTrade(Trade trade) {
        new Popup().width(900).attention(Res.get("portfolio.pending.failedTrade.info.popup",
                getInvalidTradeDetails(trade)))
                .show();
    }

    private String getInvalidTradeDetails(Trade trade) {
        Contract contract = trade.getContract();
        if (contract == null) {
            return Res.get("portfolio.pending.failedTrade.missingContract");
        }

        PubKeyRing myPubKeyRing = model.dataModel.getPubKeyRing();
        boolean isMyRoleBuyer = contract.isMyRoleBuyer(myPubKeyRing);
        boolean isMyRoleMaker = contract.isMyRoleMaker(myPubKeyRing);

        if (trade.getTakerFeeTxId() == null) {
            return isMyRoleMaker ?
                    Res.get("portfolio.pending.failedTrade.maker.missingTakerFeeTx") :
                    Res.get("portfolio.pending.failedTrade.taker.missingTakerFeeTx");
        }

        if (trade.getDepositTx() == null) {
            return Res.get("portfolio.pending.failedTrade.missingDepositTx");
        }

        if (trade.getDelayedPayoutTx() == null) {
            return isMyRoleBuyer ?
                    Res.get("portfolio.pending.failedTrade.buyer.existingDepositTxButMissingDelayedPayoutTx") :
                    Res.get("portfolio.pending.failedTrade.seller.existingDepositTxButMissingDelayedPayoutTx");
        }

        if (trade.hasErrorMessage()) {
            return Res.get("portfolio.pending.failedTrade.errorMsgSet", trade.getErrorMessage());
        }

        return Res.get("shared.na");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chat
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateNewChatMessagesByTradeMap() {
        model.dataModel.list.forEach(t -> {
            Trade trade = t.getTrade();
            newChatMessagesByTradeMap.put(trade.getId(),
                    trade.getChatMessages().stream()
                            .filter(m -> !m.isWasDisplayed())
                            .filter(m -> !m.isSystemMessage())
                            .count());
        });
    }

    private void openChat(Trade trade) {
        if (chatPopupStage != null)
            chatPopupStage.close();

        TraderChatManager traderChatManager = model.dataModel.getTraderChatManager();
        if (trade.getChatMessages().isEmpty()) {
            traderChatManager.addSystemMsg(trade);
        }

        trade.getChatMessages().forEach(m -> m.setWasDisplayed(true));
        model.dataModel.getTradeManager().requestPersistence();
        tradeIdOfOpenChat = trade.getId();

        ChatView chatView = new ChatView(traderChatManager, Res.get("offerbook.trader"));
        chatView.setAllowAttachments(false);
        chatView.setDisplayHeader(false);
        chatView.initialize();

        AnchorPane pane = new AnchorPane(chatView);
        pane.setPrefSize(760, 500);
        AnchorPane.setLeftAnchor(chatView, 10d);
        AnchorPane.setRightAnchor(chatView, 10d);
        AnchorPane.setTopAnchor(chatView, -20d);
        AnchorPane.setBottomAnchor(chatView, 10d);

        boolean isTaker = !model.dataModel.isMaker(trade.getOffer());
        TradeChatSession tradeChatSession = new TradeChatSession(trade, isTaker);

        disputeStateListener = (observable, oldValue, newValue) -> {
            if (newValue == Trade.DisputeState.DISPUTE_CLOSED || newValue == Trade.DisputeState.REFUND_REQUEST_CLOSED) {
                chatPopupStage.hide();
            }
        };
        trade.disputeStateProperty().addListener(disputeStateListener);

        mediationResultStateListener = (observable, oldValue, newValue) -> {
            if (newValue == MediationResultState.PAYOUT_TX_PUBLISHED ||
                    newValue == MediationResultState.RECEIVED_PAYOUT_TX_PUBLISHED_MSG ||
                    newValue == MediationResultState.PAYOUT_TX_SEEN_IN_NETWORK) {
                chatPopupStage.hide();
            }
        };
        trade.mediationResultStateProperty().addListener(mediationResultStateListener);

        chatView.display(tradeChatSession, pane.widthProperty());

        chatView.activate();
        chatView.scrollToBottom();

        chatPopupStage = new Stage();
        chatPopupStage.setTitle(Res.get("tradeChat.chatWindowTitle", trade.getShortId()));
        StackPane owner = MainView.getRootContainer();
        Scene rootScene = owner.getScene();
        chatPopupStage.initOwner(rootScene.getWindow());
        chatPopupStage.initModality(Modality.NONE);
        chatPopupStage.initStyle(StageStyle.DECORATED);
        chatPopupStage.setOnHiding(event -> {
            chatView.deactivate();
            // at close we set all as displayed. While open we ignore updates of the numNewMsg in the list icon.
            trade.getChatMessages().forEach(m -> m.setWasDisplayed(true));
            model.dataModel.getTradeManager().requestPersistence();
            tradeIdOfOpenChat = null;

            if (xPositionListener != null) {
                chatPopupStage.xProperty().removeListener(xPositionListener);
            }
            if (yPositionListener != null) {
                chatPopupStage.xProperty().removeListener(yPositionListener);
            }

            trade.disputeStateProperty().addListener(disputeStateListener);
            trade.mediationResultStateProperty().addListener(mediationResultStateListener);
            traderChatManager.requestPersistence();
        });

        Scene scene = new Scene(pane);
        CssTheme.loadSceneStyles(scene, preferences.getCssTheme(), useDevModeHeader);
        scene.addEventHandler(KeyEvent.KEY_RELEASED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                ev.consume();
                chatPopupStage.hide();
            }
        });
        chatPopupStage.setScene(scene);

        chatPopupStage.setOpacity(0);
        chatPopupStage.show();

        xPositionListener = (observable, oldValue, newValue) -> chatPopupStageXPosition = (double) newValue;
        chatPopupStage.xProperty().addListener(xPositionListener);
        yPositionListener = (observable, oldValue, newValue) -> chatPopupStageYPosition = (double) newValue;
        chatPopupStage.yProperty().addListener(yPositionListener);

        if (chatPopupStageXPosition == -1) {
            Window rootSceneWindow = rootScene.getWindow();
            double titleBarHeight = rootSceneWindow.getHeight() - rootScene.getHeight();
            chatPopupStage.setX(Math.round(rootSceneWindow.getX() + (owner.getWidth() - chatPopupStage.getWidth() / 4 * 3)));
            chatPopupStage.setY(Math.round(rootSceneWindow.getY() + titleBarHeight + (owner.getHeight() - chatPopupStage.getHeight() / 4 * 3)));
        } else {
            chatPopupStage.setX(chatPopupStageXPosition);
            chatPopupStage.setY(chatPopupStageYPosition);
        }

        // Delay display to next render frame to avoid that the popup is first quickly displayed in default position
        // and after a short moment in the correct position
        UserThread.execute(() -> chatPopupStage.setOpacity(1));
        updateChatMessageCount(trade, badgeByTrade.get(trade.getId()));
    }

    private void updateChatMessageCount(Trade trade, JFXBadge badge) {
        if (!trade.getId().equals(tradeIdOfOpenChat)) {
            updateNewChatMessagesByTradeMap();
            long num = newChatMessagesByTradeMap.get(trade.getId());
            if (num > 0) {
                badge.setText(String.valueOf(num));
                badge.setEnabled(true);
            } else {
                badge.setText("");
                badge.setEnabled(false);
            }
        } else {
            badge.setText("");
            badge.setEnabled(false);
        }
        badge.refreshBadge();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateTableSelection() {
        PendingTradesListItem selectedItemFromModel = model.dataModel.selectedItemProperty.get();
        if (selectedItemFromModel != null) {
            // Select and focus selectedItem from model
            int index = tableView.getItems().indexOf(selectedItemFromModel);
            UserThread.execute(() -> tableView.getSelectionModel().select(index));
        }
    }

    private void onListChanged() {
        updateNewChatMessagesByTradeMap();
        updateMoveTradeToFailedColumnState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((pendingTradesListItem) -> new ReadOnlyObjectWrapper<>(pendingTradesListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem,
                            PendingTradesListItem> column) {
                        return new TableCell<>() {
                            private Trade trade;
                            private ChangeListener<Trade.State> listener;

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    trade = item.getTrade();
                                    listener = (observable, oldValue, newValue) -> update();
                                    trade.stateProperty().addListener(listener);
                                    update();
                                } else {
                                    setGraphic(null);
                                    if (trade != null && listener != null) {
                                        trade.stateProperty().removeListener(listener);
                                        trade = null;
                                        listener = null;
                                    }
                                }
                            }

                            private void update() {
                                HyperlinkWithIcon field;
                                if (trade == null) return;

                                if (isMaybeInvalidTrade(trade)) {
                                    field = new HyperlinkWithIcon(trade.getShortId());
                                    field.setIcon(FormBuilder.getMediumSizeIcon(MaterialDesignIcon.ALERT_CIRCLE_OUTLINE));
                                    field.setOnAction(event -> tradeDetailsWindow.show(trade));
                                    field.setTooltip(new Tooltip(Res.get("tooltip.invalidTradeState.warning")));
                                    if (trade.isTxChainInvalid()) {
                                        field.getIcon().getStyleClass().addAll("icon", "error-icon");
                                    } else {
                                        field.getIcon().getStyleClass().addAll("icon", "warn-icon");
                                    }
                                } else {
                                    field = new HyperlinkWithIcon(trade.getShortId());
                                    field.setOnAction(event -> tradeDetailsWindow.show(trade));
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                }
                                setGraphic(field);
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(DisplayUtils.formatDateTime(item.getTrade().getDate())));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getAmountAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getPriceAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    try {
                                        String volume = VolumeUtil.formatVolumeWithCode(item.getTrade().getVolume());
                                        setGraphic(new AutoTooltipLabel(volume));
                                    } catch (Throwable ignore) {
                                        log.debug(ignore.toString()); // Stupidity to make Codacy happy
                                    }
                                } else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setPaymentMethodColumnCellFactory() {
        paymentMethodColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(item.getPaymentMethod()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getMarketDescription()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setRoleColumnCellFactory() {
        roleColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        roleColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(model.getMyRole(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<PendingTradesListItem, PendingTradesListItem> setAvatarColumnCellFactory() {
        avatarColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(PendingTradesListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (!empty && newItem != null) {
                                    final Trade trade = newItem.getTrade();
                                    final NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
                                    int numPastTrades = model.getNumPastTrades(trade);
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIconTrading(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            trade,
                                            preferences,
                                            model.accountAgeWitnessService,
                                            useDevPrivilegeKeys);
                                    setPadding(new Insets(1, 0, 0, 0));
                                    setGraphic(peerInfoIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return avatarColumn;
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<PendingTradesListItem, PendingTradesListItem> setChatColumnCellFactory() {
        chatColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        chatColumn.getStyleClass().addAll("last-column", "avatar-column");
        chatColumn.setSortable(false);
        chatColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(PendingTradesListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (!empty && newItem != null) {
                                    Trade trade = newItem.getTrade();
                                    String id = trade.getId();

                                    // We use maps for each trade to avoid multiple listener registrations when
                                    // switching views. With current implementation we avoid that but we do not
                                    // remove listeners when a trade is removed (completed) but that has no consequences
                                    // as we will not receive any message anyway from a closed trade. Supporting it
                                    // more correctly would require more effort and managing listener deactivation at
                                    // screen switches (currently we get the update called if we have selected another
                                    // view.
                                    Button button;
                                    if (!buttonByTrade.containsKey(id)) {
                                        button = FormBuilder.getIconButton(MaterialDesignIcon.COMMENT_MULTIPLE_OUTLINE);
                                        buttonByTrade.put(id, button);
                                        button.setTooltip(new Tooltip(Res.get("tradeChat.openChat")));
                                    } else {
                                        button = buttonByTrade.get(id);
                                    }

                                    JFXBadge badge;
                                    if (!badgeByTrade.containsKey(id)) {
                                        badge = new JFXBadge(button);
                                        badgeByTrade.put(id, badge);
                                        badge.setPosition(Pos.TOP_RIGHT);
                                    } else {
                                        badge = badgeByTrade.get(id);
                                    }

                                    button.setOnAction(e -> {
                                        tableView.getSelectionModel().select(this.getIndex());
                                        openChat(trade);
                                    });

                                    if (!listenerByTrade.containsKey(id)) {
                                        ListChangeListener<ChatMessage> listener = c -> updateChatMessageCount(trade, badge);
                                        listenerByTrade.put(id, listener);
                                        trade.getChatMessages().addListener(listener);
                                    }

                                    updateChatMessageCount(trade, badge);

                                    setGraphic(badge);
                                } else {
                                    setGraphic(null);
                                }
                            }

                        };
                    }
                });
        return chatColumn;
    }

    private void setRemoveTradeColumnCellFactory() {
        moveTradeToFailedColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        moveTradeToFailedColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem,
                            PendingTradesListItem> column) {
                        return new TableCell<>() {
                            private Trade trade;
                            private JFXButton warnIconButton, trashIconButton;
                            private ChangeListener<Trade.State> listener;

                            @Override
                            public void updateItem(PendingTradesListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (!empty && newItem != null) {
                                    trade = newItem.getTrade();
                                    listener = (observable, oldValue, newValue) -> update();
                                    trade.stateProperty().addListener(listener);
                                    update();
                                } else {
                                    cleanup();
                                }
                            }

                            private void update() {
                                if (isMaybeInvalidTrade(trade)) {
                                    Text warnIcon = FormBuilder.getMediumSizeIcon(MaterialDesignIcon.ALERT_CIRCLE_OUTLINE);
                                    Text trashIcon = FormBuilder.getMediumSizeIcon(MaterialDesignIcon.ARROW_RIGHT_BOLD_BOX_OUTLINE);
                                    if (trade.isTxChainInvalid()) {
                                        trashIcon.getStyleClass().addAll("icon", "error-icon");
                                        warnIcon.getStyleClass().addAll("icon", "error-icon");
                                    } else {
                                        trashIcon.getStyleClass().addAll("icon", "warn-icon");
                                        warnIcon.getStyleClass().addAll("icon", "warn-icon");
                                    }

                                    warnIconButton = new JFXButton("", warnIcon);
                                    warnIconButton.getStyleClass().add("hidden-icon-button");
                                    warnIconButton.setTooltip(new Tooltip(Res.get("portfolio.pending.failedTrade.warningIcon.tooltip")));
                                    warnIconButton.setOnAction(e -> onShowInfoForInvalidTrade(trade));

                                    trashIconButton = new JFXButton("", trashIcon);
                                    trashIconButton.getStyleClass().add("hidden-icon-button");
                                    trashIconButton.setTooltip(new Tooltip(Res.get("portfolio.pending.failedTrade.moveTradeToFailedIcon.tooltip")));
                                    trashIconButton.setOnAction(e -> onMoveInvalidTradeToFailedTrades(trade));

                                    HBox hBox = new HBox();
                                    hBox.setSpacing(0);
                                    hBox.getChildren().addAll(warnIconButton, trashIconButton);
                                    setGraphic(hBox);
                                } else {
                                    cleanup();
                                }

                                updateMoveTradeToFailedColumnState();
                            }

                            private void cleanup() {
                                if (warnIconButton != null) {
                                    warnIconButton.setOnAction(null);
                                }
                                if (trashIconButton != null) {
                                    trashIconButton.setOnAction(null);
                                }
                                if (listener != null && trade != null) {
                                    trade.stateProperty().removeListener(listener);
                                }
                                setGraphic(null);
                            }
                        };
                    }
                });
    }
}
