package com.danneu.reddit

import java.util.Collections


/**
 * A chain of iterators that act as a single iterator.
 *
 * This is a simpler version of apache-common's LazyIteratorChain. I had catastrophic performance when using
 * the apache-common's version.
 *
 * Example:
 *
 *     val nodes: List<Iterable<Int>> = listOf(
 *         listOf(1),
 *         listOf(2),
 *         listOf(3)
 *     )
 *
 *     LazyIteratorChain.fromIterables(nodes).asSequence().toList() == listOf(1, 2, 3)
 *
 * When #iterator() has side-effects, LazyIteratorChain lets you wrap iterator-producers
 * so that #iterator() is only called on them when it's their turn to produce the next iterator
 * in the chain.
 *
 * For instance, I use this to lazily load comments in a reddit submission. If I instead
 * eagerly called #iterator() on all comment nodes, then the user would not be able to consume
 * comments until all of the LoadMore nodes had loaded.
 */
abstract class LazyIteratorChain<T>: Iterator<T> {
    var currIter: Iterator<T> = Collections.emptyIterator()
    var count = 0

    /**
     * Produce the next iterator in the chain.
     *
     * The chain is finished when this method returns `null`.
     *
     * @param count the number of times `nextIterator` has been called. e.g. if count is 2, then return
     *              the 2nd iterator in the chain.
     */
    abstract fun nextIterator(count: Int): Iterator<T>?

    override fun hasNext(): Boolean {
        while (true) {
            if (currIter.hasNext()) return true
            currIter = nextIterator(++count) ?: return false
        }
    }

    override fun next(): T = currIter.next()

    companion object {
        /**
         * Chain together a list of iterator-producing objects.
         */
        fun <T> fromIterables(ables: List<Iterable<T>>) = object : LazyIteratorChain<T>() {
            override fun nextIterator(count: Int) = ables.getOrNull(count - 1)?.iterator()
        }
    }
}
