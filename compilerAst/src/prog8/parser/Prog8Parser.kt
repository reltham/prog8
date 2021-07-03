package prog8.parser

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.ParseCancellationException
import prog8.ast.antlr.toAst
import prog8.ast.Module


class Prog8ErrorStrategy: BailErrorStrategy() {
    override fun recover(recognizer: Parser?, e: RecognitionException?) {
        try {
            // let it
            super.recover(recognizer, e) // fills in exception e in all the contexts
            // ...then throws ParseCancellationException, which is
            // *deliberately* not a RecognitionException. However, we don't try any
            // error recovery, therefore report an error in this case, too.
        } catch (pce: ParseCancellationException) {
            reportError(recognizer, e)
        }
    }

    override fun recoverInline(recognizer: Parser?): Token {
        throw InputMismatchException(recognizer)
    }
}

object ThrowErrorListener: BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
        throw ParsingFailedError("$e: $msg")
    }
}

class Prog8Parser(private val errorListener: ANTLRErrorListener = ThrowErrorListener) {

    fun parseModule(sourceText: String): Module {
        val chars = CharStreams.fromString(sourceText)
        val lexer = Prog8ANTLRLexer(chars)
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val tokens = CommonTokenStream(lexer)
        val parser = Prog8ANTLRParser(tokens)
        parser.errorHandler = Prog8ErrorStrategy()
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val parseTree = parser.module()
        val moduleName = "anonymous"

        val module = parseTree.toAst(moduleName, pathFrom(""), PetsciiEncoding)
        // TODO: use Module ctor directly

        for (statement in module.statements) {
            statement.linkParents(module)
    }
        return module
    }
}
