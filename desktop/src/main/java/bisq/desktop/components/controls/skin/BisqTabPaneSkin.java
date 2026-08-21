package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqRippler;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Material-style tab pane skin built from scratch (mirrors jfoenix's JFXTabPaneSkin shape, not
 * its source). Skeleton:
 *
 *   ┌───────────────────────────────────────────────┐
 *   │ Market    Buy    Sell        ← header (HBox)  │
 *   │ ─────                       ← selected-line   │
 *   ├───────────────────────────────────────────────┤
 *   │                                               │
 *   │   selected tab's content                      │
 *   │                                               │
 *   └───────────────────────────────────────────────┘
 *
 * The selected-line slides + resizes to match the active tab. Tab clicks update the selection.
 */
public class BisqTabPaneSkin extends SkinBase<TabPane> {

    private static final double LINE_HEIGHT = 1;
    private static final Duration ANIM_DURATION = Duration.millis(220);

    private final HBox header = new HBox();
    private final StackPane contentArea = new StackPane();
    private final Region selectedLine = new Region();
    private final Timeline anim = new Timeline();
    private final Rectangle paneClip = new Rectangle();

    private final ListChangeListener<Tab> tabsListener;
    private final ChangeListener<Tab> selectionListener;
    private final ChangeListener<Tab> selectedClassListener;

    public BisqTabPaneSkin(TabPane tabPane) {
        super(tabPane);

        header.getStyleClass().add("tab-header-area");
        header.setSpacing(4);
        contentArea.getStyleClass().add("tab-content-area");
        selectedLine.getStyleClass().add("tab-selected-line");
        selectedLine.setManaged(false);
        selectedLine.setMouseTransparent(true);
        selectedLine.setPrefHeight(LINE_HEIGHT);
        // Region doesn't auto-resize unmanaged children when prefWidth changes — keep its
        // actual width in lock-step with prefWidth via a single listener.
        selectedLine.prefWidthProperty().addListener((o, ov, nv) ->
                selectedLine.resize(nv.doubleValue(), LINE_HEIGHT));

        // Content first (below), then header (on top), then the slider line over the header.
        getChildren().addAll(contentArea, header, selectedLine);

        // Clip the whole tab pane to its bounds (mirrors jfoenix's JFXTabPaneSkin). Combined with
        // the computeMinHeight override below, this lets an ancestor shrink the pane: the content
        // is clipped at the bottom instead of forcing the pane — and the surrounding chrome (main
        // nav, sub-tabs) — taller than the window and scrolling it off-screen.
        paneClip.widthProperty().bind(tabPane.widthProperty());
        paneClip.heightProperty().bind(tabPane.heightProperty());
        tabPane.setClip(paneClip);

        tabsListener = c -> {
            // (Un)wire content listeners on tabs added or removed.
            while (c.next()) {
                for (Tab t : c.getRemoved()) {
                    Object oldL = t.getProperties().remove("bisq.contentListener");
                    if (oldL instanceof ChangeListener<?>) {
                        @SuppressWarnings("unchecked")
                        ChangeListener<Node> cl = (ChangeListener<Node>) oldL;
                        t.contentProperty().removeListener(cl);
                    }
                }
                for (Tab t : c.getAddedSubList()) {
                    attachContentListener(t);
                }
            }
            rebuildHeaders();
            showSelectedContent();
        };
        tabPane.getTabs().addListener(tabsListener);
        for (Tab t : tabPane.getTabs()) attachContentListener(t);

        selectionListener = (o, oldT, newT) -> {
            showSelectedContent();
            Platform.runLater(this::syncLine);
        };
        tabPane.getSelectionModel().selectedItemProperty().addListener(selectionListener);

        // Single instance — was previously added inside rebuildHeaders, leaking one per rebuild.
        selectedClassListener = (o, oldT, newT) -> {
            for (Node n : header.getChildren()) {
                Label lab = findLabel(n);
                if (lab == null) continue;
                lab.getStyleClass().remove("selected");
                if (n.getUserData() == newT) lab.getStyleClass().add("selected");
            }
        };
        tabPane.getSelectionModel().selectedItemProperty().addListener(selectedClassListener);

        rebuildHeaders();
        showSelectedContent();
    }

    /** When a tab's content changes (e.g. bisq's Navigation sets it post-selection), refresh
     *  the display so the new content gets a Scene parent and views can resolve their TabPane. */
    private void attachContentListener(Tab tab) {
        ChangeListener<Node> contentChange = (o, oldC, newC) -> {
            if (tab == getSkinnable().getSelectionModel().getSelectedItem()) {
                showSelectedContent();
            }
        };
        tab.contentProperty().addListener(contentChange);
        tab.getProperties().put("bisq.contentListener", contentChange);
    }

    private void rebuildHeaders() {
        header.getChildren().clear();
        for (Tab tab : getSkinnable().getTabs()) {
            Label l = new Label(tab.getText() == null ? "" : tab.getText());
            l.getStyleClass().add("tab-label");
            l.setPadding(new javafx.geometry.Insets(8, 18, 8, 18));
            l.setCursor(Cursor.HAND);
            l.setUserData(tab);
            if (tab == getSkinnable().getSelectionModel().getSelectedItem()) {
                l.getStyleClass().add("selected");
            }
            // Ripple overlay sized to the label — clipped to label bounds so ring stays
            // inside the tab. Mounted as a StackPane wrapper so the ripple draws over text.
            Pane ripple = new Pane();
            ripple.setMouseTransparent(true);
            ripple.prefWidthProperty().bind(l.widthProperty());
            ripple.prefHeightProperty().bind(l.heightProperty());
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(l.widthProperty());
            clip.heightProperty().bind(l.heightProperty());
            ripple.setClip(clip);
            StackPane cell = new StackPane(l, ripple);
            cell.setUserData(tab);
            cell.setCursor(Cursor.HAND);
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
                Circle c = BisqRippler.pressAt(ripple, l, e.getX(), e.getY(),
                        BisqRippler.GREEN_TINT);
                cell.getProperties().put("bisq.ripple", c);
            });
            cell.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
                Object c = cell.getProperties().remove("bisq.ripple");
                if (c instanceof Circle circle) BisqRippler.release(ripple, circle);
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    getSkinnable().getSelectionModel().select(tab);
                }
            });
            header.getChildren().add(cell);
        }
        Platform.runLater(this::syncLine);
    }

    private Label findLabel(Node cell) {
        if (cell instanceof Label l) return l;
        if (cell instanceof StackPane sp) {
            for (Node child : sp.getChildren()) {
                if (child instanceof Label l) return l;
            }
        }
        return null;
    }

    private void showSelectedContent() {
        Tab tab = getSkinnable().getSelectionModel().getSelectedItem();
        if (tab == null) {
            contentArea.getChildren().clear();
            return;
        }
        Node content = tab.getContent();
        if (content == null) {
            contentArea.getChildren().clear();
        } else {
            contentArea.getChildren().setAll(content);
        }
    }

    private boolean firstSync = true;
    private int syncRetries = 0;
    private static final int MAX_SYNC_RETRIES = 16;
    private boolean layoutSyncPending = false;

    private void syncLine() { syncLine(true); }

    private void syncLine(boolean animate) {
        // Deferred via Platform.runLater from multiple sites — dispose() doesn't cancel those
        // queued callbacks. Bail out if the skin has since been detached.
        TabPane tabPane = getSkinnable();
        if (tabPane == null) return;
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Node cell = null;
        for (Node n : header.getChildren()) {
            if (n.getUserData() == selected) { cell = n; break; }
        }
        if (cell == null) return;

        // Force CSS + layout BEFORE reading bounds so a just-applied `.selected` style (which
        // can change font weight / width) is reflected in cell.getBoundsInParent().
        header.applyCss();
        header.layout();

        javafx.geometry.Bounds lb = cell.getBoundsInParent();
        double headerX = header.getLayoutX();
        double headerW = header.getWidth();
        double targetX = headerX + lb.getMinX();
        double y = header.getLayoutY() + header.getHeight() - LINE_HEIGHT;
        double targetW = lb.getWidth();
        // Clamp so the line can never extend past the header's right edge — covers any
        // transient bound miscalculation while header content settles.
        double headerRight = headerX + headerW;
        if (targetX + targetW > headerRight) targetW = Math.max(0, headerRight - targetX);
        if (targetW <= 0 || header.getHeight() <= 0) {
            // Header not laid out yet — retry a bounded number of times.
            if (syncRetries++ < MAX_SYNC_RETRIES) Platform.runLater(this::syncLine);
            return;
        }
        syncRetries = 0;
        selectedLine.setPrefHeight(LINE_HEIGHT);
        selectedLine.setLayoutY(y);
        // First paint: park the line on the left edge of the header with target width set,
        // then animate layoutX into position. Jfoenix-style: line slides FROM left INTO target.
        if (firstSync) {
            firstSync = false;
            selectedLine.setLayoutX(header.getLayoutX());
            selectedLine.setPrefWidth(targetW);
            selectedLine.resize(targetW, LINE_HEIGHT);
            anim.stop();
            anim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
                    new KeyValue(selectedLine.layoutXProperty(), targetX, Interpolator.EASE_BOTH)));
            anim.play();
            return;
        }
        if (!animate) {
            selectedLine.setLayoutX(targetX);
            selectedLine.setPrefWidth(targetW);
            selectedLine.resize(targetW, LINE_HEIGHT);
            return;
        }
        anim.stop();
        final double finalTargetX = targetX;
        final double finalTargetW = targetW;
        final double finalY = y;
        anim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
                new KeyValue(selectedLine.layoutXProperty(), finalTargetX, Interpolator.EASE_BOTH),
                new KeyValue(selectedLine.prefWidthProperty(), finalTargetW, Interpolator.EASE_BOTH),
                new KeyValue(selectedLine.layoutYProperty(), finalY, Interpolator.EASE_BOTH)));
        // On anim end, snap exactly to current bounds — handles any late layout change that
        // occurred during the animation (font load, content swap shifting headerH, etc.).
        anim.setOnFinished(e -> Platform.runLater(() -> syncLine(false)));
        anim.play();
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Only the header is mandatory; the content is clipped (paneClip) when space is tight, so
        // the pane can shrink to the header height. The default SkinBase implementation would add
        // the content's (large) min height, making the pane unshrinkable and pushing surrounding
        // chrome off-screen on resize.
        double headerH = Math.max(header.prefHeight(width), 32);
        return topInset + bottomInset + headerH;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double headerH = Math.max(header.prefHeight(w), 32);
        header.resizeRelocate(x, y, w, headerH);
        contentArea.resizeRelocate(x, y + headerH, w, Math.max(0, h - headerH));
        // Coalesce so a resize cascade enqueues a single sync, not one per layout pass.
        if (!layoutSyncPending) {
            layoutSyncPending = true;
            Platform.runLater(() -> {
                layoutSyncPending = false;
                syncLine(false);
            });
        }
    }

    @Override
    public void dispose() {
        TabPane tp = getSkinnable();
        if (tp != null) {
            paneClip.widthProperty().unbind();
            paneClip.heightProperty().unbind();
            tp.setClip(null);
            tp.getTabs().removeListener(tabsListener);
            tp.getSelectionModel().selectedItemProperty().removeListener(selectionListener);
            tp.getSelectionModel().selectedItemProperty().removeListener(selectedClassListener);
            // Detach per-tab content listeners we attached earlier (see attachContentListener).
            for (Tab t : tp.getTabs()) {
                Object l = t.getProperties().remove("bisq.contentListener");
                if (l instanceof ChangeListener<?>) {
                    @SuppressWarnings("unchecked")
                    ChangeListener<Node> cl = (ChangeListener<Node>) l;
                    t.contentProperty().removeListener(cl);
                }
            }
        }
        anim.stop();
        super.dispose();
    }
}
