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

class Arguments(private val defaults_: DefaultArguments, args: Array<String>) {
    private val arguments_ = ArgumentMap(defaults_)

    val arguments get() = arguments_
    val defaults get() = defaults_

    val strings get() = arguments_.strings
    val booleans get() = arguments_.booleans
    val numbers get() = arguments_.numbers
    val unspecified get() = arguments_.unspecified

    init {
        var flag: String? = null
        val (strs, bools, nums, other) = arguments_
        val boolDesc = defaults_.booleans
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
    }
}