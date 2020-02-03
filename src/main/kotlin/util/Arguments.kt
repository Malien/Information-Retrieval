package util

data class ArgumentMap(val strings: HashMap<String, String?> = HashMap(),
                       val booleans: HashSet<String> = HashSet(),
                       val numbers: HashMap<String, Double?> = HashMap(),
                       val unspecified: ArrayList<String> = ArrayList())
{
    constructor(defaults: DefaultArguments) : this(strings = defaults.strings, numbers = defaults.numbers)
}

data class DefaultArguments (val strings: HashMap<String, String?> = HashMap(),
                             val numbers: HashMap<String, Double?> = HashMap(),
                             val booleans: HashSet<String> = HashSet())

//TODO: add support for shortened arguments
//TODO: add support for string parsing and/or escape characters
fun parseArgs(defaults: DefaultArguments, args: Array<String>) : ArgumentMap {
    val arguments = ArgumentMap(defaults)
    var flag: String? = null
    val (strs, bools, nums, other) = arguments
    val boolDesc = defaults.booleans
    for (arg in args) {
        if (flag != null) {
            if (flag in nums) nums[flag] = arg.toDouble()
            else strs[flag] = arg
        } else {
            if (arg.startsWith("-")) {
                val stripped = arg.drop(1)
                if (stripped in boolDesc) bools.add(stripped)
                else flag = stripped
            } else other.add(arg)
        }
    }
    return arguments
}