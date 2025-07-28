package bisq.common.util;

import java.util.Optional;
import java.util.function.Supplier;

public class OptionalUtils {
    public static <R> Optional<R> optionalIf(boolean condition, Supplier<R> supplier) {
        return condition ? Optional.of(supplier.get()) : Optional.empty();
    }
}
