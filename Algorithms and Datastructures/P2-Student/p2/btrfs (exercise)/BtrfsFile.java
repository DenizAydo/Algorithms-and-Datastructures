package p2.btrfs;

import p2.storage.EmptyStorageView;
import p2.storage.Interval;
import p2.storage.Storage;
import p2.storage.StorageView;

import java.util.Arrays;
import java.util.List;

/**
 * A file in a Btrfs file system. it uses a B-tree to store the intervals that hold the file's data.
 */
public class BtrfsFile {

    /**
     * The storage in which the file is stored.
     */
    private final Storage storage;

    /**
     * The name of the file.
     */
    private final String name;

    /**
     * The degree of the B-tree.
     */
    private final int degree;

    private final int maxKeys;

    /**
     * The root node of the B-tree.
     */
    private BtrfsNode root;

    /**
     * The total size of the file.
     */
    private int size;

    /**
     * Creates a new {@link BtrfsFile} instance.
     *
     * @param name the name of the file.
     * @param storage the storage in which the file is stored.
     * @param degree the degree of the B-tree.
     */
    public BtrfsFile(String name, Storage storage, int degree) {
        this.name = name;
        this.storage = storage;
        this.degree = degree;
        maxKeys = 2 * degree - 1;
        root = new BtrfsNode(degree);
    }

    /**
     * Reads all data from the file.
     *
     * @return a {@link StorageView} containing all data that is stored in this file.
     */
    public StorageView readAll() {
        return readAll(root);
    }

    /**
     * Reads all data from the given node.
     *
     * @param node the node to read from.
     * @return a {@link StorageView} containing all data that is stored in this file.
     */
    private StorageView readAll(BtrfsNode node) {

        StorageView view = new EmptyStorageView(storage);

        for (int i = 0; i < node.size; i++) {
            // before i-th key and i-th child.

            // read from i-th child if it exists
            if (node.children[i] != null) {
                view = view.plus(readAll(node.children[i]));
            }

            Interval key = node.keys[i];

            // read from i-th key
            view = view.plus(storage.createView(new Interval(key.start(), key.length())));
        }

        // read from last child if it exists
        if (node.children[node.size] != null) {
            view = view.plus(readAll(node.children[node.size]));
        }

        return view;
    }

    /**
     * Reads the given amount of data from the file starting at the given start position.
     *
     * @param start the start position.
     * @param length the amount of data to read.
     * @return a {@link StorageView} containing the data that was read.
     */
    public StorageView read(int start, int length) {
        return read(start, length, root, 0, 0);
    }

    /**
     * Reads the given amount of data from the given node starting at the given start position.
     *
     * @param start the start position.
     * @param length the amount of data to read.
     * @param node the current node to read from.
     * @param cumulativeLength the cumulative length of the intervals that have been visited so far.
     * @param lengthRead the amount of data that has been read so far.
     * @return a {@link StorageView} containing the data that was read.
     */

    // Diese Methode liest Daten aus einem BtrfsNode-Objekt und erstellt eine StorageView, die die angeforderten Daten enthält.
// Die Lesevorgänge erfolgen basierend auf den Parametern "start" (Startposition), "length" (Länge der Daten),
// "node" (der aktuelle Knoten, aus dem gelesen wird), "cumulativeLength" (kumulative Länge der gelesenen Daten)
// und "lengthRead" (Anzahl der bereits gelesenen Daten).

    private StorageView read(int start, int length, BtrfsNode node, int cumulativeLength, int lengthRead) { // 0/4 points

        // Überprüfen, ob die angeforderte Länge bereits vollständig gelesen wurde.
        if (length <= lengthRead) {
            return new EmptyStorageView(storage);
        }

        // Erstellen einer neuen leeren StorageView, die die gelesenen Daten enthält.
        StorageView newView = new EmptyStorageView(storage);

        int i = 0;
        while (i < node.size) {
            Interval varKey = node.keys[i];

            // Überprüfen, ob der aktuelle Knoten ein Kindknoten hat und ob die kumulative Länge des Kindknotens
            // plus 1 plus die kumulative Länge der bereits gelesenen Daten kleiner als der Startwert ist.
            if (node.children[i] != null && node.childLengths[i] - 1 + cumulativeLength < start) {
                cumulativeLength += node.childLengths[i];

                // Überprüfen, ob die kumulative Länge minus 1 plus die Länge des aktuellen Intervalls größer als der Startwert ist.
                if (cumulativeLength - 1 + varKey.length() > start) {
                    // Berechnen der neuen Länge, die gelesen werden soll, basierend auf der verbleibenden Länge
                    // und der Länge des aktuellen Intervalls.
                    int newLength = Math.min(length - lengthRead, varKey.length());
                    lengthRead += newLength;
                    cumulativeLength += newLength;

                    // Erstellen einer neuen StorageView, die den Teil des Intervalls enthält, der gelesen wurde,
                    // und Hinzufügen dieser View zur bisherigen StorageView.
                    newView = newView.plus(storage.createView(new Interval(varKey.start(), newLength)));
                } else {
                    // Rekursiver Aufruf der read-Methode, um in den Kindknoten zu wechseln und weitere Daten zu lesen.
                    read(start + lengthRead, length, node.children[i], cumulativeLength, lengthRead);
                }
            }

            // Überprüfen, ob die kumulative Länge minus 1 plus die Länge des aktuellen Intervalls größer als der Startwert ist.
            if (cumulativeLength - 1 + varKey.length() > start) {
                // Berechnen der neuen Länge, die gelesen werden soll, basierend auf der verbleibenden Länge
                // und der Länge des aktuellen Intervalls.
                int newLength = Math.min(length - lengthRead, varKey.length());
                lengthRead += newLength;
                cumulativeLength += newLength;

                // Erstellen einer neuen StorageView, die den Teil des Intervalls enthält, der gelesen wurde,
                // und Hinzufügen dieser View zur bisherigen StorageView.
                newView = newView.plus(storage.createView(new Interval(varKey.start(), newLength)));
            } else {
                cumulativeLength += varKey.length();
            }
            i++;
        }

        // Überprüfen, ob der aktuelle Knoten einen letzten Kindknoten hat und rekursiv die read-Methode für den Kindknoten aufrufen.
        if (node.children[node.size] != null) {
            newView = newView.plus(read(start + lengthRead, length, node.children[node.size], cumulativeLength, lengthRead));
        }

        // Rückgabe der finalen StorageView, die die gelesenen Daten enthält.
        return newView;
    }


    /**
     * Insert the given data into the file starting at the given start position.
     *
     * @param start the start position.
     * @param intervals the intervals to write to.
     * @param data the data to write into the storage.
     */
    public void insert(int start, List<Interval> intervals, byte[] data) {

        // fill the intervals with the data
        int dataPos = 0;
        for (Interval interval : intervals) {
            storage.write(interval.start(), data, dataPos, interval.length());
            dataPos += interval.length();
        }

        size += data.length;

        int insertionSize = data.length;

        // findInsertionIndex assumes that the current node is not full
        if (root.isFull()) {
            split(new IndexedNodeLinkedList(null, root, 0));
        }

        insert(intervals, findInsertionPosition(new IndexedNodeLinkedList(
            null, root, 0), start, 0, insertionSize, null), insertionSize);

    }

    /**
     * Inserts the given data into the given leaf at the given index.
     *
     * @param intervals the intervals to insert.
     * @param indexedLeaf The node and index to insert at.
     * @param remainingLength the remaining length of the data to insert.
     */
    private void insert(List<Interval> intervals, IndexedNodeLinkedList indexedLeaf, int remainingLength) { // 0/3

        int insertPosition = indexedLeaf.index;
        BtrfsNode leaf = indexedLeaf.node;

        // Füge alle möglichen Intervalle in den aktuellen Knoten ein
        int i = 0;
        Interval intvl = intervals.get(i);
        while(i < intervals.size() && remainingLength >= intervals.get(i).length()) {
            // leaf.children[insertPosition] = intvl; - fügt intvl an gegebener Position in leafNode ein
            remainingLength -= intvl.length();
            i++;
        }
        intervals.subList(0, i).clear(); // Entferne eingefügte Intervalle aus Liste

        //Aktualisiere Länge Knotens
        // List<BtrfsNode> childList = Arrays.asList(leaf.children);
        // childList.add(insertPosition, intvl);
        // leaf.children = childList.toArray();

        //Splitte übrig gebliebene Intervalle
        if(!intervals.isEmpty()) {
            split(indexedLeaf);
        }

        // Aktualisiere Länge des rechten Knotens
        // indexedLeaf.parent.node.childLengths(indexedLeaf.parent.index + 1, remainingLength); (funktioniert nicht)
        // Rekursiver Aufruf
        insert(intervals, indexedLeaf.parent, remainingLength);
    }

    /**
     * Finds the leaf node and index at which new intervals should be inserted given a start position.
     * It ensures that the start position is not in the middle of an existing interval
     * and updates the childLengths of the visited nodes.
     *
     * @param indexedNode The current Position in the tree.
     * @param start The start position of the intervals to insert.
     * @param cumulativeLength The length of the intervals in the tree up to the current node and index.
     * @param insertionSize The total size of the intervals to insert.
     * @param splitKey The right half of the interval that had to be split to ensure that the start position
     *                 is not in the middle of an interval. It will be inserted once the leaf node is reached.
     *                 If no split was necessary, this is null.
     * @return The leaf node and index, as well as the path to it, at which the intervals should be inserted.
     */
    private IndexedNodeLinkedList findInsertionPosition(IndexedNodeLinkedList indexedNode,
                                                        int start,
                                                        int cumulativeLength,
                                                        int insertionSize,
                                                        Interval splitKey) {

        throw new UnsupportedOperationException("Not implemented yet"); // 0/4 - not implemented
    }


    private int getTotalLength(BtrfsNode node) {
        int sum = 0;
        int i = 0;
        while(i < node.size) {
            sum += node.keys[i].length();
            i++;
        }
        i = 0;
        while(i < node.children.length) {
            if (node.children[i] != null) {
                sum += node.childLengths[i];
            }
            i++;
        }
        return sum;
    }

    /**
     * Splits the given node at the given index.
     * The method ensures that the given indexedNode points to correct node and index after the split.
     *
     * @param indexedNode The node to split.
     */
    private void split(IndexedNodeLinkedList indexedNode) { // 4/4 points

        int newIndex = indexedNode.index;
        boolean right = newIndex >= degree;

        if(indexedNode.parent != null && indexedNode.parent.node.isFull()) {
            split(indexedNode.parent); //Check, ob parent voll ist
        }

        BtrfsNode newNode = new BtrfsNode(degree);
        Interval keyInMiddle = indexedNode.node.keys[degree - 1];
        indexedNode.node.keys[degree - 1] = null;
        newNode.size = degree - 1;

        int j = 0;
        do {
            newNode.keys[j] = indexedNode.node.keys[j + degree];
            indexedNode.node.keys[j + degree] = null;
            j++;
        } while(j < degree - 1); //Schlüssel wird in neue Knoten kopiert

        if(!indexedNode.node.isLeaf()) {
            int i = 0;
            while (i < degree) {
                newNode.children[i] = indexedNode.node.children[i + degree];
                newNode.childLengths[i] = indexedNode.node.childLengths[i + degree]; //Wenn Knoten kein Blatt ist, werden Kinder kopiert
                indexedNode.node.children[i + degree] = null;
                indexedNode.node.childLengths[i + degree] = 0;
                i++;
            }
        }
        indexedNode.node.size = degree - 1;

        if(indexedNode.node.equals(root)) {

            IndexedNodeLinkedList newSetRoot = new IndexedNodeLinkedList(null, new BtrfsNode(degree), 0);

            newSetRoot.node.keys[0] = keyInMiddle; //Mittlerer Schlüssel wird zum Root geführt

            newSetRoot.node.children[0] = indexedNode.node;
            newSetRoot.node.children[1] = newNode; //Kinder werden gesetzt

            newSetRoot.node.childLengths[0] = getTotalLength(indexedNode.node);
            newSetRoot.node.childLengths[1] = getTotalLength(newNode); //Länge der Kinder werden gesetzt;

            indexedNode.parent = newSetRoot; //newRoot als neuen Wurzel gesetzt

            root = newSetRoot.node;

        } else {

            int indexInParent = indexedNode.parent.index;

            for (int i = indexedNode.parent.node.size - 1; i >= indexInParent; i--) {
                indexedNode.parent.node.keys[i + 1] = indexedNode.parent.node.keys[i]; //Schlüssel werden verschoben
            }

            for (int i = indexedNode.parent.node.size; i >= indexInParent + 1; i--) {
                indexedNode.parent.node.children[i + 1] = indexedNode.parent.node.children[i];
                indexedNode.parent.node.childLengths[i + 1] = indexedNode.parent.node.childLengths[i]; //Kinder werden verschoben
            }

            indexedNode.parent.node.keys[indexInParent] = keyInMiddle;
            indexedNode.parent.node.childLengths[indexInParent] = getTotalLength(indexedNode.node);
            indexedNode.parent.node.children[indexInParent + 1] = newNode;
            indexedNode.parent.node.childLengths[indexInParent + 1] = getTotalLength(newNode);

        }
        indexedNode.parent.node.size =  indexedNode.parent.node.size + 1;

        if(right) {
            indexedNode.parent.index++;
            indexedNode.index -= degree;
            indexedNode.node = newNode;
        }
    }

    /**
     * Writes the given data to the given intervals and stores them in the file starting at the given start position.
     * This method will override existing data starting at the given start position.
     *
     * @param start the start position.
     * @param intervals the intervals to write to.
     * @param data the data to write into the storage.
     */
    public void write(int start, List<Interval> intervals, byte[] data) {
        throw new UnsupportedOperationException("Not implemented yet"); //TODO H4 a): remove if implemented
    }

    /**
     * Removes the given number of bytes starting at the given position from this file.
     *
     * @param start the start position of the bytes to remove
     * @param length the amount of bytes to remove
     */
    public void remove(int start, int length) {
        size -= length;
        int removed = remove(start, length, new IndexedNodeLinkedList(null, root, 0), 0, 0);

        // check if we have traversed the whole tree
        if (removed < length) {
            throw new IllegalArgumentException("start + length is out of bounds");
        } else if (removed > length) {
            throw new IllegalStateException("Removed more keys than wanted"); // sanity check
        }
    }

    /**
     * Removes the given number of bytes starting at the given position from the given node.
     *
     * @param start the start position of the bytes to remove
     * @param length the amount of bytes to remove
     * @param indexedNode the current node to remove from
     * @param cumulativeLength the length of the intervals up to the current node and index
     * @param removedLength the length of the intervals that have already been removed
     * @return the number of bytes that have been removed
     */
    private int remove(int start, int length, IndexedNodeLinkedList indexedNode, int cumulativeLength, int removedLength) {

        int initiallyRemoved = removedLength;
        boolean visitNextChild = true;

        // iterate over all children and keys
        for (; indexedNode.index < indexedNode.node.size; indexedNode.index++) {
            // before i-th child and i-th child.

            // check if we have removed enough
            if (removedLength > length) {
                throw new IllegalStateException("Removed more keys than wanted"); // sanity check
            } else if (removedLength == length) {
                return removedLength - initiallyRemoved;
            }

            // check if we have to visit the next child
            // we don't want to visit the child if we have already visited it but had to go back because the previous
            // key changed
            if (visitNextChild) {

                // remove from i-th child if start is in front of or in the i-th child, and it exists
                if (indexedNode.node.children[indexedNode.index] != null &&
                    start < cumulativeLength + indexedNode.node.childLengths[indexedNode.index]) {

                    // remove from child
                    final int removedInChild = remove(start, length,
                        new IndexedNodeLinkedList(indexedNode, indexedNode.node.children[indexedNode.index], 0),
                        cumulativeLength, removedLength);

                    // update removedLength
                    removedLength += removedInChild;

                    // update childLength of parent accordingly
                    indexedNode.node.childLengths[indexedNode.index] -= removedInChild;

                    // check if we have removed enough
                    if (removedLength == length) {
                        return removedLength - initiallyRemoved;
                    } else if (removedLength > length) {
                        throw new IllegalStateException("Removed more keys than wanted"); // sanity check
                    }
                }

                cumulativeLength += indexedNode.node.childLengths[indexedNode.index];
            } else {
                visitNextChild = true;
            }

            // get the i-th key
            Interval key = indexedNode.node.keys[indexedNode.index];

            // the key might not exist anymore
            if (key == null) {
                return removedLength - initiallyRemoved;
            }

            // if start is in the i-th key we just have to shorten the interval
            if (start > cumulativeLength && start < cumulativeLength + key.length()) {

                // calculate the new length of the key
                final int newLength = start - cumulativeLength;

                // update cumulativeLength before updating the key
                cumulativeLength += key.length();

                // update the key
                indexedNode.node.keys[indexedNode.index] = new Interval(key.start(), newLength);

                // update removedLength
                removedLength += key.length() - newLength;

                // continue with next key
                continue;
            }

            // if start is in front of or at the start of the i-th key we have to remove the key
            if (start <= cumulativeLength) {

                // if the key is longer than the length to be removed we just have to shorten the key
                if (key.length() > length - removedLength) {

                    final int newLength = key.length() - (length - removedLength);
                    final int newStart = key.start() + (key.length() - newLength);

                    // update the key
                    indexedNode.node.keys[indexedNode.index] = new Interval(newStart, newLength);

                    // update removedLength
                    removedLength += key.length() - newLength;

                    // we are done
                    return removedLength - initiallyRemoved;
                }

                // if we are in a leaf node we can just remove the key
                if (indexedNode.node.isLeaf()) {

                    ensureSize(indexedNode);

                    // move all keys after the removed key to the left
                    System.arraycopy(indexedNode.node.keys, indexedNode.index + 1,
                        indexedNode.node.keys, indexedNode.index, indexedNode.node.size - indexedNode.index - 1);

                    // remove (duplicated) last key
                    indexedNode.node.keys[indexedNode.node.size - 1] = null;

                    // update size
                    indexedNode.node.size--;

                    // update removedLength
                    removedLength += key.length();

                    // the next key moved one index to the left
                    indexedNode.index--;

                } else { // remove key from inner node

                    // try to replace with rightmost key of left child
                    if (indexedNode.node.children[indexedNode.index].size >= degree) {
                        final Interval removedKey = removeRightMostKey(new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index], 0));

                        // update childLength of current node
                        indexedNode.node.childLengths[indexedNode.index] -= removedKey.length();

                        // update key
                        indexedNode.node.keys[indexedNode.index] = removedKey;

                        // update removedLength
                        removedLength += key.length();

                        // try to replace with leftmost key of right child
                    } else if (indexedNode.node.children[indexedNode.index + 1].size >= degree) {
                        final Interval removedKey = removeLeftMostKey(new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index + 1], 0));

                        // update childLength of current node
                        indexedNode.node.childLengths[indexedNode.index + 1] -= removedKey.length();

                        // update key
                        indexedNode.node.keys[indexedNode.index] = removedKey;

                        // update removedLength
                        removedLength += key.length();

                        cumulativeLength += removedKey.length();

                        // we might have to remove the new key as well -> go back
                        indexedNode.index--;
                        visitNextChild = false; // we don't want to remove from the previous child again

                        continue;

                        // if both children have only degree - 1 keys we have to merge them and remove the key from the merged node
                    } else {

                        // save the length of the right child before merging because we have to add it to the
                        // cumulative length later
                        final int rightNodeLength = indexedNode.node.childLengths[indexedNode.index + 1];

                        ensureSize(indexedNode);

                        // merge the two children
                        mergeWithRightSibling(new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index], 0));

                        // remove the key from the merged node
                        int removedInChild = remove(start, length, new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index], degree - 1),
                            cumulativeLength, removedLength);

                        // update childLength of current node
                        indexedNode.node.childLengths[indexedNode.index] -= removedInChild;

                        // update removedLength
                        removedLength += removedInChild;

                        // add the right child to the cumulative length
                        cumulativeLength += rightNodeLength;

                        // merging with right child shifted the keys to the left -> we have to visit the previous key again
                        indexedNode.index--;
                        visitNextChild = false; // we don't want to remove from the previous child again
                    }

                }

            }

            // update cumulativeLength after visiting the i-th key
            cumulativeLength += key.length();

        } // only the last child is left

        // check if we have removed enough
        if (removedLength > length) {
            throw new IllegalStateException("Removed more keys than wanted"); // sanity check
        } else if (removedLength == length) {
            return removedLength - initiallyRemoved;
        }

        // remove from the last child if start is in front of or in the i-th child, and it exists
        if (indexedNode.node.children[indexedNode.node.size] != null &&
            start <= cumulativeLength + indexedNode.node.childLengths[indexedNode.node.size]) {

            // remove from child
            int removedInChild = remove(start, length, new IndexedNodeLinkedList(indexedNode,
                indexedNode.node.children[indexedNode.node.size], 0), cumulativeLength, removedLength);

            // update childLength of parent accordingly
            indexedNode.node.childLengths[indexedNode.node.size] -= removedInChild;

            // update removedLength
            removedLength += removedInChild;
        }

        return removedLength - initiallyRemoved;
    }

    /**
     * Removes the rightmost key of the given node if it is a leaf.
     * Otherwise, it will remove the rightmost key of the last child.
     *
     * @param indexedNode the node to remove the rightmost key from.
     * @return the removed key.
     */
    private Interval removeRightMostKey(IndexedNodeLinkedList indexedNode) { // 4/4

        Interval keyToRemove;

        if(indexedNode.node.isLeaf()) {

            ensureSize(indexedNode);
            keyToRemove = indexedNode.node.keys[indexedNode.node.size - 1];
            indexedNode.node.keys[indexedNode.node.size - 1] = null;
            indexedNode.node.size = indexedNode.node.size - 1;

        } else {

            int childCount = 0;
            int i = 0;
            while(i < indexedNode.node.children.length) {
                if( indexedNode.node.children[i] != null) {
                    childCount++;
                }
                i++;
            }

            IndexedNodeLinkedList childOfNode = new IndexedNodeLinkedList(indexedNode, indexedNode.node.children[childCount - 1],  indexedNode.node.children[childCount - 1].size - 1);

            childOfNode.parent.index = childCount - 1;
            keyToRemove = removeRightMostKey(childOfNode);
        }

        if(indexedNode.parent != null) {
            BtrfsNode parent = indexedNode.parent.node;
            int parentOfIndex = indexedNode.parent.index;
            parent.childLengths[parentOfIndex] = getTotalLength(indexedNode.node);
        }
        return keyToRemove;
    }

    /**
     * Removes the leftmost key of the given node if it is a leaf.
     * Otherwise, it will remove the leftmost key of the first child.
     *
     * @param indexedNode the node to remove the leftmost key from.
     * @return the removed key.
     */
    private Interval removeLeftMostKey(IndexedNodeLinkedList indexedNode) { // 4/4 (both removeRight and removeLeft have to work correctly)

        Interval keyToRemove;

        if(indexedNode.node.isLeaf()) {

            ensureSize(indexedNode);
            keyToRemove = indexedNode.node.keys[0];

            int i = 0;
            while(i < indexedNode.node.size - 1) {
                indexedNode.node.keys[i] = indexedNode.node.keys[i + 1];
                i++;
            }
            indexedNode.node.size =  indexedNode.node.size - 1;

        } else {
            IndexedNodeLinkedList childOfNode = new IndexedNodeLinkedList(indexedNode, indexedNode.node.children[0],  0);
            childOfNode.parent.index = 0;
            keyToRemove = removeLeftMostKey(childOfNode);
        }

        if(indexedNode.parent != null) {
            BtrfsNode parent = indexedNode.parent.node;
            int IndexOfParent = indexedNode.parent.index;
            parent.childLengths[IndexOfParent] = getTotalLength(indexedNode.node);
        }
        return keyToRemove;
    }

    /**
     * Ensures that the given node has at least degree keys if it is not the root.
     * If the node has less than degree keys, it will try to rotate a key from a sibling or merge with a sibling.
     *
     * @param indexedNode the node to ensure the size of.
     */
    private void ensureSize(IndexedNodeLinkedList indexedNode) { // 4/4

        if(indexedNode.node.size >= degree) {
            return;
        } else if(indexedNode.node.equals(root)) {
            return;
        }

        int IndexOfParent = indexedNode.parent.index;
        BtrfsNode leftSibling;
        BtrfsNode rightSibling = null;

        if (IndexOfParent < indexedNode.parent.node.children.length - 1) {
            rightSibling = indexedNode.parent.node.children[IndexOfParent + 1];
            if (rightSibling != null && rightSibling.size > degree - 1) {
                rotateFromRightSibling(indexedNode);
                return;
            }
        }

        if (IndexOfParent > 0) {
            leftSibling = indexedNode.parent.node.children[IndexOfParent - 1];
            if (leftSibling != null && leftSibling.size > degree - 1) {
                rotateFromLeftSibling(indexedNode);
                return;
            }
        }

        ensureSize(indexedNode.parent);

        if (rightSibling != null) {
            mergeWithRightSibling(indexedNode);
        } else {
            mergeWithLeftSibling(indexedNode);
        }

        if (indexedNode.parent.node.equals(root) && indexedNode.parent.node.size == 0) {
            root = indexedNode.node;
        }

    }

    /**
     * Merges the given node with its left sibling.
     * The method ensures that the given indexedNode points to correct node and index after the split.
     *
     * @param indexedNode the node to merge with its left sibling.
     */
    private void mergeWithLeftSibling(IndexedNodeLinkedList indexedNode) { // 3/3 (both mergeWith - methods have to work correctly for all points)

        int i = 0;
        int childCount = 0;
        int IndexOfParent = indexedNode.parent.index;
        Interval parentKey = indexedNode.parent.node.keys[IndexOfParent - 1];
        BtrfsNode leftChild = indexedNode.parent.node.children[IndexOfParent - 1];

        indexedNode.index += leftChild.size + 1;
        leftChild.keys[leftChild.size] = parentKey;
        leftChild.size = leftChild.size + 1;

        while (i < indexedNode.node.size) {
            leftChild.keys[leftChild.size] = indexedNode.node.keys[i];
            leftChild.size++;
            i++;
        }

        for(i = 0; i < indexedNode.node.children.length; i++) {
            if(indexedNode.node.children[i] != null){
                childCount++;
            }
        }

        i = 0;
        do {
            leftChild.children[childCount] = indexedNode.node.children[i];
            leftChild.childLengths[childCount] = indexedNode.node.childLengths[i];
            childCount++;
            i++;
        } while(i <= indexedNode.node.size);

        indexedNode.node.size = leftChild.size;
        System.arraycopy(leftChild.keys, 0, indexedNode.node.keys, 0, leftChild.size);
        System.arraycopy(leftChild.childLengths, 0, indexedNode.node.childLengths, 0, leftChild.children.length);
        System.arraycopy(leftChild.children, 0, indexedNode.node.children, 0, leftChild.children.length);

        for(i = IndexOfParent - 1; i < indexedNode.parent.node.size; i++){
            indexedNode.parent.node.keys[i] = indexedNode.parent.node.keys[i + 1];
        }

        i = IndexOfParent - 1;
        do {
            indexedNode.parent.node.children[i] = indexedNode.parent.node.children[i + 1];
            indexedNode.parent.node.childLengths[i] = indexedNode.parent.node.childLengths[i + 1];
            i++;
        } while(i <= indexedNode.parent.node.size);

        indexedNode.parent.index =  indexedNode.parent.index - 1;
        indexedNode.parent.node.size = indexedNode.parent.node.size - 1;
        indexedNode.parent.node.childLengths[IndexOfParent - 1] = getTotalLength(indexedNode.node);
    }

    /**
     * Merges the given node with its right sibling.
     * The method ensures that the given indexedNode points to correct node and index after the split.
     *
     * @param indexedNode the node to merge with its right sibling.
     */
    private void mergeWithRightSibling(IndexedNodeLinkedList indexedNode) { // 3/3 (both rotateFrom - methods have to work correctly for all points)

        int i = 0;
        int childCount = 0;
        int IndexOfParent = indexedNode.parent.index;
        Interval parentKey = indexedNode.parent.node.keys[IndexOfParent];

        BtrfsNode rightSibling = indexedNode.parent.node.children[IndexOfParent + 1];
        indexedNode.node.keys[indexedNode.node.size] = parentKey;
        indexedNode.node.size =  indexedNode.node.size + 1;

        while(i < rightSibling.size) {
            indexedNode.node.keys[indexedNode.node.size] = rightSibling.keys[i];
            indexedNode.node.size++;
            rightSibling.keys[i] = null;
            i++;
        }

        for(i = 0; i < indexedNode.node.children.length; i++) {
            if(indexedNode.node.children[i] != null) {
                childCount++;
            }
        }

        i = 0;
        do {
            indexedNode.node.childLengths[childCount] = rightSibling.childLengths[i];
            indexedNode.node.children[childCount] = rightSibling.children[i];
            rightSibling.childLengths[i] = 0;
            rightSibling.children[i] = null;
            childCount++;
            i++;
        } while(i <= rightSibling.size);


        rightSibling.size = 0;

        indexedNode.parent.node.childLengths[IndexOfParent] = getTotalLength(indexedNode.node);
        indexedNode.parent.node.children[IndexOfParent + 1] = null;
        indexedNode.parent.node.childLengths[IndexOfParent + 1] = 0;

        indexedNode.parent.node.keys[IndexOfParent] = null;
        for(i = IndexOfParent; i < indexedNode.parent.node.size; i++){
            indexedNode.parent.node.keys[i] = indexedNode.parent.node.keys[i + 1];
        }

        i = IndexOfParent + 1;
        do {
            indexedNode.parent.node.childLengths[i] = indexedNode.parent.node.childLengths[i + 1];
            indexedNode.parent.node.children[i] = indexedNode.parent.node.children[i + 1];
            i++;
        } while(i <= indexedNode.parent.node.size);

        indexedNode.parent.node.size = indexedNode.parent.node.size - 1;
    }

    /**
     * Rotates an interval from the left sibling via the parent to the given node.
     *
     * @param indexedNode the node to rotate to.
     */
    private void rotateFromLeftSibling(IndexedNodeLinkedList indexedNode) { // 4/4

        int IndexOfParent = indexedNode.parent.index;
        BtrfsNode leftSibling = indexedNode.parent.node.children[IndexOfParent - 1];
        Interval lastKeyOfLeftSibling = leftSibling.keys[leftSibling.size - 1];

        int childCount = 0;
        int i = 0;
        while(i < indexedNode.node.children.length) {
            if(indexedNode.node.children[i] != null) {
                childCount = i;
            }
            i++;
        }

        BtrfsNode lastChildOfLeftSibling = leftSibling.children[childCount + 1];
        int lengthOfLastChild = leftSibling.childLengths[childCount + 1];

        Interval parentKey = indexedNode.parent.node.keys[IndexOfParent - 1];
        indexedNode.parent.node.keys[IndexOfParent - 1] = lastKeyOfLeftSibling;

        for  (i = indexedNode.node.size - 1; i >= 0; i--) {
            indexedNode.node.keys[i + 1] = indexedNode.node.keys[i];
        }

        for (i = indexedNode.node.size; i >= 0; i--) {
            indexedNode.node.children[i + 1] = indexedNode.node.children[i];
            indexedNode.node.childLengths[i + 1] = indexedNode.node.childLengths[i];
        }

        indexedNode.node.children[0] = lastChildOfLeftSibling;
        indexedNode.node.childLengths[0] = lengthOfLastChild;
        leftSibling.children[childCount + 1] = null;
        leftSibling.childLengths[childCount + 1] = 0;

        indexedNode.node.keys[0] = parentKey;
        indexedNode.node.size = indexedNode.node.size +1;
        indexedNode.index = indexedNode.index + 1;

        leftSibling.keys[leftSibling.size - 1] = null;
        leftSibling.size = leftSibling.size - 1;

        indexedNode.parent.node.childLengths[IndexOfParent - 1] = getTotalLength(leftSibling);
        indexedNode.parent.node.childLengths[IndexOfParent] = getTotalLength(indexedNode.node);
    }

    /**
     * Rotates an interval from the right sibling via the parent to the given node.
     *
     * @param indexedNode the node to rotate to.
     */
    private void rotateFromRightSibling(IndexedNodeLinkedList indexedNode) { // 4/4

        int indexOfParent = indexedNode.parent.index;

        BtrfsNode siblingRight = indexedNode.parent.node.children[indexOfParent + 1]; // Zugriff auf den rechten Geschwisterknoten des übergeordneten Knotens
        BtrfsNode firstChildOfRightSibling = siblingRight.children[0]; // Zugriff auf das erste Kind des rechten Geschwisterknotens und den ersten Schlüssel des rechten Geschwisterknotens
        Interval firstKeyOfRightSibling = siblingRight.keys[0];
        int lengthOfFirstChild = siblingRight.childLengths[0];

        // Zählen der Kinder im aktuellen Knoten
        int childCount = 0;
        int i = 0;
        while(i < indexedNode.node.children.length) {
            if(indexedNode.node.children[i] != null){ // Solange es Kinder gibt, wird die Schleife nicht abgebrochen
                childCount++;
            }
            i++;
        }

        indexedNode.node.children[childCount] = firstChildOfRightSibling;
        indexedNode.node.childLengths[childCount] = lengthOfFirstChild;

        Interval parentKey = indexedNode.parent.node.keys[indexOfParent];
        indexedNode.parent.node.keys[indexOfParent] = firstKeyOfRightSibling;

        indexedNode.node.keys[indexedNode.node.size] = parentKey; // parentKey wird zum aktuellen Knoten hinzugefügt
        indexedNode.node.size =  indexedNode.node.size + 1;
        siblingRight.keys[0] = null;
        siblingRight.size =  siblingRight.size - 1;

        for (i = 0; i <  siblingRight.size; i++) {
            siblingRight.keys[i] =  siblingRight.keys[i + 1]; // Verschieben der restlichen Geschwisterknoten um 1 nach rechts/vorne
        }

        siblingRight.keys[ siblingRight.size] = null;

        i = 0;
        do{
            siblingRight.children[i] = siblingRight.children[i + 1];
            siblingRight.childLengths[i] =  siblingRight.childLengths[i + 1];
            i++;
        } while(i <=  siblingRight.size);

        siblingRight.children[ siblingRight.size + 1] = null;
        siblingRight.childLengths[ siblingRight.size + 1] = 0;

        indexedNode.parent.node.childLengths[indexOfParent] = getTotalLength(indexedNode.node);
        indexedNode.parent.node.childLengths[indexOfParent + 1] = getTotalLength(siblingRight);
    }

    /**
     * Checks if there are any adjacent intervals that are also point to adjacent bytes in the storage.
     * If there are such intervals, they are merged into a single interval.
     */
    public void shrink() {

        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Returns the size of the file.
     * This is the sum of the length of all intervals or the amount of bytes used in the storage.
     *
     * @return the size of the file.
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the name of the file.
     *
     * @return the name of the file.
     */
    public String getName() {
        return name;
    }
}
