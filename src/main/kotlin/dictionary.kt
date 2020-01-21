import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

inline class DocumentID(val id: Int) : Comparable<DocumentID> {
    override fun compareTo(other: DocumentID) =
        id.compareTo(other.id)
}

inline class DictionaryEntry(val counts: ArrayMap<DocumentID, Int> = ArrayMap()) {
    override fun toString(): String {
        return buildString {
            append("DictionaryEntry(counts=[")
            for ((document, count) in counts) {
                append(document)
                append(':')
                append(count)
            }
            append("])")
        }
    }
}

class Dictionary : Iterable<Dictionary.Companion.WordWithEntry> {
    private val entries = ArrayMap<String, DictionaryEntry>()
    private val requestQueue: ArrayBlockingQueue<InsertionRequest> by lazy {
        ArrayBlockingQueue<InsertionRequest>(1000)
    }

    private val newDictFactory = { DictionaryEntry() }
    private val increment: (Int) -> Int = { it + 1}
    private val newCountFactory = { 0 }
    fun add(word: String, document: DocumentID) {
        val entry = entries.getOrSet(word, newDictFactory)
        entry.counts.mutateOrSet(document, increment, newCountFactory)
    }

    fun addParallel(from: Collection<File>) {
        val done = AtomicInteger(0)
        val tasks = from.mapIndexed { idx, file ->
            GlobalScope.launch(Dispatchers.IO) {
                val br = BufferedReader(FileReader(file))
                br.lineSequence()
                    .flatMap { it.split(Regex("\\W+")).asSequence() }
                    .filter { it.isNotBlank() }
                    .map { it.toLowerCase() }
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
                    .filter { it.isNotBlank() }
                    .map { it.toLowerCase() }
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
        while (!until()) {
            if (requestQueue.isNotEmpty()) {
                val (word, from) = requestQueue.poll()
                add(word!!, from)
            }
        }
    }

    override fun toString(): String {
        return "Dictionary(entries=$entries)"
    }

    override fun iterator() =
        entries.iterator().asSequence().map { (word, entry) -> WordWithEntry(word, entry) }.iterator()

    companion object {
        data class InsertionRequest(val word: String?, val from: DocumentID)
        data class WordWithEntry(val word: String, val entry: DictionaryEntry)
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
//    val suspendDict = Dictionary()
//    val suspendTime = measureTimeMillis {
//        runBlocking {
//            suspendDict.addSuspending(getFiles(("input")))
//        }
//    }
//    println("suspend")
//
//    val parallelDict = Dictionary()
//    val parallelTime = measureTimeMillis {
//        parallelDict.addParallel(getFiles("input"))
//    }
//    println("parallel")

    val syncDict = Dictionary()
    val syncTime = measureTimeMillis {
        for (file in getFiles("input")) {
            val br = BufferedReader(FileReader(file))
            br.lineSequence()
                .flatMap { it.split(Regex("\\W+")).asSequence() }
                .filter { it.isNotBlank() }
                .map { it.toLowerCase() }
                .forEach { syncDict.add(it, DocumentID(0)) }
            br.close()
        }
    }

    syncDict.forEach {
        println(it)
    }

//    val parallelRatio = (syncTime.toDouble() / parallelTime).round(2)
//    val suspendRatio = (syncTime.toDouble() / suspendTime).round(2)
//    println("Parallel: $parallelTime ($parallelRatio x), Suspending $suspendTime ($suspendRatio x), Synchronous: $syncTime")
}
