package com.danneu.reddit

import java.util.Collections

// Example:
//
//    val nodes: List<Iterable<Int>> = listOf(
//        listOf(1),
//        listOf(2),
//        listOf(3)
//    )
//
//    LazyIteratorChain.fromIterables(nodes).asSequence().toList() == listOf(1, 2, 3)

// A simpler version of apache-commons' LazyIteratorChain.
//
// When #iterator() has side-effects, LazyIteratorChain lets you wrap iterator-producers
// so that #iterator() is only called on them when it's their turn to produce the next iterator
// in the chain.
//
// For instance, I use this to lazily load comments in a reddit submission. If I instead
// eagerly call #iterator() on all comment nodes, then the user would not be able to consume
// comments until all of the LoadMore nodes had loaded.

abstract class LazyIteratorChain<T>: Iterator<T> {
    var currIter: Iterator<T> = Collections.emptyIterator()
    var count = 0

    abstract fun nextIterator(count: Int): Iterator<T>?

    override fun hasNext(): Boolean {
        while (true) {
            if (currIter.hasNext()) return true
            currIter = nextIterator(++count) ?: return false
        }
    }

    override fun next(): T = currIter.next()

    companion object {
        fun <T> fromIterables(ables: List<Iterable<T>>) = object : LazyIteratorChain<T>() {
            override fun nextIterator(count: Int) = ables.getOrNull(count - 1)?.iterator()
        }
    }
}
