package dict.spimi

@ExperimentalUnsignedTypes
data class SPIMIFlags(var flags: UInt = 0u) {
    operator fun get(idx: Int) =
        (flags shr idx) and 1u == 1u

    operator fun set(idx: Int, flag: Boolean) {
        val value = if (flag) 1u else 0u
        flags = flags and (1u shl idx).inv() or (value shl idx)
    }

    inline fun <T> compressionAction(idx: Int, big: () -> T, medium: () -> T, small: () -> T): T =
        if (get(idx)) {
            if (get(idx + 1)) small()
            else medium()
        } else big()

    inline fun <T> slcAction(big: () -> T, medium: () -> T, small: () -> T): T =
        compressionAction(0, big, medium, small)

    inline fun <T> spcAction(big: () -> T, medium: () -> T, small: () -> T): T =
        compressionAction(2, big, medium, small)

    inline fun <T> dscAction(big: () -> T, medium: () -> T, small: () -> T): T =
        compressionAction(4, big, medium, small)

    inline fun <T> dicAction(big: () -> T, medium: () -> T, small: () -> T): T =
        compressionAction(6, big, medium, small)

    inline fun <T> dpcAction(big: () -> T, medium: () -> T, small: () -> T): T =
        compressionAction(8, big, medium, small)

    inline val stringLengthSize get() = slcAction({ 4u }, { 2u }, { 1u })
    inline val stringPointerSize get() = spcAction({ 4u }, { 2u }, { 1u })
    inline val documentBlockSize get() = dscAction({ 4u }, { 2u }, { 1u })
    inline val documentIDSize get() = dicAction({ 4u }, { 2u }, { 1u })
    inline val documentPointerSize get() = dpcAction({ 4u }, { 2u }, { 1u })

    val entrySize get() = stringPointerSize + if (db) documentPointerSize else documentIDSize

    var slc
        get() = get(0)
        set(value) = set(0, value)

    var sluc
        get() = get(1)
        set(value) = set(1, value)

    var spc
        get() = get(2)
        set(value) = set(2, value)

    var spuc
        get() = get(3)
        set(value) = set(3, value)

    var dsc
        get() = get(4)
        set(value) = set(4, value)

    var dsuc
        get() = get(5)
        set(value) = set(5, value)

    var dic
        get() = get(6)
        set(value) = set(6, value)

    var diuc
        get() = get(7)
        set(value) = set(7, value)

    var dpc
        get() = get(8)
        set(value) = set(8, value)

    var dpuc
        get() = get(9)
        set(value) = set(9, value)

    var se
        get() = get(10)
        set(value) = set(10, value)

    var ud
        get() = get(11)
        set(value) = set(11, value)

    var ss
        get() = get(12)
        set(value) = set(12, value)

    var db
        get() = get(13)
        set(value) = set(13, value)

    var es
        get() = get(14)
        set(value) = set(14, value)

    var ed
        get() = get(15)
        set(value) = set(15, value)

    override fun toString() = buildString {
        append("SPIMIFlags(")
        append(sequence {
            if (slc) yield("SLC")
            if (sluc) yield("SLUC")
            if (spc) yield("SPC")
            if (spuc) yield("SPUC")
            if (dsc) yield("DSC")
            if (dsuc) yield("DSUC")
            if (dic) yield("DIC")
            if (diuc) yield("DUIC")
            if (dpc) yield("DPC")
            if (dpuc) yield("DPUC")
            if (se) yield("SE")
            if (ud) yield("UD")
            if (db) yield("DB")
            if (ss) yield("SS")
            if (es) yield("ES")
            if (ed) yield("ED")
        }.joinToString(separator = ", "))
        append(')')
    }
}
