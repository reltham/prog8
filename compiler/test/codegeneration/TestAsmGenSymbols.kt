package prog8tests.codegeneration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.code.ast.PtAddressOf
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtIdentifier
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8.code.target.c64.C64Zeropage
import prog8.codegen.cpu6502.AsmGen
import prog8.compiler.astprocessing.IntermediateAstMaker
import prog8.compiler.astprocessing.SymbolTableMaker
import prog8tests.helpers.*

class TestAsmGenSymbols: StringSpec({
    fun createTestProgram(): Program {
        /*
    main  {

    label_outside:
    uword var_outside

    sub start () {
        uword localvar = 1234
        uword tgt

    locallabel:
        tgt = localvar
        tgt = &locallabel
        tgt = &var_outside
        tgt = &label_outside
        tgt = &main.start.localvar
        tgt = &main.start.locallabel
        tgt = &main.var_outside
        tgt = &main.label_outside
    }
    }

         */
        val varInSub = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.UWORD, ZeropageWish.DONTCARE, null, "localvar", NumericLiteral.optimalInteger(1234, Position.DUMMY), false, false, null, Position.DUMMY)
        val var2InSub = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.UWORD, ZeropageWish.DONTCARE, null, "tgt", null, false, false, null, Position.DUMMY)
        val labelInSub = Label("locallabel", Position.DUMMY)

        val tgt = AssignTarget(IdentifierReference(listOf("tgt"), Position.DUMMY), null, null, Position.DUMMY)
        val assign1 = Assignment(tgt, IdentifierReference(listOf("localvar"), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign2 = Assignment(tgt, AddressOf(IdentifierReference(listOf("locallabel"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign3 = Assignment(tgt, AddressOf(IdentifierReference(listOf("var_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign4 = Assignment(tgt, AddressOf(IdentifierReference(listOf("label_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign5 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","start","localvar"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign6 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","start","locallabel"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign7 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","var_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign8 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","label_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)

        val statements = mutableListOf(varInSub, var2InSub, labelInSub, assign1, assign2, assign3, assign4, assign5, assign6, assign7, assign8)
        val subroutine = Subroutine("start", mutableListOf(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, statements, Position.DUMMY)
        val labelInBlock = Label("label_outside", Position.DUMMY)
        val varInBlock = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.UWORD, ZeropageWish.DONTCARE, null, "var_outside", null, false, false, null, Position.DUMMY)
        val block = Block("main", null, mutableListOf(labelInBlock, varInBlock, subroutine), false, Position.DUMMY)

        val module = Module(mutableListOf(block), Position.DUMMY, SourceCode.Generated("test"))
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder).addModule(module)

        return program
    }

    fun createTestAsmGen(program: Program): AsmGen {
        val errors = ErrorReporterForTests()
        val options = CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, true, C64Target(), 999u)
        options.compTarget.machine.zeropage = C64Zeropage(options)
        val st = SymbolTableMaker(program, options).make()
        val ptProgram = IntermediateAstMaker(program, st, options).transform()
        return AsmGen(ptProgram, st, options, errors)
    }

    "symbol and variable names from strings" {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        asmgen.asmSymbolName("name") shouldBe "name"
        asmgen.asmSymbolName("name") shouldBe "name"
        asmgen.asmSymbolName("<name>") shouldBe "prog8_name"
        asmgen.asmSymbolName(RegisterOrPair.R15) shouldBe "cx16.r15"
        asmgen.asmSymbolName(listOf("a", "b", "name")) shouldBe "a.b.name"
        asmgen.asmVariableName("name") shouldBe "name"
        asmgen.asmVariableName("<name>") shouldBe "prog8_name"
        asmgen.asmVariableName(listOf("a", "b", "name")) shouldBe "a.b.name"
    }

    "symbol and variable names from variable identifiers" {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        val sub = asmgen.program.entrypoint()!!

        val localvarIdent = sub.children.asSequence().filterIsInstance<PtAssignment>().first { it.value is PtIdentifier }.value as PtIdentifier
        asmgen.asmSymbolName(localvarIdent) shouldBe "localvar"
        asmgen.asmVariableName(localvarIdent) shouldBe "localvar"
        val localvarIdentScoped = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="main.start.localvar" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(localvarIdentScoped) shouldBe "main.start.localvar"
        asmgen.asmVariableName(localvarIdentScoped) shouldBe "main.start.localvar"

        val scopedVarIdent = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="var_outside" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(scopedVarIdent) shouldBe "var_outside"
        asmgen.asmVariableName(scopedVarIdent) shouldBe "var_outside"
        val scopedVarIdentScoped = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="main.var_outside" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(scopedVarIdentScoped) shouldBe "main.var_outside"
        asmgen.asmVariableName(scopedVarIdentScoped) shouldBe "main.var_outside"
    }

    "symbol and variable names from label identifiers" {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        val sub = asmgen.program.entrypoint()!!

        val localLabelIdent = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="locallabel" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(localLabelIdent) shouldBe "locallabel"
        asmgen.asmVariableName(localLabelIdent) shouldBe "locallabel"
        val localLabelIdentScoped = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="main.start.locallabel" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(localLabelIdentScoped) shouldBe "main.start.locallabel"
        asmgen.asmVariableName(localLabelIdentScoped) shouldBe "main.start.locallabel"

        val scopedLabelIdent = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="label_outside" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(scopedLabelIdent) shouldBe "label_outside"
        asmgen.asmVariableName(scopedLabelIdent) shouldBe "label_outside"
        val scopedLabelIdentScoped = (sub.children.asSequence().filterIsInstance<PtAssignment>().first { (it.value as? PtAddressOf)?.identifier?.name=="main.label_outside" }.value as PtAddressOf).identifier
        asmgen.asmSymbolName(scopedLabelIdentScoped) shouldBe "main.label_outside"
        asmgen.asmVariableName(scopedLabelIdentScoped) shouldBe "main.label_outside"
    }

    "asm names for hooks to zp temp vars" {
        /*
main {

    sub start() {
        prog8_lib.P8ZP_SCRATCH_REG = 1
        prog8_lib.P8ZP_SCRATCH_B1 = 1
        prog8_lib.P8ZP_SCRATCH_W1 = 1
        prog8_lib.P8ZP_SCRATCH_W2 = 1
         */
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        asmgen.asmSymbolName("prog8_lib.P8ZP_SCRATCH_REG") shouldBe "P8ZP_SCRATCH_REG"
        asmgen.asmSymbolName("prog8_lib.P8ZP_SCRATCH_W2") shouldBe "P8ZP_SCRATCH_W2"
        asmgen.asmSymbolName(listOf("prog8_lib","P8ZP_SCRATCH_REG")) shouldBe "P8ZP_SCRATCH_REG"
        asmgen.asmSymbolName(listOf("prog8_lib","P8ZP_SCRATCH_W2")) shouldBe "P8ZP_SCRATCH_W2"
        val id1 = PtIdentifier("prog8_lib.P8ZP_SCRATCH_REG", DataType.UBYTE, Position.DUMMY)
        val id2 = PtIdentifier("prog8_lib.P8ZP_SCRATCH_W2", DataType.UWORD, Position.DUMMY)
        asmgen.asmSymbolName(id1) shouldBe "P8ZP_SCRATCH_REG"
        asmgen.asmSymbolName(id2) shouldBe "P8ZP_SCRATCH_W2"
    }

    "no double labels with various loops" {
        val text="""
            main  {
                sub start() {
                    if true {
                    }
            
                    repeat 4 {
                    }
            
                    while true {
                    }
            
                    repeat {
                    }
                }
            }            
        """
        val result = compileText(C64Target(), false, text, writeAssembly = true)
        result shouldNotBe null
    }

    "identifiers can have the names of cpu instructions" {
        val text="""
%import textio

nop {
    sub lda(ubyte sec) -> ubyte {
asl:
        ubyte brk = sec
        sec++
        brk += sec
        return brk
    }
}

main {

    sub ffalse(ubyte arg) -> ubyte {
        arg++
        return 0
    }
    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 128
    }

    sub start() {
        ubyte col = 10
        ubyte row = 20
        txt.print_ub(nop.lda(42))
        txt.nl()
        txt.print_uw(nop.lda.asl)

        void ffalse(99)
        void ftrue(99)
    }
}
"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)
        result shouldNotBe null
        val result2 = compileText(VMTarget(), false, text, writeAssembly = true)
        result2 shouldNotBe null

    }
})
