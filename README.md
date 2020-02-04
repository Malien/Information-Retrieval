# Information-Retrieval

### Retrieve your info like a... boss(?). 
_Probably not_

## OwO wats dis?
This is commandline program for indexing and finding stuff within your text files. Like grep but with indexing and worse.

## How does one build this bad boi?
Just gradle this son of a bitch. Gradle him good. But seriously everyting's there, yet in gradle kotlin DSL. Dunno retrieve some info (see what I did there?) online on how to import gradle project.

## How does it work?
I assume you are familiar with the commandline args, so here's the arguments this one has. (sorry, no `--help` or `man` yet)
```bash
# If packaged inside of a jar file
java -jar executable-name.jar [files] <options>
java -jar executable-name.jar -d [directories] <options>
```
### Huh, options you say. What are they, you might ask. Here you go, bud:
- `d` index files inside directories istead of files themselves
- `i` or `interactive` will launch REPL mode, aka. Read Print Eval Loop where you can write your queries and get result inline.
- `stat` will print collection and runtime info upon finishing indexing.
- `v` or `verbose` will print a lot more stuff to the console. Recomended to set it unless you wan't only pure results.
- `s` or `sequential` **\[WIP\]** will index things sequentially, instead of in parallel. Parallel mode is not implemented, so it will only index things sequentially by now.
- `n` will evaluate boolean negations.
- `disable-single-word` will disable indexing stuff inside of single-word index table.
- `disable-double-word` will disable indexing word pairs for more relevant and fast two-word queries
- `disable-postion` will disable indexing words with their respected positions. This disables finding words within k words within each other and will lower accuracy of mutli-word queries
- `find [query]` will execute single querry.
- `execute [file]` will execute queries found in file
- `o [file]` will save indexing table to file
- `from [file]` will load indexing table from file

### Querries you say...
Yep. And the are kida structured, but still not SQL. Querries are just words or phrases you want to find with some additional features, such as:
#### Boolean logic
You can structure querries as such
```
hello & !(goodbye | evening)
```
Which means: Find all documents where there is `hello` and there is no `goodbye` or `evening`. As I said, boolean logic
There are following operators:
- `&` and, e.g. `hello & goodbye` _(hello and goodbye)_
- `|` or, e.g. `hello | goodbye` _(hello or goodbye)_
- `!` not, e.g. `!hello` _(not hello)_
- and parentasis `(`, `)` for setting precedence

_Note: because operation not could take a lot of time to evaluate. It is optimized not to be evalueated each time it is specified, but if resulting value is a negation, it will not be evalueated, unless flag `n` is specified_
### [WIP] Closeness
You can specify how far words in a phrase are appart from one another as such:
```
hello \3 evening
```
Which means: Find all documents where `hello` is 3 words behind `evening`. Syntax is `words \k words`, where k is the number of words between.

## License and Copyright stuff
Wait, this thing is licensed? Should be under MIT License. 

© Yaroslav Petryk. 2020
