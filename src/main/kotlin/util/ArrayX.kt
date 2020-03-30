package util

inline fun <T, reified U> Array<T>.mapArray(transform: (value: T) -> U) =
    Array(size) { transform(this[it]) }

inline fun <T, reified U> List<T>.mapArray(transform: (value: T) -> U) =
    Array(size) { transform(this[it]) }
