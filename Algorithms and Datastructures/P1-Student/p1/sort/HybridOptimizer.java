package p1.sort;

import java.util.Arrays;

/**
 * Optimizes the {@link HybridSort} by trying to find the k-value with the lowest number of read and write operations..
 */
public class HybridOptimizer {

    /**
     * Optimizes the {@link HybridSort} by trying to find the k-value with the lowest number of read and write operations.
     * The method will try all k-values starting from and return the k-value with the lowest number of read and write operations.
     * It will stop once if found the first local minimum or reaches the maximum possible k-value for the size of the given array.
     *
     * @param hybridSort the {@link HybridSort} to optimize.
     * @param array the array to sort.
     * @return the k-value with the lowest number of read and write operations.
     * @param <T> the type of the elements to be sorted.
     */
    public static <T> int optimize(HybridSort<T> hybridSort, T[] array) { // 1/5 points
        int[] c = new int[array.length];

        for (int k = 0; k < array.length; k++) {
            ArraySortList<T> tempList = new ArraySortList<T>(array);
            hybridSort.setK(k);
            hybridSort.sort(tempList);
            c[k] = tempList.getReadCount() + tempList.getWriteCount();
        }
        int minIndex = 0;
        for (int k = 1; k < c.length; k++) {
            if (c[k] < c[minIndex]) {
                minIndex = k;
            }
        }
        return minIndex;
    }
}
