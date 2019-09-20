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

package bisq.desktop.main.account.content.wallet.monero.tx;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import bisq.common.UserThread;
import bisq.core.locale.Res;
import bisq.core.xmr.XmrFormatter;
import bisq.core.xmr.wallet.XmrTxListItem;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.xmr.wallet.listeners.WalletUiListener;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

@FxmlView
public class XmrTxView extends ActivatableView<GridPane, Void> implements WalletUiListener {

    private TableView<XmrTxListItem> tableView;
    private final XmrWalletRpcWrapper walletWrapper;
    private final XmrFormatter xmrFormatter;
    private final ObservableList<XmrTxListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<XmrTxListItem> sortedList = new SortedList<>(observableList);
    private Button searchButton;
    private TextField searchTextField;
    private BusyAnimation busyAnimation = new BusyAnimation(false);
    private XmrTxProofWindow txProofWindow;

    @Inject
    private XmrTxView(XmrWalletRpcWrapper walletWrapper, XmrFormatter xmrFormatter) {
        this.walletWrapper = walletWrapper;
        this.xmrFormatter = xmrFormatter;
    }

    @Override
	public void initialize() {
    	if(!walletWrapper.isXmrWalletRpcRunning()) {
        	walletWrapper.openWalletRpcInstance(this);
    	}

    	root.setPadding(new Insets(10));
    	searchButton = new Button(Res.get("shared.search"));
    	searchButton.setOnAction((event) -> {
			try {
				if(searchTextField.getText() != null && !searchTextField.getText().isEmpty()) {
					walletWrapper.searchTx(XmrTxView.this, searchTextField.getText());
				}
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				new Popup<>().error(Res.get("mainView.networkWarning.localhostLost", "Monero")).show();
			} catch (Exception e) {
				e.printStackTrace();
				new Popup<>().error(Res.get("shared.account.wallet.popup.error.startupFailed")).show();
			}
        });

        VBox vbox = new VBox(5);
    	
    	HBox hbox = new HBox(5);
        searchTextField = new TextField();
        searchTextField.setPromptText(Res.get("shared.account.wallet.tx.searchPrompt"));
        searchTextField.setMinWidth(950);
        hbox.getChildren().addAll(searchTextField, searchButton, busyAnimation);
        GridPane.setColumnSpan(vbox, 3);
                
        root.add(vbox, 1, 1);
        GridPane.setConstraints(hbox, 1, 1, 1, 2, HPos.CENTER, VPos.CENTER);    	
	    tableView = new TableView<>();
	    tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
	    tableView.setEditable(false);
	    
	    addDateColumn();
	    addTxIdColumn();
	    addPaymentIdColumn();
	    addAmountColumn();
	    addDestinationColumn();
	    addDirectionColumn();
	    addConfirmedColumn();
	
        vbox.getChildren().addAll(hbox, tableView);
	}

    @Override
    protected void activate() {
	    try {
		    HashMap<String, Object> data = new HashMap<>();
		    data.put("getTxs", null);
			data.put("getBalance", null);
			data.put("getFee", null);
			walletWrapper.update(this, data);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			new Popup<>().error(Res.get("mainView.networkWarning.localhostLost", "Monero")).show();
		} catch (Exception e) {
			e.printStackTrace();
			new Popup<>().error(Res.get("shared.account.wallet.tx.item.account.wallet.popup.error.startupFailed")).show();
		}
	    
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(observableList);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
    }

    @SuppressWarnings("unchecked")
	@Override
    public void onUpdateBalances(HashMap<String, Object> walletRpcData) {
    	log.debug("onUpdateBalances => {}", walletRpcData.keySet());
    	List<XmrTxListItem> txList = (List<XmrTxListItem>) walletRpcData.get("getTxs");
    	if(txList != null) {
        	observableList.setAll(txList);
    	}
    }

    private void openTxInBlockExplorer(XmrTxListItem item) {
        if (item.getId() != null)
            GUIUtil.openWebPage("https://testnet.xmrchain.com/search?value=" + item.getId(), false);//TODO(niyid) Change from hardcoded URL
    }
    
    private void addDateColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.datetime"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(180);
        column.setMaxWidth(column.getMinWidth() + 20);
        column.getStyleClass().add("first-column");

        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(xmrFormatter.formatDateTime(item.getDate()));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
        column.setComparator(new Comparator<XmrTxListItem>() {
			@Override
			public int compare(XmrTxListItem o1, XmrTxListItem o2) {
				return o2.getDate().compareTo(o1.getDate());
			}
		});
        column.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(column);
    }

    private void addTxIdColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.txId"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(200);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, MaterialDesignIcon.LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }

    private void addPaymentIdColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.paymentId"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(200);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String paymentId = item.getPaymentId();
                                    setText(paymentId);
                                } else {
                                	setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }

    private void addDestinationColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.destination"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(150);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String address = item.getDestinationAddress();
                                    setText(address);
                                } else {
                                	setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }
    
    private void addDirectionColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.direction"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String direction = item.getDirection();
                                    setText(direction);
                                } else {
                                	setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }

    private void addConfirmedColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.confirmed"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {
                        	private HyperlinkWithIcon hyperlinkWithIcon;
                        	
                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String confirmed = item.isConfirmed() ? Res.get("shared.yes") : Res.get("shared.no");
                                    hyperlinkWithIcon = new HyperlinkWithIcon(confirmed, MaterialDesignIcon.TICKET_CONFIRMATION);
                                    hyperlinkWithIcon.setOnAction(e -> showTxProof(item.getId(), "Transaction at " + xmrFormatter.formatDateTime(item.getDate())));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openTxProof", item.getId())));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }

    private void addConfirmationsColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.confirmations"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                            XmrTxListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final XmrTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String confirmations = Long.toString(item.getConfirmations());
                                    setText(confirmations);
                                } else {
                                	setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }
    
    private void addAmountColumn() {
        TableColumn<XmrTxListItem, XmrTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.account.wallet.tx.item.amount", "XMR"));
        column.setMinWidth(120);
        column.setMaxWidth(column.getMinWidth());

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {

            @Override
            public TableCell<XmrTxListItem, XmrTxListItem> call(TableColumn<XmrTxListItem,
                    XmrTxListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final XmrTxListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            String xmrAmount = Res.get("shared.account.wallet.tx.item.na");

                            if (item.getConfirmations() > 0) {
                            	xmrAmount = xmrFormatter.formatBigInteger(item.getAmount());
                            }

                            setText(xmrAmount);
                        } else {
                            setText("");
                        }
                    }
                };
            }
        });
        tableView.getColumns().add(column);
    }

	@Override
	public void playAnimation() {
    	UserThread.execute(() -> {
    		busyAnimation.setVisible(true);
			busyAnimation.play();
        });
	}

	@Override
	public void stopAnimation() {
    	UserThread.execute(() -> {
			busyAnimation.setVisible(false);
			busyAnimation.stop();
        });
	}

	@Override
	public void popupErrorWindow(String resourceMessage) {
    	UserThread.execute(() -> {
			new Popup<>().error(resourceMessage).show();
        });
	}
	
	private void showTxProof(String txId, String message) {
        this.txProofWindow = new XmrTxProofWindow();
        this.txProofWindow.show();
        walletWrapper.handleTxProof(XmrTxView.this.txProofWindow, txId, message);
	}
}

