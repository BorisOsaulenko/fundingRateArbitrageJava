package com.boris.fundingarbitrage.util.coinvector

import java.util.concurrent.ConcurrentHashMap

class CoinVector<T> : MutableMap<String, T> by ConcurrentHashMap() {
    companion object {
        @JvmStatic
        fun <T> byDefaultValue(coins: Collection<String>, value: T): CoinVector<T> {
            return CoinVector<T>().apply {
                for (coin in coins) {
                    this[coin] = value
                }
            }
        }
    }

    fun <R> transform(op: (T, String?) -> R): CoinVector<R> {
        val result = CoinVector<R>()
        for ((key, value) in this) {
            result[key] = op(value, key)
        }
        return result
    }

    private fun <K> ensureSameKeys(other: CoinVector<K>) {
        if (this.keys.size != other.keys.size) {
            error("Should be same coins.")
        }

        for (key in this.keys) {
            if (!other.containsKey(key)) {
                error("Should be same coins.")
            }
        }
    }

    operator fun <T : Number> CoinVector<T>.plus(other: CoinVector<T>): CoinVector<Double> {
        val result = CoinVector<Double>()
        this.ensureSameKeys(other)

        for (key in this.keys) {
            val thisValue = this[key]
            val otherValue = other[key]
            if (thisValue == null || otherValue == null) error("Values are not allowed to be null on addition.")
            result[key] = (thisValue.toDouble() + otherValue.toDouble())
        }
        return result
    }

    operator fun <T : Number> CoinVector<T>.minus(other: CoinVector<T>): CoinVector<Double> {
        val result = CoinVector<Double>()
        this.ensureSameKeys(other)

        for (key in this.keys) {
            val thisValue = this[key]
            val otherValue = other[key]
            if (thisValue == null || otherValue == null) error("Values are not allowed to be null on addition.")
            result[key] = (thisValue.toDouble() - otherValue.toDouble())
        }
        return result
    }

    operator fun <T : Number> CoinVector<T>.times(other: CoinVector<T>): CoinVector<Double> {
        val result = CoinVector<Double>()
        this.ensureSameKeys(other)

        for (key in this.keys) {
            val thisValue = this[key]
            val otherValue = other[key]
            if (thisValue == null || otherValue == null) error("Values are not allowed to be null on addition.")
            result[key] = (thisValue.toDouble() * otherValue.toDouble())
        }
        return result
    }

    operator fun <T : Number> CoinVector<T>.div(other: CoinVector<T>): CoinVector<Double> {
        val result = CoinVector<Double>()
        this.ensureSameKeys(other)

        for (key in this.keys) {
            val thisValue = this[key]
            val otherValue = other[key]
            if (thisValue == null || otherValue == null) error("Values are not allowed to be null on addition.")
            result[key] = (thisValue.toDouble() / otherValue.toDouble())
        }

        return result
    }

    operator fun <T : Number> CoinVector<T>.unaryMinus(): CoinVector<Double> {
        return this.transform { value, _ -> -value.toDouble() }
    }

    fun <T : Comparable<T>> CoinVector<T>.compareEach(other: CoinVector<T>): CoinVector<Int> {
        val result = CoinVector<Int>()
        this.ensureSameKeys(other)
        for (key in this.keys) {
            val thisValue = this[key]
            val otherValue = other[key]

            if (thisValue == null || otherValue == null) error("Values are not allowed to be null on comparison.")
            result[key] = thisValue.compareTo(otherValue)
        }

        return result
    }

    fun filter(predicate: (T, String) -> Boolean): CoinVector<T> {
        return CoinVector<T>().apply {
            for ((key, value) in this@CoinVector) {
                if (predicate(value, key)) {
                    this[key] = value
                }
            }
        }
    }

    fun filter(predicate: (T) -> Boolean): CoinVector<T> {
        return CoinVector<T>().apply {
            for ((key, value) in this@CoinVector) {
                if (predicate(value)) {
                    this[key] = value
                }
            }
        }
    }

    fun sort(comparator: Comparator<T>): List<Map.Entry<String, T>> {
        val sortedEntries = this.entries.sortedWith { a, b -> comparator.compare(a.value, b.value) }
        return sortedEntries
    }

    fun sortDesc(comparator: Comparator<T>): List<Map.Entry<String, T>> {
        val sortedEntries = this.entries.sortedWith { a, b -> comparator.compare(b.value, a.value) }
        return sortedEntries
    }

    fun getMaxEntry(comparator: Comparator<T>): Map.Entry<String, T> =
        this.entries.maxWith { a, b -> comparator.compare(a.value, b.value) }

    fun getMinEntry(comparator: Comparator<T>): Map.Entry<String, T> =
        this.entries.minWith { a, b -> comparator.compare(a.value, b.value) }
}