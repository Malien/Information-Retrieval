# Information-Retrieval

### Retrieve your info like a... boss(?). 
_Probably not_

## OwO wats dis?
This is commandline program for indexing and finding stuff within your text files. Like grep but with indexing and worse.

## How does one build this bad boi?
Just gradle this son of a bitch. Gradle him good. But seriously everything's there, yet in gradle kotlin DSL. Dunno retrieve some info (see what I did there?) online on how to import gradle project.

## How does it work?
I assume you are familiar with the commandline args, so here's the arguments this one has. (sorry, no `--help` or `man` yet)
```bash
# If packaged inside of a jar file
java -jar executable-name.jar [files or directories] <options>
```
If directory is specified, program will index all the files within
### Huh, options you say. What are they, you might ask. Here you go, bud:
- `i` or `interactive` will launch REPL mode, aka. Read Print Eval Loop where you can write your queries and get result inline.
- `stat` will print collection and runtime info upon finishing indexing.
- `v` or `verbose` will print a lot more stuff to the console. Recommended to set it unless you wan't only pure results.
- `s` or `sequential` will index things sequentially, instead of in parallel. Parallel mode is not implemented, so it will only index things sequentially by now.
- `n` will evaluate boolean negations. _Only in map-reduce mode_
- `disable-single-word` will disable indexing stuff inside of single-word index table.
- `disable-double-word` will disable indexing word pairs for more relevant and fast two-word queries. Disabling double-word dictionary will lower accuracy if position is also disabled
- `disable-postion` will disable indexing words with their respected positions. This disables finding words within k words within each other and will lower accuracy of mutli-word queries
- `find [query]` **\[WIP\]** will execute single query.
- `execute [file]` **\[WIP\]** will execute queries found in file
- `o [file]` will save indexing table to file
- `from [file]` will load indexing table from file
- `map-reduce` uses new highly scalable map-reduce indexer. Is completely incompatible with non map-reduce variant
- `p [count]` how many processing threads are used for indexing. _Only in map-reduce mode_
- `r` if directory is specified, will index recursively all of the sub-directories
- `pretty-print` enables a bit prettier printing to the console in verbose mode
- `joker [types]` specifies what kind of indexing structure for non-complete word queries is used. Possible types are:
  - `prefix-tree`
  - `trigram`
  - `relocation`
  
### SPIMI (map-reduce) mode
That is completely different kind of beast to old way of doing indexing. 
It relies on SPIMI file structure described in the `src/dict/spimi/README.md`. 

Files structure of indexed collections may be structured like this:
- `<name>.sppckg` -- complete package
  - `dictionary.spimi` -- reduced dictionary
  - `strings.sstr` -- external strings
  - `documents.sdoc` -- external document sequences
  - `<uuid>.spimim` -- chunks of mapping data

### Queries you say...
Yep. And the are kinda structured, but still not SQL. Queries are just words or phrases you want to find with some additional features, such as:
#### Boolean logic
You can structure queries as such
```
hello & !(goodbye | evening)
```
Which means: Find all documents where there is `hello` and there is no `goodbye` or `evening`. As I said, boolean logic

There are following operators:
- `&` and, e.g. `hello & goodbye` _(hello and goodbye)_
- `|` or, e.g. `hello | goodbye` _(hello or goodbye)_
- `!` not, e.g. `!hello` _(not hello)_
- and parenthesis `(`, `)` for setting precedence

_Note: because operation not could take a lot of time to evaluate. It is optimized not to be evaluated each time it is specified, but if resulting value is a negation, it will not be evaluated, unless flag `n` is specified_
### Closeness
You can specify how far words in a phrase are apart from one another as such:
```
hello \3 evening
```
Which means: Find all documents where `hello` is 3 words behind `evening`. Syntax is `words \k words`, where k is the number of words between.

## License and Copyright stuff
Wait, this thing is licensed? Should be under MIT License. 

© Yaroslav Petryk. 2020
