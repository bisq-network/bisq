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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.encoding.canonical;

import com.google.common.annotations.VisibleForTesting;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reproduces the Java 11 HashMap iteration order used by the legacy DAO state hash path for string-keyed maps.
 */
public final class LegacyHashMapOrderMapEntryIterator<V> implements CanonicalMapEntryIterator<String, V> {
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int TREEIFY_THRESHOLD = 8;
    private static final int UNTREEIFY_THRESHOLD = 6;
    private static final int MIN_TREEIFY_CAPACITY = 64;

    @Override
    public Iterator<Map.Entry<String, V>> iterate(List<Map.Entry<String, V>> entries) {
        return toJava11HashMapIterationOrder(entries).entrySet().iterator();
    }

    @VisibleForTesting
    public static List<String> getJava11HashMapIterationOrder(Collection<String> keys) {
        List<Map.Entry<String, Boolean>> entries = keys.stream()
                .map(key -> new AbstractMap.SimpleImmutableEntry<>(key, Boolean.TRUE))
                .collect(Collectors.toList());
        return new ArrayList<>(toJava11HashMapIterationOrder(entries).keySet());
    }

    @VisibleForTesting
    public static <V> LinkedHashMap<String, V> toJava11HashMapIterationOrder(List<Map.Entry<String, V>> entries) {
        Java11HashMapIterationOrderBuilder<V> builder = new Java11HashMapIterationOrderBuilder<>();
        for (Map.Entry<String, V> entry : entries) {
            builder.put(entry.getKey(), entry.getValue());
        }
        return builder.toLinkedHashMap();
    }

    private static int spreadHash(int hash) {
        return hash ^ (hash >>> 16);
    }

    private static final class Java11HashMapIterationOrderBuilder<V> {
        private HashNode<V>[] table;
        private int threshold;
        private int size;

        void put(String key, V value) {
            Objects.requireNonNull(key, "Canonical map keys must not be null");

            int hash = spreadHash(key.hashCode());
            HashNode<V>[] tab = table;
            int tableLength;
            if (tab == null || (tableLength = tab.length) == 0) {
                tab = resize();
                tableLength = tab.length;
            }

            int index = (tableLength - 1) & hash;
            HashNode<V> first = tab[index];
            if (first == null) {
                tab[index] = new HashNode<>(hash, key, value, null);
            } else if (first instanceof TreeNode) {
                if (((TreeNode<V>) first).putTreeVal(tab, hash, key, value) != null) {
                    throw new IllegalArgumentException("Duplicate canonical map key " + key);
                }
            } else {
                HashNode<V> node = first;
                for (int binCount = 0; ; ++binCount) {
                    if (node.hash == hash && node.key.equals(key)) {
                        throw new IllegalArgumentException("Duplicate canonical map key " + key);
                    }

                    HashNode<V> next = node.next;
                    if (next == null) {
                        node.next = new HashNode<>(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1) {
                            treeifyBin(tab, hash);
                        }
                        break;
                    }
                    node = next;
                }
            }

            if (++size > threshold) {
                resize();
            }
        }

        LinkedHashMap<String, V> toLinkedHashMap() {
            LinkedHashMap<String, V> result = new LinkedHashMap<>();
            if (table == null) {
                return result;
            }

            for (HashNode<V> bucket : table) {
                for (HashNode<V> node = bucket; node != null; node = node.next) {
                    result.put(node.key, node.value);
                }
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private HashNode<V>[] resize() {
            HashNode<V>[] oldTable = table;
            int oldCapacity = oldTable == null ? 0 : oldTable.length;
            int oldThreshold = threshold;
            int newCapacity;
            int newThreshold = 0;

            if (oldCapacity > 0) {
                if (oldCapacity >= MAXIMUM_CAPACITY) {
                    threshold = Integer.MAX_VALUE;
                    return oldTable;
                } else if ((newCapacity = oldCapacity << 1) < MAXIMUM_CAPACITY &&
                        oldCapacity >= DEFAULT_INITIAL_CAPACITY) {
                    newThreshold = oldThreshold << 1;
                }
            } else {
                newCapacity = DEFAULT_INITIAL_CAPACITY;
                newThreshold = (int) (0.75f * DEFAULT_INITIAL_CAPACITY);
            }

            if (newThreshold == 0) {
                float calculatedThreshold = (float) newCapacity * 0.75f;
                newThreshold = newCapacity < MAXIMUM_CAPACITY && calculatedThreshold < (float) MAXIMUM_CAPACITY ?
                        (int) calculatedThreshold :
                        Integer.MAX_VALUE;
            }

            threshold = newThreshold;
            HashNode<V>[] newTable = (HashNode<V>[]) new HashNode[newCapacity];
            table = newTable;

            if (oldTable != null) {
                for (int index = 0; index < oldCapacity; ++index) {
                    HashNode<V> node = oldTable[index];
                    if (node != null) {
                        oldTable[index] = null;
                        if (node.next == null) {
                            newTable[node.hash & (newCapacity - 1)] = node;
                        } else if (node instanceof TreeNode) {
                            ((TreeNode<V>) node).split(newTable, index, oldCapacity);
                        } else {
                            splitListBucket(newTable, index, oldCapacity, node);
                        }
                    }
                }
            }

            return newTable;
        }

        private void splitListBucket(HashNode<V>[] newTable, int index, int oldCapacity, HashNode<V> node) {
            HashNode<V> loHead = null;
            HashNode<V> loTail = null;
            HashNode<V> hiHead = null;
            HashNode<V> hiTail = null;

            do {
                HashNode<V> next = node.next;
                if ((node.hash & oldCapacity) == 0) {
                    if (loTail == null) {
                        loHead = node;
                    } else {
                        loTail.next = node;
                    }
                    loTail = node;
                } else {
                    if (hiTail == null) {
                        hiHead = node;
                    } else {
                        hiTail.next = node;
                    }
                    hiTail = node;
                }
                node = next;
            } while (node != null);

            if (loTail != null) {
                loTail.next = null;
                newTable[index] = loHead;
            }
            if (hiTail != null) {
                hiTail.next = null;
                newTable[index + oldCapacity] = hiHead;
            }
        }

        private void treeifyBin(HashNode<V>[] tab, int hash) {
            int tableLength = tab.length;
            if (tableLength < MIN_TREEIFY_CAPACITY) {
                resize();
                return;
            }

            int index = (tableLength - 1) & hash;
            HashNode<V> node = tab[index];
            if (node == null) {
                return;
            }

            TreeNode<V> head = null;
            TreeNode<V> tail = null;
            do {
                TreeNode<V> treeNode = new TreeNode<>(node.hash, node.key, node.value, null);
                if (tail == null) {
                    head = treeNode;
                } else {
                    treeNode.prev = tail;
                    tail.next = treeNode;
                }
                tail = treeNode;
                node = node.next;
            } while (node != null);

            tab[index] = head;
            if (head != null) {
                head.treeify(tab);
            }
        }
    }

    private static class HashNode<V> {
        final int hash;
        final String key;
        final V value;
        HashNode<V> next;

        private HashNode(int hash, String key, V value, HashNode<V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private static final class TreeNode<V> extends HashNode<V> {
        private TreeNode<V> parent;
        private TreeNode<V> left;
        private TreeNode<V> right;
        private TreeNode<V> prev;
        private boolean red;

        private TreeNode(int hash, String key, V value, HashNode<V> next) {
            super(hash, key, value, next);
        }

        private TreeNode<V> root() {
            TreeNode<V> root = this;
            while (root.parent != null) {
                root = root.parent;
            }
            return root;
        }

        private HashNode<V> putTreeVal(HashNode<V>[] tab, int hash, String key, V value) {
            TreeNode<V> root = parent != null ? root() : this;
            TreeNode<V> node = root;
            while (true) {
                int direction;
                if (node.hash > hash) {
                    direction = -1;
                } else if (node.hash < hash) {
                    direction = 1;
                } else if (node.key.equals(key)) {
                    return node;
                } else {
                    direction = node.key.compareTo(key) > 0 ? -1 : 1;
                }

                TreeNode<V> parentNode = node;
                node = direction <= 0 ? node.left : node.right;
                if (node == null) {
                    HashNode<V> next = parentNode.next;
                    TreeNode<V> inserted = new TreeNode<>(hash, key, value, next);
                    if (direction <= 0) {
                        parentNode.left = inserted;
                    } else {
                        parentNode.right = inserted;
                    }
                    parentNode.next = inserted;
                    inserted.parent = parentNode;
                    inserted.prev = parentNode;
                    if (next != null) {
                        ((TreeNode<V>) next).prev = inserted;
                    }
                    moveRootToFront(tab, balanceInsertion(root, inserted));
                    return null;
                }
            }
        }

        private void treeify(HashNode<V>[] tab) {
            TreeNode<V> root = null;
            TreeNode<V> node = this;
            while (node != null) {
                TreeNode<V> next = (TreeNode<V>) node.next;
                node.left = null;
                node.right = null;
                if (root == null) {
                    node.parent = null;
                    node.red = false;
                    root = node;
                } else {
                    int hash = node.hash;
                    String key = node.key;
                    TreeNode<V> treeNode = root;
                    while (true) {
                        int direction;
                        if (treeNode.hash > hash) {
                            direction = -1;
                        } else if (treeNode.hash < hash) {
                            direction = 1;
                        } else {
                            direction = treeNode.key.compareTo(key) > 0 ? -1 : 1;
                        }

                        TreeNode<V> parentNode = treeNode;
                        treeNode = direction <= 0 ? treeNode.left : treeNode.right;
                        if (treeNode == null) {
                            node.parent = parentNode;
                            if (direction <= 0) {
                                parentNode.left = node;
                            } else {
                                parentNode.right = node;
                            }
                            root = balanceInsertion(root, node);
                            break;
                        }
                    }
                }
                node = next;
            }
            moveRootToFront(tab, root);
        }

        private void split(HashNode<V>[] newTable, int index, int bit) {
            TreeNode<V> loHead = null;
            TreeNode<V> loTail = null;
            TreeNode<V> hiHead = null;
            TreeNode<V> hiTail = null;
            int loCount = 0;
            int hiCount = 0;

            TreeNode<V> node = this;
            while (node != null) {
                TreeNode<V> next = (TreeNode<V>) node.next;
                node.next = null;
                if ((node.hash & bit) == 0) {
                    node.prev = loTail;
                    if (loTail == null) {
                        loHead = node;
                    } else {
                        loTail.next = node;
                    }
                    loTail = node;
                    ++loCount;
                } else {
                    node.prev = hiTail;
                    if (hiTail == null) {
                        hiHead = node;
                    } else {
                        hiTail.next = node;
                    }
                    hiTail = node;
                    ++hiCount;
                }
                node = next;
            }

            if (loHead != null) {
                if (loCount <= UNTREEIFY_THRESHOLD) {
                    newTable[index] = loHead.untreeify();
                } else {
                    newTable[index] = loHead;
                    if (hiHead != null) {
                        loHead.treeify(newTable);
                    }
                }
            }
            if (hiHead != null) {
                if (hiCount <= UNTREEIFY_THRESHOLD) {
                    newTable[index + bit] = hiHead.untreeify();
                } else {
                    newTable[index + bit] = hiHead;
                    if (loHead != null) {
                        hiHead.treeify(newTable);
                    }
                }
            }
        }

        private HashNode<V> untreeify() {
            HashNode<V> head = null;
            HashNode<V> tail = null;
            for (HashNode<V> node = this; node != null; node = node.next) {
                HashNode<V> replacement = new HashNode<>(node.hash, node.key, node.value, null);
                if (tail == null) {
                    head = replacement;
                } else {
                    tail.next = replacement;
                }
                tail = replacement;
            }
            return head;
        }

        private static <V> void moveRootToFront(HashNode<V>[] tab, TreeNode<V> root) {
            if (root == null || tab == null || tab.length == 0) {
                return;
            }

            int index = (tab.length - 1) & root.hash;
            TreeNode<V> first = (TreeNode<V>) tab[index];
            if (root != first) {
                HashNode<V> rootNext = root.next;
                tab[index] = root;
                TreeNode<V> rootPrev = root.prev;
                if (rootNext != null) {
                    ((TreeNode<V>) rootNext).prev = rootPrev;
                }
                if (rootPrev != null) {
                    rootPrev.next = rootNext;
                }
                if (first != null) {
                    first.prev = root;
                }
                root.next = first;
                root.prev = null;
            }
        }

        private static <V> TreeNode<V> balanceInsertion(TreeNode<V> root, TreeNode<V> node) {
            node.red = true;
            while (true) {
                TreeNode<V> parent = node.parent;
                if (parent == null) {
                    node.red = false;
                    return node;
                }
                TreeNode<V> grandParent = parent.parent;
                if (!parent.red || grandParent == null) {
                    return root;
                }

                if (parent == grandParent.left) {
                    TreeNode<V> uncle = grandParent.right;
                    if (uncle != null && uncle.red) {
                        uncle.red = false;
                        parent.red = false;
                        grandParent.red = true;
                        node = grandParent;
                    } else {
                        if (node == parent.right) {
                            root = rotateLeft(root, parent);
                            node = parent;
                            parent = node.parent;
                            grandParent = parent == null ? null : parent.parent;
                        }
                        if (parent != null) {
                            parent.red = false;
                            if (grandParent != null) {
                                grandParent.red = true;
                                root = rotateRight(root, grandParent);
                            }
                        }
                    }
                } else {
                    TreeNode<V> uncle = grandParent.left;
                    if (uncle != null && uncle.red) {
                        uncle.red = false;
                        parent.red = false;
                        grandParent.red = true;
                        node = grandParent;
                    } else {
                        if (node == parent.left) {
                            root = rotateRight(root, parent);
                            node = parent;
                            parent = node.parent;
                            grandParent = parent == null ? null : parent.parent;
                        }
                        if (parent != null) {
                            parent.red = false;
                            if (grandParent != null) {
                                grandParent.red = true;
                                root = rotateLeft(root, grandParent);
                            }
                        }
                    }
                }
            }
        }

        private static <V> TreeNode<V> rotateLeft(TreeNode<V> root, TreeNode<V> node) {
            TreeNode<V> right = node.right;
            if (right != null) {
                TreeNode<V> rightLeft = right.left;
                node.right = rightLeft;
                if (rightLeft != null) {
                    rightLeft.parent = node;
                }

                TreeNode<V> parent = node.parent;
                right.parent = parent;
                if (parent == null) {
                    root = right;
                    root.red = false;
                } else if (parent.left == node) {
                    parent.left = right;
                } else {
                    parent.right = right;
                }

                right.left = node;
                node.parent = right;
            }
            return root;
        }

        private static <V> TreeNode<V> rotateRight(TreeNode<V> root, TreeNode<V> node) {
            TreeNode<V> left = node.left;
            if (left != null) {
                TreeNode<V> leftRight = left.right;
                node.left = leftRight;
                if (leftRight != null) {
                    leftRight.parent = node;
                }

                TreeNode<V> parent = node.parent;
                left.parent = parent;
                if (parent == null) {
                    root = left;
                    root.red = false;
                } else if (parent.right == node) {
                    parent.right = left;
                } else {
                    parent.left = left;
                }

                left.right = node;
                node.parent = left;
            }
            return root;
        }
    }
}
