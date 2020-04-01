package parser.cfg

class SyntaxError(message: String) : Error(message)

class InterpretationError(msg: String) : Error(msg)

class LexicalError(msg: String) : Error(msg)
