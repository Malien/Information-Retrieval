package dict.legacy.joker

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class PrefixDict : JokerDict {
   private val prefix = PrefixTree()
   private val suffix = PrefixTree()

   override fun add(word: String) {
      prefix.add(word)
      suffix.add(word.reversed())
   }

   @Transient
   val firstRegex = Regex("""\w+\*""")
   @Transient
   val lastRegex = Regex("""\*\w+""")

   override fun getRough(query: String): Iterator<String>? {
      val first = firstRegex.find(query)?.value
      val last = lastRegex.find(query)?.value

      val fres = if (first != null) {
         HashSet<String>().apply {
            for (res in prefix.query(first.dropLast(1))) add(res)
         }
      } else null

      val lres = if (last != null) {
         HashSet<String>().apply {
            for (res in suffix.query(last.drop(1).reversed())) add(res.reversed())
         }
      } else null

      return when {
         fres != null && lres != null -> fres.intersect(lres)
         lres != null -> lres
         fres != null -> fres
         else -> emptySet()
      }.iterator()
   }
}