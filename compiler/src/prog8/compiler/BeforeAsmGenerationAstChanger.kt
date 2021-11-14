package prog8.compiler

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor
import prog8.compiler.astprocessing.isSubroutineParameter
import prog8.compiler.target.AssemblyError
import prog8.compilerinterface.*


internal class BeforeAsmGenerationAstChanger(val program: Program, private val options: CompilationOptions,
                                             private val errors: IErrorReporter) : AstWalker() {

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.type==VarDeclType.VAR && decl.value != null && decl.datatype in NumericDatatypes)
            throw FatalAstException("vardecls for variables, with initial numerical value, should have been rewritten as plain vardecl + assignment $decl")

        subroutineVariables.add(decl.name to decl)
        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // Try to replace A = B <operator> Something  by A= B, A = A <operator> Something
        // this triggers the more efficent augmented assignment code generation more often.
        // But it can only be done if the target variable IS NOT OCCURRING AS AN OPERAND ITSELF.
        if(!assignment.isAugmentable
                && assignment.target.identifier != null
                && assignment.target.isInRegularRAMof(options.compTarget.machine)) {
            val binExpr = assignment.value as? BinaryExpression

            if(binExpr!=null && binExpr.inferType(program).istype(DataType.FLOAT) && !options.optimizeFloatExpressions)
                return noModifications

            if (binExpr != null && binExpr.operator !in comparisonOperators) {
                if (binExpr.left !is BinaryExpression) {
                    if (binExpr.right.referencesIdentifier(*assignment.target.identifier!!.nameInSource.toTypedArray())) {
                        // the right part of the expression contains the target variable itself.
                        // we can't 'split' it trivially because the variable will be changed halfway through.
                        if(binExpr.operator in associativeOperators) {
                            // A = <something-without-A>  <associativeoperator>  <otherthing-with-A>
                            // use the other part of the expression to split.
                            val sourceDt = binExpr.right.inferType(program).getOrElse { throw AssemblyError("invalid dt") }
                            val (_, right) = binExpr.right.typecastTo(assignment.target.inferType(program).getOr(DataType.UNDEFINED), sourceDt, implicit=true)
                            val assignRight = Assignment(assignment.target, right, assignment.position)
                            return listOf(
                                    IAstModification.InsertBefore(assignment, assignRight, parent as IStatementContainer),
                                    IAstModification.ReplaceNode(binExpr.right, binExpr.left, binExpr),
                                    IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
                        }
                    } else {
                        val sourceDt = binExpr.left.inferType(program).getOrElse { throw AssemblyError("invalid dt") }
                        val (_, left) = binExpr.left.typecastTo(assignment.target.inferType(program).getOr(DataType.UNDEFINED), sourceDt, implicit=true)
                        val assignLeft = Assignment(assignment.target, left, assignment.position)
                        return listOf(
                                IAstModification.InsertBefore(assignment, assignLeft, parent as IStatementContainer),
                                IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
                    }
                }
            }
        }
        return noModifications
    }

    private val subroutineVariables = mutableListOf<Pair<String, VarDecl>>()
    private val addedIfConditionVars = mutableSetOf<Pair<Subroutine, String>>()

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        subroutineVariables.clear()
        addedIfConditionVars.clear()

        if(!subroutine.isAsmSubroutine) {
            // change 'str' parameters into 'uword' (just treat it as an address)
            val stringParams = subroutine.parameters.filter { it.type==DataType.STR }
            val parameterChanges = stringParams.map {
                val uwordParam = SubroutineParameter(it.name, DataType.UWORD, it.position)
                IAstModification.ReplaceNode(it, uwordParam, subroutine)
            }

            val stringParamNames = stringParams.map { it.name }.toSet()
            val varsChanges  = subroutine.statements
                .filterIsInstance<VarDecl>()
                .filter { it.autogeneratedDontRemove && it.name in stringParamNames }
                .map {
                    val newvar = VarDecl(it.type, DataType.UWORD, it.zeropage, null, it.name, null, false, true, it.sharedWithAsm, it.position)
                    IAstModification.ReplaceNode(it, newvar, subroutine)
                }

            return parameterChanges + varsChanges
        }
        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        if(scope.statements.any { it is VarDecl || it is IStatementContainer })
            throw FatalAstException("anonymousscope may no longer contain any vardecls or subscopes")

        val decls = scope.statements.filterIsInstance<VarDecl>().filter { it.type == VarDeclType.VAR }
        subroutineVariables.addAll(decls.map { it.name to it })
        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val firstDeclarations = mutableMapOf<String, VarDecl>()
        for(decl in subroutineVariables) {
            val existing = firstDeclarations[decl.first]
            if(existing!=null && existing !== decl.second) {
                errors.err("variable ${decl.first} already defined in subroutine ${subroutine.name} at ${existing.position}", decl.second.position)
            } else {
                firstDeclarations[decl.first] = decl.second
            }
        }


        // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernal routine.
        // and if an assembly block doesn't contain a rts/rti, and some other situations.
        val mods = mutableListOf<IAstModification>()
        val returnStmt = Return(null, subroutine.position)
        if (subroutine.asmAddress == null && !subroutine.inline) {
            if(subroutine.statements.isEmpty() ||
                (subroutine.amountOfRtsInAsm() == 0
                && subroutine.statements.lastOrNull { it !is VarDecl } !is Return
                && subroutine.statements.last() !is Subroutine)) {
                    mods += IAstModification.InsertLast(returnStmt, subroutine)
            }
        }

        // precede a subroutine with a return to avoid falling through into the subroutine from code above it
        val outerScope = subroutine.definingScope
        val outerStatements = outerScope.statements
        val subroutineStmtIdx = outerStatements.indexOf(subroutine)
        if (subroutineStmtIdx > 0
                && outerStatements[subroutineStmtIdx - 1] !is Jump
                && outerStatements[subroutineStmtIdx - 1] !is Subroutine
                && outerStatements[subroutineStmtIdx - 1] !is Return
                && outerScope !is Block) {
            mods += IAstModification.InsertAfter(outerStatements[subroutineStmtIdx - 1], returnStmt, outerScope)
        }
        return mods
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove redundant typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword
        // Also the special typecast of a reference type (str, array) to an UWORD will be changed into address-of,
        //   UNLESS it's a str parameter in the containing subroutine - then we remove the typecast altogether
        val sourceDt = typecast.expression.inferType(program).getOr(DataType.UNDEFINED)
        if (typecast.type in ByteDatatypes && sourceDt in ByteDatatypes
                || typecast.type in WordDatatypes && sourceDt in WordDatatypes) {
            if(typecast.parent !is Expression) {
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        if(sourceDt in PassByReferenceDatatypes) {
            if(typecast.type==DataType.UWORD) {
                val identifier = typecast.expression as? IdentifierReference
                if(identifier!=null) {
                    return if(identifier.isSubroutineParameter(program)) {
                        listOf(IAstModification.ReplaceNode(
                            typecast,
                            typecast.expression,
                            parent
                        ))
                    } else {
                        listOf(IAstModification.ReplaceNode(
                            typecast,
                            AddressOf(identifier, typecast.position),
                            parent
                        ))
                    }
                } else if(typecast.expression is IFunctionCall) {
                    return listOf(IAstModification.ReplaceNode(
                            typecast,
                            typecast.expression,
                            parent
                    ))
                }
            } else {
                errors.err("cannot cast pass-by-reference value to type ${typecast.type} (only to UWORD)", typecast.position)
            }
        }

        return noModifications
    }

    @Suppress("DuplicatedCode")
    override fun after(ifStatement: IfStatement, parent: Node): Iterable<IAstModification> {
        val prefixExpr = ifStatement.condition as? PrefixExpression
        if(prefixExpr!=null && prefixExpr.operator=="not") {
            // if not x -> if x==0
            val booleanExpr = BinaryExpression(prefixExpr.expression, "==", NumericLiteralValue.optimalInteger(0, ifStatement.condition.position), ifStatement.condition.position)
            return listOf(IAstModification.ReplaceNode(ifStatement.condition, booleanExpr, ifStatement))
        }

        val binExpr = ifStatement.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in comparisonOperators) {
            // if x  ->  if x!=0,    if x+5  ->  if x+5 != 0
            val booleanExpr = BinaryExpression(ifStatement.condition, "!=", NumericLiteralValue.optimalInteger(0, ifStatement.condition.position), ifStatement.condition.position)
            return listOf(IAstModification.ReplaceNode(ifStatement.condition, booleanExpr, ifStatement))
        }

        if((binExpr.left as? NumericLiteralValue)?.number==0 &&
            (binExpr.right as? NumericLiteralValue)?.number!=0)
            throw FatalAstException("0==X should have been swapped to if X==0")

        // simplify the conditional expression, introduce simple assignments if required.
        // NOTE: sometimes this increases code size because additional stores/loads are generated for the
        //       intermediate variables. We assume these are optimized away from the resulting assembly code later.
        val simplify = simplifyConditionalExpression(binExpr)
        val modifications = mutableListOf<IAstModification>()
        if(simplify.rightVarAssignment!=null) {
            modifications += IAstModification.ReplaceNode(binExpr.right, simplify.rightOperandReplacement!!, binExpr)
            modifications += IAstModification.InsertBefore(ifStatement, simplify.rightVarAssignment, parent as IStatementContainer)
        }
        if(simplify.leftVarAssignment!=null) {
            modifications += IAstModification.ReplaceNode(binExpr.left, simplify.leftOperandReplacement!!, binExpr)
            modifications += IAstModification.InsertBefore(ifStatement, simplify.leftVarAssignment, parent as IStatementContainer)
        }

        return modifications
    }

    private class CondExprSimplificationResult(
        val leftVarAssignment: Assignment?,
        val leftOperandReplacement: Expression?,
        val rightVarAssignment: Assignment?,
        val rightOperandReplacement: Expression?
    )

    private fun simplifyConditionalExpression(expr: BinaryExpression): CondExprSimplificationResult {
        var leftAssignment: Assignment? = null
        var leftOperandReplacement: Expression? = null
        var rightAssignment: Assignment? = null
        var rightOperandReplacement: Expression? = null


        // TODO don't optimize simple conditionals that are just a function call

        if(!expr.left.isSimple) {
            val dt = expr.left.inferType(program)
            val name = when {
                dt.istype(DataType.UBYTE) -> listOf("cx16","r9L")       // assume (hope) cx16.r9 isn't used for anything else...
                dt.istype(DataType.UWORD) -> listOf("cx16","r9")        // assume (hope) cx16.r9 isn't used for anything else...
                dt.istype(DataType.BYTE) -> listOf("prog8_lib","retval_interm_b")
                dt.istype(DataType.WORD) -> listOf("prog8_lib","retval_interm_w")
                else -> throw AssemblyError("invalid dt")
            }
            leftOperandReplacement = IdentifierReference(name, expr.position)
            leftAssignment = Assignment(
                AssignTarget(IdentifierReference(name, expr.position), null, null, expr.position),
                expr.left,
                expr.position
            )
        }
        if(!expr.right.isSimple) {
            val dt = expr.right.inferType(program)
            val name = when {
                dt.istype(DataType.UBYTE) -> listOf("prog8_lib","retval_interm_ub")
                dt.istype(DataType.UWORD) -> listOf("prog8_lib","retval_interm_uw")
                dt.istype(DataType.BYTE) -> listOf("prog8_lib","retval_interm_b2")
                dt.istype(DataType.WORD) -> listOf("prog8_lib","retval_interm_w2")
                else -> throw AssemblyError("invalid dt")
            }
            rightOperandReplacement = IdentifierReference(name, expr.position)
            rightAssignment = Assignment(
                AssignTarget(IdentifierReference(name, expr.position), null, null, expr.position),
                expr.right,
                expr.position
            )
        }
        return CondExprSimplificationResult(
            leftAssignment, leftOperandReplacement,
            rightAssignment, rightOperandReplacement
        )
    }

    @Suppress("DuplicatedCode")
    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        val prefixExpr = untilLoop.condition as? PrefixExpression
        if(prefixExpr!=null && prefixExpr.operator=="not") {
            // until not x -> until x==0
            val booleanExpr = BinaryExpression(prefixExpr.expression, "==", NumericLiteralValue.optimalInteger(0, untilLoop.condition.position), untilLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(untilLoop.condition, booleanExpr, untilLoop))
        }

        val binExpr = untilLoop.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in comparisonOperators) {
            // until x  ->  until x!=0,    until x+5  ->  until x+5 != 0
            val booleanExpr = BinaryExpression(untilLoop.condition, "!=", NumericLiteralValue.optimalInteger(0, untilLoop.condition.position), untilLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(untilLoop.condition, booleanExpr, untilLoop))
        }

        if((binExpr.left as? NumericLiteralValue)?.number==0 &&
            (binExpr.right as? NumericLiteralValue)?.number!=0)
            throw FatalAstException("0==X should have been swapped to if X==0")

        // TODO simplify conditional expression like in if-statement

        return noModifications
    }

    @Suppress("DuplicatedCode")
    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        val prefixExpr = whileLoop.condition as? PrefixExpression
        if(prefixExpr!=null && prefixExpr.operator=="not") {
            // while not x -> while x==0
            val booleanExpr = BinaryExpression(prefixExpr.expression, "==", NumericLiteralValue.optimalInteger(0, whileLoop.condition.position), whileLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(whileLoop.condition, booleanExpr, whileLoop))
        }

        val binExpr = whileLoop.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in comparisonOperators) {
            // while x  ->  while x!=0,    while x+5  ->  while x+5 != 0
            val booleanExpr = BinaryExpression(whileLoop.condition, "!=", NumericLiteralValue.optimalInteger(0, whileLoop.condition.position), whileLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(whileLoop.condition, booleanExpr, whileLoop))
        }

        if((binExpr.left as? NumericLiteralValue)?.number==0 &&
            (binExpr.right as? NumericLiteralValue)?.number!=0)
            throw FatalAstException("0==X should have been swapped to if X==0")

        // TODO simplify conditional expression like in if-statement

        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource==listOf("cmp")) {
            // if the datatype of the arguments of cmp() are different, cast the byte one to word.
            val arg1 = functionCallStatement.args[0]
            val arg2 = functionCallStatement.args[1]
            val dt1 = arg1.inferType(program).getOr(DataType.UNDEFINED)
            val dt2 = arg2.inferType(program).getOr(DataType.UNDEFINED)
            if(dt1 in ByteDatatypes) {
                if(dt2 in ByteDatatypes)
                    return noModifications
                val (replaced, cast) = arg1.typecastTo(if(dt1==DataType.UBYTE) DataType.UWORD else DataType.WORD, dt1, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg1, cast, functionCallStatement))
            } else {
                if(dt2 in WordDatatypes)
                    return noModifications
                val (replaced, cast) = arg2.typecastTo(if(dt2==DataType.UBYTE) DataType.UWORD else DataType.WORD, dt2, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg2, cast, functionCallStatement))
            }
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {

        val containingStatement = getContainingStatement(arrayIndexedExpression)
        if(getComplexArrayIndexedExpressions(containingStatement).size > 1) {
            errors.err("it's not possible to use more than one complex array indexing expression in a single statement; break it up via a temporary variable for instance", containingStatement.position)
            return noModifications
        }


        val index = arrayIndexedExpression.indexer.indexExpr
        if(index !is NumericLiteralValue && index !is IdentifierReference) {
            // replace complex indexing expression with a temp variable to hold the computed index first
            return getAutoIndexerVarFor(arrayIndexedExpression)
        }

        return noModifications
    }

    private fun getComplexArrayIndexedExpressions(stmt: Statement): List<ArrayIndexedExpression> {

        class Searcher : IAstVisitor {
            val complexArrayIndexedExpressions = mutableListOf<ArrayIndexedExpression>()
            override fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
                val ix = arrayIndexedExpression.indexer.indexExpr
                if(ix !is NumericLiteralValue && ix !is IdentifierReference)
                    complexArrayIndexedExpressions.add(arrayIndexedExpression)
            }

            override fun visit(branchStatement: BranchStatement) {}

            override fun visit(forLoop: ForLoop) {}

            override fun visit(ifStatement: IfStatement) {
                ifStatement.condition.accept(this)
            }

            override fun visit(untilLoop: UntilLoop) {
                untilLoop.condition.accept(this)
            }
        }

        val searcher = Searcher()
        stmt.accept(searcher)
        return searcher.complexArrayIndexedExpressions
    }

    private fun getContainingStatement(expression: Expression): Statement {
        var node: Node = expression
        while(node !is Statement)
            node = node.parent

        return node
    }

    private fun getAutoIndexerVarFor(expr: ArrayIndexedExpression): MutableList<IAstModification> {
        val modifications = mutableListOf<IAstModification>()
        val statement = expr.containingStatement
        val dt = expr.indexer.indexExpr.inferType(program)
        val register = if(dt istype DataType.UBYTE  || dt istype DataType.BYTE ) "retval_interm_ub" else "retval_interm_b"
        // replace the indexer with just the variable (simply use a cx16 virtual register r9, that we HOPE is not used for other things in the expression...)
        // assign the indexing expression to the helper variable, but only if that hasn't been done already
        val target = AssignTarget(IdentifierReference(listOf("prog8_lib", register), expr.indexer.position), null, null, expr.indexer.position)
        val assign = Assignment(target, expr.indexer.indexExpr, expr.indexer.position)
        modifications.add(IAstModification.InsertBefore(statement, assign, statement.parent as IStatementContainer))
        modifications.add(IAstModification.ReplaceNode(expr.indexer.indexExpr, target.identifier!!.copy(), expr.indexer))
        return modifications
    }

}
