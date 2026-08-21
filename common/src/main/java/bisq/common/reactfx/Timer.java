package bisq.common.reactfx;

/**
 * Timer represents a delayed action. This means that every timer has an
 * associated action and an associated delay. Action and delay are specified
 * on timer creation.
 *
 * <p>Every timer also has an associated thread (such as JavaFX application
 * thread or a single-thread executor's thread). Timer may only be accessed
 * from its associated thread. Timer's action is executed on its associated
 * thread, too. This design allows to implement guarantees provided by
 * {@link #stop()}.
 *
 * Copied from:
 * https://raw.githubusercontent.com/TomasMikula/ReactFX/537fffdbb2958a77dfbca08b712bb2192862e960/reactfx/src/main/java/org/reactfx/util/Timer.java*
 */
public interface Timer {
    /**
     * Schedules the associated action to be executed after the associated
     * delay. If the action is already scheduled but hasn't been executed yet,
     * the timeout is reset, so that the action won't be executed before the
     * full delay from now.
     */
    void restart();

    /**
     * If the associated action has been scheduled for execution but not yet
     * executed, this method prevents it from being executed at all. This is
     * also true in case the timer's timeout has already expired, but the
     * associated action hasn't had a chance to be executed on the associated
     * thread. Note that this is a stronger guarantee than the one given by
     * {@link javafx.animation.Animation#stop()}:
     *
     * <pre>
     * {@code
     * Timeline timeline = new Timeline(new KeyFrame(
     *         Duration.millis(1000),
     *         ae -> System.out.println("FIRED ANYWAY")));
     * timeline.play();
     *
     * // later on the JavaFX application thread,
     * // but still before the action has been executed
     * timeline.stop();
     *
     * // later, "FIRED ANYWAY" may still be printed
     * }
     * </pre>
     *
     * In contrast, using the {@link FxTimer}, the action is guaranteed not to
     * be executed after {@code stop()}:
     * <pre>
     * {@code
     * Timer timer = FxTimer.runLater(
     *         Duration.ofMillis(1000),
     *         () -> System.out.println("FIRED"));
     *
     * // later on the JavaFX application thread,
     * // but still before the action has been executed
     * timer.stop();
     *
     * // "FIRED" is guaranteed *not* to be printed
     * }
     * </pre>
     */
    void stop();
}
