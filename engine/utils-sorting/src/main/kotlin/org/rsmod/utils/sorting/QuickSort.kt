package org.rsmod.utils.sorting

public object QuickSort {
    /** Equal pivot values move only at odd indices; preserve this tie ordering. */
    public fun <T> alternating(
        list: MutableList<T>,
        low: Int = 0,
        high: Int = list.size - 1,
        compare: (T, T) -> Int,
    ) {
        if (low >= high || list.isEmpty()) {
            return
        }
        val pivotIndex = (low + high) / 2
        val pivotValue = list[pivotIndex]

        list[pivotIndex] = list[high]
        list[high] = pivotValue

        var counter = low
        var loopIndex = low

        while (loopIndex < high) {
            if (compare(list[loopIndex], pivotValue) < (loopIndex and 0x1)) {
                val tmp = list[loopIndex]
                list[loopIndex] = list[counter]
                list[counter] = tmp
                counter++
            }
            loopIndex++
        }

        list[high] = list[counter]
        list[counter] = pivotValue

        if (low < counter - 1) {
            alternating(list, low, counter - 1, compare)
        }

        if (counter + 1 < high) {
            alternating(list, counter + 1, high, compare)
        }
    }

    public fun <T> alternating(
        arr: Array<T>,
        low: Int = 0,
        high: Int = arr.size - 1,
        compare: (T, T) -> Int,
    ) {
        if (low >= high || arr.isEmpty()) {
            return
        }
        val pivotIndex = (low + high) / 2
        val pivotValue = arr[pivotIndex]

        arr[pivotIndex] = arr[high]
        arr[high] = pivotValue

        var counter = low
        var loopIndex = low

        while (loopIndex < high) {
            if (compare(arr[loopIndex], pivotValue) < (loopIndex and 0x1)) {
                val tmp = arr[loopIndex]
                arr[loopIndex] = arr[counter]
                arr[counter] = tmp
                counter++
            }
            loopIndex++
        }

        arr[high] = arr[counter]
        arr[counter] = pivotValue

        if (low < counter - 1) {
            alternating(arr, low, counter - 1, compare)
        }

        if (counter + 1 < high) {
            alternating(arr, counter + 1, high, compare)
        }
    }

    /** Equal pivot values remain in the right partition. */
    public fun <T> lessThanZero(
        list: MutableList<T>,
        low: Int = 0,
        high: Int = list.size - 1,
        compare: (T, T) -> Int,
    ) {
        if (low >= high || list.isEmpty()) {
            return
        }
        val pivotIndex = (low + high) / 2
        val pivotValue = list[pivotIndex]

        list[pivotIndex] = list[high]
        list[high] = pivotValue

        var counter = low
        var loopIndex = low

        while (loopIndex < high) {
            if (compare(list[loopIndex], pivotValue) < 0) {
                val tmp = list[loopIndex]
                list[loopIndex] = list[counter]
                list[counter] = tmp
                counter++
            }
            loopIndex++
        }

        list[high] = list[counter]
        list[counter] = pivotValue

        if (low < counter - 1) {
            lessThanZero(list, low, counter - 1, compare)
        }

        if (counter + 1 < high) {
            lessThanZero(list, counter + 1, high, compare)
        }
    }

    public fun <T> lessThanZero(
        arr: Array<T>,
        low: Int = 0,
        high: Int = arr.size - 1,
        compare: (T, T) -> Int,
    ) {
        if (low >= high || arr.isEmpty()) {
            return
        }
        val pivotIndex = (low + high) / 2
        val pivotValue = arr[pivotIndex]

        arr[pivotIndex] = arr[high]
        arr[high] = pivotValue

        var counter = low
        var loopIndex = low

        while (loopIndex < high) {
            if (compare(arr[loopIndex], pivotValue) < 0) {
                val tmp = arr[loopIndex]
                arr[loopIndex] = arr[counter]
                arr[counter] = tmp
                counter++
            }
            loopIndex++
        }

        arr[high] = arr[counter]
        arr[counter] = pivotValue

        if (low < counter - 1) {
            lessThanZero(arr, low, counter - 1, compare)
        }

        if (counter + 1 < high) {
            lessThanZero(arr, counter + 1, high, compare)
        }
    }

    /** Equal pivot values move into the left partition. */
    public fun <T> lessThanOne(
        list: MutableList<T>,
        low: Int = 0,
        high: Int = list.size - 1,
        compare: (T, T) -> Int,
    ) {
        if (low >= high || list.isEmpty()) {
            return
        }
        val pivotIndex = (low + high) / 2
        val pivotValue = list[pivotIndex]

        list[pivotIndex] = list[high]
        list[high] = pivotValue

        var counter = low
        var loopIndex = low

        while (loopIndex < high) {
            if (compare(list[loopIndex], pivotValue) < 1) {
                val tmp = list[loopIndex]
                list[loopIndex] = list[counter]
                list[counter] = tmp
                counter++
            }
            loopIndex++
        }

        list[high] = list[counter]
        list[counter] = pivotValue

        if (low < counter - 1) {
            lessThanOne(list, low, counter - 1, compare)
        }

        if (counter + 1 < high) {
            lessThanOne(list, counter + 1, high, compare)
        }
    }

    public fun <T> lessThanOne(
        arr: Array<T>,
        low: Int = 0,
        high: Int = arr.size - 1,
        compare: (T, T) -> Int,
    ) {
        if (low >= high || arr.isEmpty()) {
            return
        }
        val pivotIndex = (low + high) / 2
        val pivotValue = arr[pivotIndex]

        arr[pivotIndex] = arr[high]
        arr[high] = pivotValue

        var counter = low
        var loopIndex = low

        while (loopIndex < high) {
            if (compare(arr[loopIndex], pivotValue) < 1) {
                val tmp = arr[loopIndex]
                arr[loopIndex] = arr[counter]
                arr[counter] = tmp
                counter++
            }
            loopIndex++
        }

        arr[high] = arr[counter]
        arr[counter] = pivotValue

        if (low < counter - 1) {
            lessThanOne(arr, low, counter - 1, compare)
        }

        if (counter + 1 < high) {
            lessThanOne(arr, counter + 1, high, compare)
        }
    }
}
