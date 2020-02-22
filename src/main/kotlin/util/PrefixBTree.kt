package util

private sealed class Node<T : Comparable<T>>(val bucket: Array<T>)
private class Link<T : Comparable<T>>(bucket: Array<T>, val links: Array<Node<T>>) : Node<T>(bucket)
private class Leaf<T : Comparable<T>>(bucket: Array<T>, var next: Leaf<T>? = null) : Node<T>(bucket)

class BTreeSet<T : Comparable<T>>(val branchingFactor: Int) {

    fun add(value: T) {

    }

    private fun Node<T>.find(value: T): Node<T> =
        when (this) {
            is Leaf -> this
            is Link -> {
                TODO("In my wet dreams this may work")
            }
        }

}