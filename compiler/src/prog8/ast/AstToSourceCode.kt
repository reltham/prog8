package prog8.ast

import prog8.ast.antlr.escape
import prog8.ast.base.DataType
import prog8.ast.base.NumericDatatypes
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.*
import prog8.compiler.toHex

class AstToSourceCode(val output: (text: String) -> Unit, val program: Program): IAstVisitor {
    private var scopelevel = 0

    private fun indent(s: String) = "    ".repeat(scopelevel) + s
    private fun outputln(text: String) = output(text + "\n")
    private fun outputlni(s: Any) = outputln(indent(s.toString()))
    private fun outputi(s: Any) = output(indent(s.toString()))

    override fun visit(program: Program) {
        outputln("============= PROGRAM ${program.name} (FROM AST) ===============")
        super.visit(program)
        outputln("============= END PROGRAM ${program.name} (FROM AST) ===========")
    }

    override fun visit(module: Module) {
        if(!module.isLibraryModule) {
            outputln("; ----------- module: ${module.name} -----------")
            super.visit(module)
        }
        else outputln("; library module skipped: ${module.name}")
    }

    override fun visit(block: Block) {
        val addr = if(block.address!=null) block.address.toHex() else ""
        outputln("~ ${block.name} $addr {")
        scopelevel++
        for(stmt in block.statements) {
            outputi("")
            stmt.accept(this)
            output("\n")
        }
        scopelevel--
        outputln("}\n")
    }

    override fun visit(expr: PrefixExpression) {
        if(expr.operator.any { it.isLetter() })
            output(" ${expr.operator} ")
        else
            output(expr.operator)
        expr.expression.accept(this)
    }

    override fun visit(expr: BinaryExpression) {
        expr.left.accept(this)
        if(expr.operator.any { it.isLetter() })
            output(" ${expr.operator} ")
        else
            output(expr.operator)
        expr.right.accept(this)
    }

    override fun visit(directive: Directive) {
        output("${directive.directive} ")
        for(arg in directive.args) {
            when {
                arg.int!=null -> output(arg.int.toString())
                arg.name!=null -> output(arg.name)
                arg.str!=null -> output("\"${arg.str}\"")
            }
            if(arg!==directive.args.last())
                output(",")
        }
        output("\n")
    }

    private fun datatypeString(dt: DataType): String {
        return when(dt) {
            in NumericDatatypes -> dt.toString().toLowerCase()
            DataType.STR -> dt.toString().toLowerCase()
            DataType.ARRAY_UB -> "ubyte["
            DataType.ARRAY_B -> "byte["
            DataType.ARRAY_UW -> "uword["
            DataType.ARRAY_W -> "word["
            DataType.ARRAY_F -> "float["
            DataType.STRUCT -> ""       // the name of the struct is enough
            else -> "?????2"
        }
    }

    override fun visit(structDecl: StructDecl) {
        outputln("struct ${structDecl.name} {")
        scopelevel++
        for(decl in structDecl.statements) {
            outputi("")
            decl.accept(this)
            output("\n")
        }
        scopelevel--
        outputlni("}")
    }

    override fun visit(decl: VarDecl) {
        when(decl.type) {
            VarDeclType.VAR -> {}
            VarDeclType.CONST -> output("const ")
            VarDeclType.MEMORY -> output("&")
        }
        output(decl.struct?.name ?: "")
        output(datatypeString(decl.datatype))
        if(decl.arraysize!=null) {
            decl.arraysize!!.index.accept(this)
        }
        if(decl.isArray)
            output("]")

        if(decl.zeropage == ZeropageWish.REQUIRE_ZEROPAGE || decl.zeropage==ZeropageWish.PREFER_ZEROPAGE)
            output(" @zp")
        output(" ${decl.name} ")
        if(decl.value!=null) {
            output("= ")
            decl.value?.accept(this)
        }
    }

    override fun visit(subroutine: Subroutine) {
        output("\n")
        if(subroutine.isAsmSubroutine) {
            outputi("asmsub ${subroutine.name} (")
            for(param in subroutine.parameters.zip(subroutine.asmParameterRegisters)) {
                val reg =
                        when {
                            param.second.stack -> "stack"
                            param.second.registerOrPair!=null -> param.second.registerOrPair.toString()
                            param.second.statusflag!=null -> param.second.statusflag.toString()
                            else -> "?????1"
                        }
                output("${datatypeString(param.first.type)} ${param.first.name} @$reg")
                if(param.first!==subroutine.parameters.last())
                    output(", ")
            }
        }
        else {
            outputi("sub ${subroutine.name} (")
            for(param in subroutine.parameters) {
                output("${datatypeString(param.type)} ${param.name}")
                if(param!==subroutine.parameters.last())
                    output(", ")
            }
        }
        output(") ")
        if(subroutine.asmClobbers.isNotEmpty()) {
            output("-> clobbers (")
            val regs = subroutine.asmClobbers.toList().sorted()
            for(r in regs) {
                output(r.toString())
                if(r!==regs.last())
                    output(",")
            }
            output(") ")
        }
        if(subroutine.returntypes.any()) {
            val rt = subroutine.returntypes.single()
            output("-> ${datatypeString(rt)} ")
        }
        if(subroutine.asmAddress!=null)
            outputln("= ${subroutine.asmAddress.toHex()}")
        else {
            outputln("{ ")
            scopelevel++
            outputStatements(subroutine.statements)
            scopelevel--
            outputi("}")
        }
    }

    private fun outputStatements(statements: List<Statement>) {
        for(stmt in statements) {
            if(stmt is VarDecl && stmt.autogeneratedDontRemove)
                continue    // skip autogenerated decls (to avoid generating a newline)
            outputi("")
            stmt.accept(this)
            output("\n")
        }
    }

    override fun visit(functionCall: FunctionCall) {
        printout(functionCall as IFunctionCall)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        printout(functionCallStatement as IFunctionCall)
    }

    private fun printout(call: IFunctionCall) {
        call.target.accept(this)
        output("(")
        for(arg in call.arglist) {
            arg.accept(this)
            if(arg!==call.arglist.last())
                output(", ")
        }
        output(")")
    }

    override fun visit(identifier: IdentifierReference) {
        output(identifier.nameInSource.joinToString("."))
    }

    override fun visit(jump: Jump) {
        output("goto ")
        when {
            jump.address!=null -> output(jump.address.toHex())
            jump.generatedLabel!=null -> output(jump.generatedLabel)
            jump.identifier!=null -> jump.identifier.accept(this)
        }
    }

    override fun visit(ifStatement: IfStatement) {
        output("if ")
        ifStatement.condition.accept(this)
        output(" ")
        ifStatement.truepart.accept(this)
        if(ifStatement.elsepart.statements.isNotEmpty()) {
            output(" else ")
            ifStatement.elsepart.accept(this)
        }
    }

    override fun visit(branchStatement: BranchStatement) {
        output("if_${branchStatement.condition.toString().toLowerCase()} ")
        branchStatement.truepart.accept(this)
        if(branchStatement.elsepart.statements.isNotEmpty()) {
            output(" else ")
            branchStatement.elsepart.accept(this)
        }
    }

    override fun visit(range: RangeExpr) {
        range.from.accept(this)
        output(" to ")
        range.to.accept(this)
        output(" step ")
        range.step.accept(this)
        output(" ")
    }

    override fun visit(label: Label) {
        output("\n")
        output("${label.name}:")
    }

    override fun visit(numLiteral: NumericLiteralValue) {
        output(numLiteral.number.toString())
    }

    override fun visit(string: StringLiteralValue) {
        output("\"${escape(string.value)}\"")
    }

    override fun visit(array: ArrayLiteralValue) {
        outputListMembers(array.value.asSequence(), '[', ']')
    }

    private fun outputListMembers(array: Sequence<Expression>, openchar: Char, closechar: Char) {
        var counter = 0
        output(openchar.toString())
        scopelevel++
        for (v in array) {
            v.accept(this)
            if (v !== array.last())
                output(", ")
            counter++
            if (counter > 16) {
                outputln("")
                outputi("")
                counter = 0
            }
        }
        scopelevel--
        output(closechar.toString())
    }

    override fun visit(assignment: Assignment) {
        if(assignment is VariableInitializationAssignment) {
            val targetVar = assignment.target.identifier?.targetVarDecl(program.namespace)
            if(targetVar?.struct != null) {
                // skip STRUCT init assignments
                return
            }
        }

        assignment.target.accept(this)
        if (assignment.aug_op != null)
            output(" ${assignment.aug_op} ")
        else
            output(" = ")
        assignment.value.accept(this)
    }

    override fun visit(postIncrDecr: PostIncrDecr) {
        postIncrDecr.target.accept(this)
        output(postIncrDecr.operator)
    }

    override fun visit(contStmt: Continue) {
        output("continue")
    }

    override fun visit(breakStmt: Break) {
        output("break")
    }

    override fun visit(forLoop: ForLoop) {
        output("for ")
        if(forLoop.loopRegister!=null)
            output(forLoop.loopRegister.toString())
        else
            forLoop.loopVar!!.accept(this)
        output(" in ")
        forLoop.iterable.accept(this)
        output(" ")
        forLoop.body.accept(this)
    }

    override fun visit(whileLoop: WhileLoop) {
        output("while ")
        whileLoop.condition.accept(this)
        output(" ")
        whileLoop.body.accept(this)
    }

    override fun visit(repeatLoop: RepeatLoop) {
        output("repeat ")
        repeatLoop.body.accept(this)
        output(" until ")
        repeatLoop.untilCondition.accept(this)
    }

    override fun visit(returnStmt: Return) {
        output("return ")
        returnStmt.value?.accept(this)
    }

    override fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
        arrayIndexedExpression.identifier.accept(this)
        output("[")
        arrayIndexedExpression.arrayspec.index.accept(this)
        output("]")
    }

    override fun visit(assignTarget: AssignTarget) {
        if(assignTarget.register!=null)
            output(assignTarget.register.toString())
        else {
            assignTarget.memoryAddress?.accept(this)
            assignTarget.identifier?.accept(this)
        }
        assignTarget.arrayindexed?.accept(this)
    }

    override fun visit(scope: AnonymousScope) {
        outputln("{")
        scopelevel++
        outputStatements(scope.statements)
        scopelevel--
        outputi("}")
    }

    override fun visit(typecast: TypecastExpression) {
        output("(")
        typecast.expression.accept(this)
        output(" as ${datatypeString(typecast.type)}) ")
    }

    override fun visit(memread: DirectMemoryRead) {
        output("@(")
        memread.addressExpression.accept(this)
        output(")")
    }

    override fun visit(memwrite: DirectMemoryWrite) {
        output("@(")
        memwrite.addressExpression.accept(this)
        output(")")
    }

    override fun visit(addressOf: AddressOf) {
        output("&")
        addressOf.identifier.accept(this)
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        outputlni("%asm {{")
        outputln(inlineAssembly.assembly)
        outputlni("}}")
    }

    override fun visit(registerExpr: RegisterExpr) {
        output(registerExpr.register.toString())
    }

    override fun visit(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder) {
        output(builtinFunctionStatementPlaceholder.name)
    }

    override fun visit(whenStatement: WhenStatement) {
        output("when ")
        whenStatement.condition.accept(this)
        outputln(" {")
        scopelevel++
        whenStatement.choices.forEach { it.accept(this) }
        scopelevel--
        outputlni("}")
    }

    override fun visit(whenChoice: WhenChoice) {
        val choiceValues = whenChoice.values
        if(choiceValues==null)
            outputi("else -> ")
        else {
            outputi("")
            for(value in choiceValues) {
                value.accept(this)
                if(value !== choiceValues.last())
                    output(",")
            }
            output(" -> ")
        }
        if(whenChoice.statements.statements.size==1)
            whenChoice.statements.statements.single().accept(this)
        else
            whenChoice.statements.accept(this)
        outputln("")
    }

    override fun visit(structLv: StructLiteralValue) {
        outputListMembers(structLv.values.asSequence(), '{', '}')
    }

    override fun visit(nopStatement: NopStatement) {
        output("; NOP @ ${nopStatement.position}  $nopStatement")
    }
}
