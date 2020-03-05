package util

/**
 * data class tat holds results of argument parsing.
 */
data class ArgumentMap(
    /** Map of flag - string argument values */
    val strings: HashMap<String, String?> = HashMap(),
    /** Set of flags that are included in the argument list and are specified as boolean in schema */
    val booleans: HashSet<String> = HashSet(),
    /** Map of flag - numeric argument values */
    val numbers: HashMap<String, Double?> = HashMap(),
    /** rest of the parameters, that are not backed by the specific flag */
    val unspecified: ArrayList<String> = ArrayList()
) {
    companion object {
        /**
         * Copies defaults from schema that specify flags and their default values
         * Default values for boolean arguments are false, as such they are not included in the list
         * @param strings defaults for string arguments. Map of flag - string default values
         * @param numbers defaults for numeric arguments. Maps f flag - numeric default values
         * @return ArgumentMap constructed from schema
         */
        @Suppress("UNCHECKED_CAST")
        fun fromSchema(
            strings: HashMap<String, String?>,
            numbers: HashMap<String, Double?>
        ) = ArgumentMap(
            strings = strings.clone() as HashMap<String, String?>,
            numbers = numbers.clone() as HashMap<String, Double?>
        )
    }
}

/**
 * Function that parses commandline arguments according to the provided schema.
 * If flag is not in the number or boolean schema it it considered to be of string type
 * @param args commandline arguments passed to main function
 * @param strings defaults for string arguments. Map of flag - string default values
 * @param numbers defaults for numeric arguments. Maps f flag - numeric default values
 * @param booleans set of flags that are considered of boolean type
 * @return ArgumentMap that contains parsed values. Values without flag are considered to be unspecified
 * TODO: add support for shortened arguments
 * TODO: add support for string parsing and/or escape characters
 */
fun parseArgs(
    args: Array<String>,
    strings: HashMap<String, String?> = HashMap(),
    numbers: HashMap<String, Double?> = HashMap(),
    booleans: HashSet<String> = HashSet()
): ArgumentMap {
    val arguments = ArgumentMap.fromSchema(strings, numbers)
    var flag: String? = null
    val (stringArguments, booleanArguments, numericArguments, unspecifiedArguments) = arguments
    for (arg in args) {
        if (flag != null) {
            if (flag in numericArguments) numericArguments[flag] = arg.toDouble()
            else stringArguments[flag] = arg
            flag = null
        } else {
            if (arg.startsWith("-")) {
                val stripped = arg.drop(1)
                if (stripped in booleans) booleanArguments.add(stripped)
                else flag = stripped
            } else unspecifiedArguments.add(arg)
        }
    }
    return arguments
}