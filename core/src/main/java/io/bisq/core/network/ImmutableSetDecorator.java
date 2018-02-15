package io.bisq.core.network;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

class ImmutableSetDecorator<T> extends AbstractSet<T> {
    private final Set<T> delegate;

    ImmutableSetDecorator(Set<T> delegate) {
        this.delegate = ImmutableSet.copyOf(delegate);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
