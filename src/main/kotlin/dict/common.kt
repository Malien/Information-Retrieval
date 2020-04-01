package dict

import util.KeySet

typealias Documents = KeySet<DocumentID>

fun emptyDocuments() = Documents(iterator {})

