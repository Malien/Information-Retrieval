import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

inline class DocumentID(val id: Int): Comparable<DocumentID> {
    override fun compareTo(other: DocumentID) =
        id.compareTo(other.id)
}

data class WordCount(val document: DocumentID, var count: Int = 1): Comparable<WordCount> {
    override fun compareTo(other: WordCount) =
        document.compareTo(other.document)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WordCount
        if (document != other.document) return false
        return true
    }

    override fun hashCode() = document.id

}

data class DictionaryEntry(val key: String, val counts: ArrayList<WordCount>): Comparable<DictionaryEntry> {
    constructor(key: String, from: DocumentID) : this(key, arrayListOf(WordCount(from)))

    override fun compareTo(other: DictionaryEntry) =
        key.compareTo(other.key)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DictionaryEntry
        if (key != other.key) return false
        return true
    }

    override fun hashCode() = key.hashCode()

}

class Dictionary: Iterable<DictionaryEntry> {
    private val entries = ArrayList<DictionaryEntry>()
    private val requestQueue: ArrayBlockingQueue<InsertionRequest> by lazy {
        ArrayBlockingQueue<InsertionRequest>(1000)
    }

    fun add(word: String, from: DocumentID) {
        val insertionPoint = entries.binarySearch { word.compareTo(it.key) }
        if (insertionPoint >= 0) {
            val counts = entries[insertionPoint].counts
            val count = counts.find { it.document == from }
            if (count == null) counts.add(WordCount(from))
            else count.count++
        } else entries.insert(DictionaryEntry(word, from), -insertionPoint - 1)
    }

    fun addParallel(from: Collection<File>) {
        val done = AtomicInteger(0)
        val tasks = from.mapIndexed { idx, file ->
            GlobalScope.launch(Dispatchers.IO) {
                val br = BufferedReader(FileReader(file))
                br.lineSequence()
                    .flatMap { it.split(Regex("\\W+")).asSequence() }
                    .filter  { it.isNotBlank() }
                    .map     { it.toLowerCase() }
                    .forEach {
                        requestQueue.put(InsertionRequest(it, DocumentID(idx)))
                    }
                done.incrementAndGet()
            }
        }
        runConsumer { done.compareAndSet(from.size, done.get()) }
        runBlocking { tasks.forEach { it.join() } }
    }


    @ExperimentalCoroutinesApi
    suspend fun addSuspending(from: List<File>) {
        val channel = Channel<InsertionRequest>(1000)
        for ((idx, file) in from.withIndex()) {
            GlobalScope.launch(Dispatchers.IO) {
                val br = BufferedReader(FileReader(file))
                br.lineSequence()
                    .flatMap { it.split(Regex("\\W+")).asSequence() }
                    .filter  { it.isNotBlank() }
                    .map     { it.toLowerCase() }
                    .forEach {
                        channel.send(InsertionRequest(it, DocumentID(idx)))
                    }
                channel.send(InsertionRequest(null, DocumentID(idx)))
            }
        }
        var count = from.size
        while (count > 0) {
            val (word, document) = channel.receive()
            if (word != null) add(word, document)
            else count--
        }
    }

    fun runConsumer(until: () -> Boolean) {
        while(!until()) {
            if (requestQueue.isNotEmpty()) {
                val (word, from) = requestQueue.poll()
                add(word!!, from)
            }
        }
    }

    override fun toString(): String {
        return "Dictionary(entries=$entries)"
    }

    override fun iterator() = entries.iterator()

    companion object {
        data class InsertionRequest(val word: String?, val from: DocumentID)
    }

}

fun getFiles(path: String, extension: String? = null): List<File> {
    val directory = File(path)
    if (!directory.exists() && !directory.isDirectory) return emptyList()
    val files = directory.list { dir, name ->
        val file = File(dir, name)
        file.exists() && file.isFile && (extension == null || file.extension == extension)
    } ?: emptyArray()
    return files.map { File(directory, it) }
}

@ExperimentalCoroutinesApi
fun main() {
    val suspendDict = Dictionary()
    val suspendTime = measureTimeMillis {
        runBlocking {
            suspendDict.addSuspending(getFiles(("input")))
        }
    }
    println("suspend")

    val parallelDict = Dictionary()
    val parallelTime = measureTimeMillis {
        parallelDict.addParallel(getFiles("input"))
    }
    println("parallel")

    val syncDict = Dictionary()
    val syncTime = measureTimeMillis {
        for (file in getFiles("input")) {
            val br = BufferedReader(FileReader(file))
            br.lineSequence()
                .flatMap { it.split(Regex("\\W+")).asSequence() }
                .filter  { it.isNotBlank() }
                .map     { it.toLowerCase() }
                .forEach { syncDict.add(it, DocumentID(0)) }
            br.close()
        }
    }

    val parallelRatio = (syncTime.toDouble() / parallelTime).round(2)
    val suspendRatio  = (syncTime.toDouble() / suspendTime).round(2)
    println("Parallel: $parallelTime ($parallelRatio x), Suspending $suspendTime ($suspendRatio x), Synchronous: $syncTime")
}
