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

package bisq.bridge.grpc.services;

import io.grpc.stub.StreamObserver;

import java.util.function.Consumer;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ManagedStreamObserver<T> implements StreamObserver<T> {
    @EqualsAndHashCode.Include
    private final StreamObserver<T> delegate;

    private final Consumer<ManagedStreamObserver<T>> onErrorCallback;
    private final Consumer<ManagedStreamObserver<T>> onCompletedCallback;

    public ManagedStreamObserver(StreamObserver<T> delegate,
                                 Consumer<ManagedStreamObserver<T>> onErrorCallback,
                                 Consumer<ManagedStreamObserver<T>> onCompletedCallback) {
        this.delegate = delegate;
        this.onErrorCallback = onErrorCallback;
        this.onCompletedCallback = onCompletedCallback;
    }

    @Override
    public void onNext(T element) {
        delegate.onNext(element);
    }

    @Override
    public void onError(Throwable throwable) {
        delegate.onError(throwable);
        onErrorCallback.accept(this);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
        onCompletedCallback.accept(this);
    }
}
