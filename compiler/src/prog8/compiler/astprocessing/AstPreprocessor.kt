package prog8.compiler.astprocessing

import prog8.ast.IPipe
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


class AstPreprocessor(val program: Program, val errors: IErrorReporter, val compTarget: ICompilationTarget) : AstWalker() {

    override fun before(char: CharLiteral, parent: Node): Iterable<IAstModification> {
        if(char.encoding== Encoding.DEFAULT)
            char.encoding = compTarget.defaultEncoding
        return noModifications
    }

    override fun before(string: StringLiteral, parent: Node): Iterable<IAstModification> {
        if(string.encoding==Encoding.DEFAULT)
            string.encoding = compTarget.defaultEncoding
        return super.before(string, parent)
    }

    override fun before(pipe: Pipe, parent: Node): Iterable<IAstModification> {
        if(pipe.source is PipeExpression) {
            // correct Antlr parse tree quirk: turn nested pipe into single flat pipe
            val psrc = pipe.source as PipeExpression
            val newSource = psrc.source
            val newSegments = psrc.segments
            newSegments += pipe.segments.single()
            return listOf(IAstModification.ReplaceNode(pipe as Node, Pipe(newSource, newSegments, pipe.position), parent))
        } else if(pipe.source is IPipe)
            throw InternalCompilerException("pipe source should have been adjusted to be a normal expression")
        return noModifications
    }

    override fun before(pipeExpr: PipeExpression, parent: Node): Iterable<IAstModification> {
        if(pipeExpr.source is PipeExpression) {
            // correct Antlr parse tree quirk; turn nested pipe into single flat pipe
            val psrc = pipeExpr.source as PipeExpression
            val newSource = psrc.source
            val newSegments = psrc.segments
            newSegments += pipeExpr.segments.single()
            return listOf(IAstModification.ReplaceNode(pipeExpr as Node, PipeExpression(newSource, newSegments, pipeExpr.position), parent))
        } else if(pipeExpr.source is IPipe)
            throw InternalCompilerException("pipe source should have been adjusted to be a normal expression")
        return noModifications
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<IAstModification> {
        // has to be done before the constant folding, otherwise certain checks there will fail on invalid range sizes
        val modifications = mutableListOf<IAstModification>()
        if(range.from !is NumericLiteral) {
            try {
                val constval = range.from.constValue(program)
                if (constval != null)
                    modifications += IAstModification.ReplaceNode(range.from, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.to !is NumericLiteral) {
            try {
                val constval = range.to.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.to, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.step !is NumericLiteral) {
            try {
                val constval = range.step.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.step, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        return modifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {

        // move vardecls in Anonymous scope up to the containing subroutine
        // and add initialization assignment in its place if needed
        val vars = scope.statements.asSequence().filterIsInstance<VarDecl>()
        val parentscope = scope.definingScope
        if(vars.any() && parentscope !== parent) {
            val movements = mutableListOf<IAstModification>()
            val replacements = mutableListOf<IAstModification>()

            for(decl in vars) {
                if(decl.type != VarDeclType.VAR) {
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                    replacements.add(IAstModification.Remove(decl, scope))
                } else {
                    if(decl.value!=null && decl.datatype in NumericDatatypes) {
                        val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                        val assign = Assignment(target, decl.value!!, AssignmentOrigin.VARINIT, decl.position)
                        replacements.add(IAstModification.ReplaceNode(decl, assign, scope))
                        decl.value = null
                        decl.allowInitializeWithZero = false
                    } else {
                        replacements.add(IAstModification.Remove(decl, scope))
                    }
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                }
            }
            return movements + replacements
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // this has to be done here becuse otherwise the string / range literal values will have been replaced by variables
        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, containment, parent))
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val nextAssignment = decl.nextSibling() as? Assignment
        if(nextAssignment!=null && nextAssignment.origin!=AssignmentOrigin.VARINIT) {
            // check if it's a proper initializer assignment for the variable
            if(decl.value==null && nextAssignment.target.identifier?.targetVarDecl(program)===decl) {
                if(!nextAssignment.value.referencesIdentifier(nextAssignment.target.identifier!!.nameInSource))
                    nextAssignment.origin = AssignmentOrigin.VARINIT
            }
        }
        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // For non-kernal subroutines and non-asm parameters:
        // inject subroutine params as local variables (if they're not there yet).
        val symbolsInSub = subroutine.allDefinedSymbols
        val namesInSub = symbolsInSub.map{ it.first }.toSet()
        if(subroutine.asmAddress==null) {
            if(!subroutine.isAsmSubroutine && subroutine.parameters.isNotEmpty()) {
                val vars = subroutine.statements.asSequence().filterIsInstance<VarDecl>().map { it.name }.toSet()
                if(!vars.containsAll(subroutine.parameters.map{it.name})) {
                    return subroutine.parameters
                        .filter { it.name !in namesInSub }
                        .map {
                            val vardecl = VarDecl.fromParameter(it)
                            IAstModification.InsertFirst(vardecl, subroutine)
                        }
                }
            }
        }

        return noModifications
    }
}
