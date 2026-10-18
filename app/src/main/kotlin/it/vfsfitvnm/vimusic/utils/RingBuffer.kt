package it.vfsfitvnm.vimusic.utils

class RingBuffer<T>(val size: Int, private val init: (index: Int) -> T) {
    private val list = MutableList(size, init)

    private var index = 0

    fun getOrNull(index: Int): T? = list.getOrNull(index)

    fun firstOrNull(predicate: (T) -> Boolean): T? = list.firstOrNull(predicate)

    fun append(element: T) = list.set(index++ % size, element)

    fun clear() {
        for (i in 0 until size) list[i] = init(i)
    }
}
