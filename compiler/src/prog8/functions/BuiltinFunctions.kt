package prog8.functions

import prog8.ast.*
import prog8.compiler.HeapValues
import kotlin.math.log2



class BuiltinFunctionParam(val name: String, val possibleDatatypes: List<DataType>)

class FunctionSignature(val pure: Boolean,      // does it have side effects?
                        val parameters: List<BuiltinFunctionParam>,
                        val returntype: DataType?,
                        val constExpressionFunc: ((args: List<IExpression>, position: Position, namespace: INameScope, heap: HeapValues) -> LiteralValue)? = null)


val BuiltinFunctions = mapOf(
    "rol"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.UBYTE, DataType.UWORD))), null),
    "rol2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.UBYTE, DataType.UWORD))), null),
    "lsl"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.UBYTE, DataType.UWORD))), null),
    "lsr"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.UBYTE, DataType.UWORD))), null),
    "sin"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::sin) },
    "cos"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::cos) },
    "acos"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::acos) },
    "asin"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::asin) },
    "tan"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::tan) },
    "atan"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::atan) },
    "ln"          to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::log) },
    "log2"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, ::log2) },
    "log10"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::log10) },
    "sqrt"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::sqrt) },
    "rad"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::toRadians) },
    "deg"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::toDegrees) },
    "avg"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes.toList())), DataType.FLOAT, ::builtinAvg),
    "abs"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT, ::builtinAbs),
    "round"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.WORD) { a, p, n, h -> oneDoubleArgOutputWord(a, p, n, h, Math::round) },
    "floor"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.WORD) { a, p, n, h -> oneDoubleArgOutputWord(a, p, n, h, Math::floor) },
    "ceil"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.WORD) { a, p, n, h -> oneDoubleArgOutputWord(a, p, n, h, Math::ceil) },
    "max"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes.toList())), null) { a, p, n, h -> collectionArgOutputNumber(a, p, n, h) { it.max()!! }},        // type depends on args
    "min"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes.toList())), null) { a, p, n, h -> collectionArgOutputNumber(a, p, n, h) { it.min()!! }},        // type depends on args
    "sum"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes.toList())), null) { a, p, n, h -> collectionArgOutputNumber(a, p, n, h) { it.sum() }},        // type depends on args
    "len"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", IterableDatatypes.toList())), DataType.UWORD, ::builtinLen),
    "any"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes.toList())), DataType.UBYTE) { a, p, n, h -> collectionArgOutputBoolean(a, p, n, h) { it.any { v -> v != 0.0} }},
    "all"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes.toList())), DataType.UBYTE) { a, p, n, h -> collectionArgOutputBoolean(a, p, n, h) { it.all { v -> v != 0.0} }},
    "lsb"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE) { a, p, n, h -> oneIntArgOutputInt(a, p, n, h) { x: Int -> x and 255 }},
    "msb"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE) { a, p, n, h -> oneIntArgOutputInt(a, p, n, h) { x: Int -> x ushr 8 and 255}},
    "flt"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", NumericDatatypes.toList())), DataType.FLOAT, ::builtinFlt),
    "uwrd"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.UBYTE, DataType.BYTE, DataType.WORD))), DataType.UWORD, ::builtinUwrd),
    "wrd"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD))), DataType.WORD, ::builtinWrd),
    "uwrdhi"      to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.UBYTE, DataType.BYTE))), DataType.UWORD, ::builtinWrdHi),
    "b2ub"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.BYTE))), DataType.UBYTE, ::builtinB2ub),
    "ub2b"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.UBYTE))), DataType.BYTE, ::builtinUb2b),
    "rnd"         to FunctionSignature(true, emptyList(), DataType.UBYTE),
    "rndw"        to FunctionSignature(true, emptyList(), DataType.UWORD),
    "rndf"        to FunctionSignature(true, emptyList(), DataType.FLOAT),
    "set_carry"   to FunctionSignature(false, emptyList(), null),
    "clear_carry" to FunctionSignature(false, emptyList(), null),
    "set_irqd"    to FunctionSignature(false, emptyList(), null),
    "clear_irqd"  to FunctionSignature(false, emptyList(), null),
    "str2byte"    to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes.toList())), DataType.BYTE),
    "str2ubyte"   to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes.toList())), DataType.UBYTE),
    "str2word"    to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes.toList())), DataType.WORD),
    "str2uword"   to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes.toList())), DataType.UWORD),
    "str2float"   to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes.toList())), DataType.FLOAT),
    "_vm_write_memchr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("address", listOf(DataType.UWORD))), null),
    "_vm_write_memstr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("address", listOf(DataType.UWORD))), null),
    "_vm_write_num"     to FunctionSignature(false, listOf(BuiltinFunctionParam("number", NumericDatatypes.toList())), null),
    "_vm_write_char"    to FunctionSignature(false, listOf(BuiltinFunctionParam("char", listOf(DataType.UBYTE))), null),
    "_vm_write_str"     to FunctionSignature(false, listOf(BuiltinFunctionParam("string", StringDatatypes.toList())), null),
    "_vm_input_str"     to FunctionSignature(false, listOf(BuiltinFunctionParam("intovar", StringDatatypes.toList())), null),
    "_vm_gfx_clearscr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("color", listOf(DataType.UBYTE))), null),
    "_vm_gfx_pixel"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("y", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("color", IntegerDatatypes.toList())), null),
    "_vm_gfx_line"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x1", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("y1", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("x2", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("y2", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("color", IntegerDatatypes.toList())), null),
    "_vm_gfx_text"      to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("y", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("color", IntegerDatatypes.toList()),
                                                        BuiltinFunctionParam("text", StringDatatypes.toList())),
                                                        null)
)


fun builtinFunctionReturnType(function: String, args: List<IExpression>, namespace: INameScope, heap: HeapValues): DataType? {

    fun datatypeFromListArg(arglist: IExpression): DataType {
        if(arglist is LiteralValue) {
            if(arglist.type==DataType.ARRAY_UB || arglist.type==DataType.ARRAY_UW || arglist.type==DataType.ARRAY_F || arglist.type==DataType.MATRIX_UB) {
                val dt = arglist.arrayvalue!!.map {it.resultingDatatype(namespace, heap)}
                if(dt.any { it!=DataType.UBYTE && it!=DataType.UWORD && it!=DataType.FLOAT}) {
                    throw FatalAstException("fuction $function only accepts array of numeric values")
                }
                if(dt.any { it==DataType.FLOAT }) return DataType.FLOAT
                if(dt.any { it==DataType.UWORD }) return DataType.UWORD
                return DataType.UBYTE
            }
        }
        if(arglist is IdentifierReference) {
            val dt = arglist.resultingDatatype(namespace, heap)
            return when(dt) {
                DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT,
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> dt
                DataType.ARRAY_UB -> DataType.UBYTE
                DataType.ARRAY_B -> DataType.BYTE
                DataType.ARRAY_UW -> DataType.UWORD
                DataType.ARRAY_W -> DataType.WORD
                DataType.ARRAY_F -> DataType.FLOAT
                DataType.MATRIX_UB -> DataType.UBYTE
                DataType.MATRIX_B -> DataType.BYTE
                null -> throw FatalAstException("function requires one argument which is an array $function")
            }
        }
        throw FatalAstException("function requires one argument which is an array $function")
    }

    val func = BuiltinFunctions[function]!!
    if(func.returntype!=null)
        return func.returntype
    // function has return values, but the return type depends on the arguments

    return when (function) {
        "max", "min" -> {
            val dt = datatypeFromListArg(args.single())
            when(dt) {
                DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT -> dt
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.UBYTE
                DataType.ARRAY_UB -> DataType.UBYTE
                DataType.ARRAY_B -> DataType.BYTE
                DataType.ARRAY_UW -> DataType.UWORD
                DataType.ARRAY_W -> DataType.WORD
                DataType.ARRAY_F -> DataType.FLOAT
                DataType.MATRIX_UB -> DataType.UBYTE
                DataType.MATRIX_B -> DataType.BYTE
            }
        }
        "sum" -> {
            val dt=datatypeFromListArg(args.single())
            when(dt) {
                DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                DataType.BYTE, DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                DataType.ARRAY_UB, DataType.ARRAY_UW -> DataType.UWORD
                DataType.ARRAY_B, DataType.ARRAY_W -> DataType.WORD
                DataType.ARRAY_F -> DataType.FLOAT
                DataType.MATRIX_UB -> DataType.UWORD
                DataType.MATRIX_B -> DataType.WORD
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.UWORD
            }
        }
        else -> throw FatalAstException("unknown result type for builtin function $function")
    }
}


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.FLOAT)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = constval.asNumericValue?.toDouble()!!
    return numericLiteral(function(float), args[0].position)
}

private fun oneDoubleArgOutputWord(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.FLOAT)
        throw SyntaxError("built-in function requires one floating point argument", position)
    return LiteralValue(DataType.WORD, wordvalue=function(constval.asNumericValue!!.toDouble()).toInt(), position=args[0].position)
}

private fun oneIntArgOutputInt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues, function: (arg: Int)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.UBYTE && constval.type!=DataType.UWORD)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.asNumericValue?.toInt()!!
    return numericLiteral(function(integer).toInt(), args[0].position)
}

private fun collectionArgOutputNumber(args: List<IExpression>, position: Position,
                                      namespace:INameScope, heap: HeapValues,
                                      function: (arg: Collection<Double>)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue.map { it.constValue(namespace, heap)?.asNumericValue }
        if(null in constants)
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() }).toDouble()
    } else {
        when(iterable.type) {
            DataType.UBYTE, DataType.UWORD, DataType.FLOAT -> throw SyntaxError("function expects an iterable type", position)
            else -> {
                if(iterable.heapId==null)
                    throw FatalAstException("iterable value should be on the heap")
                val array = heap.get(iterable.heapId).array ?: throw SyntaxError("function expects an iterable type", position)
                function(array.map { it.toDouble() })
            }
        }
    }
    return numericLiteral(result, args[0].position)
}

private fun collectionArgOutputBoolean(args: List<IExpression>, position: Position,
                                       namespace:INameScope, heap: HeapValues,
                                       function: (arg: Collection<Double>)->Boolean): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue.map { it.constValue(namespace, heap)?.asNumericValue }
        if(null in constants)
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() })
    } else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("function requires array/matrix argument", position)
        function(array.map { it.toDouble() })
    }
    return LiteralValue.fromBoolean(result, position)
}

private fun builtinFlt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 numeric arg, convert to float
    if(args.size!=1)
        throw SyntaxError("flt requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue ?: throw SyntaxError("flt requires one numeric argument", position)
    return LiteralValue(DataType.FLOAT, floatvalue = number.toDouble(), position = position)
}

private fun builtinWrd(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 byte arg, convert to word
    if(args.size!=1)
        throw SyntaxError("wrd requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.UBYTE && constval.type!=DataType.BYTE && constval.type!=DataType.UWORD)
        throw SyntaxError("wrd requires one argument of type ubyte, byte or uword", position)
    return LiteralValue(DataType.WORD, wordvalue = constval.bytevalue!!.toInt(), position = position)
}

private fun builtinUwrd(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 arg, convert to uword
    if(args.size!=1)
        throw SyntaxError("uwrd requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.BYTE && constval.type!=DataType.WORD && constval.type!=DataType.UWORD)
        throw SyntaxError("uwrd requires one argument of type byte, word or uword", position)
    return LiteralValue(DataType.UWORD, wordvalue = constval.bytevalue!!.toInt(), position = position)
}

private fun builtinWrdHi(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 arg, convert to word (in hi byte)
    if(args.size!=1)
        throw SyntaxError("wrdhi requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.UBYTE)
        throw SyntaxError("wrdhi requires one byte argument", position)
    return LiteralValue(DataType.UWORD, wordvalue = constval.bytevalue!!.toInt() shl 8, position = position)
}

private fun builtinB2ub(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 byte arg, convert to ubyte
    if(args.size!=1)
        throw SyntaxError("b2ub requires one byte argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.BYTE)
        throw SyntaxError("b2ub requires one argument of type byte", position)
    return LiteralValue(DataType.UBYTE, bytevalue=constval.bytevalue!!, position = position)
}

private fun builtinUb2b(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 ubyte arg, convert to byte
    if(args.size!=1)
        throw SyntaxError("ub2b requires one ubyte argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.UBYTE)
        throw SyntaxError("ub2b requires one argument of type ubyte", position)
    return LiteralValue(DataType.BYTE, bytevalue=constval.bytevalue!!, position = position)
}

private fun builtinAbs(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 arg, type = float or int, result type= same as argument type
    if(args.size!=1)
        throw SyntaxError("abs requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue
    return when (number) {
        is Int, is Byte, is Short -> numericLiteral(Math.abs(number.toInt()), args[0].position)
        is Double -> numericLiteral(Math.abs(number.toDouble()), args[0].position)
        else -> throw SyntaxError("abs requires one numeric argument", position)
    }
}

private fun builtinAvg(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("avg requires array/matrix argument", position)
    val iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue!=null) {
        val constants = iterable.arrayvalue.map { it.constValue(namespace, heap)?.asNumericValue }
        if (null in constants)
            throw NotConstArgumentException()
        (constants.map { it!!.toDouble() }).average()
    }
    else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("avg requires array/matrix argument", position)
        array.average()
    }
    return numericLiteral(result, args[0].position)
}

private fun builtinLen(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)
    var argument = args[0].constValue(namespace, heap)
    if(argument==null) {
        if(args[0] !is IdentifierReference)
            throw SyntaxError("len over weird argument ${args[0]}", position)
        argument = ((args[0] as IdentifierReference).targetStatement(namespace) as? VarDecl)?.value?.constValue(namespace, heap)
                ?: throw SyntaxError("len over weird argument ${args[0]}", position)
    }
    return when(argument.type) {
        DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.MATRIX_UB, DataType.MATRIX_B -> {
            val arraySize = argument.arrayvalue?.size ?: heap.get(argument.heapId!!).array!!.size
            LiteralValue(DataType.UWORD, wordvalue=arraySize, position=args[0].position)
        }
        DataType.ARRAY_F -> {
            val arraySize = argument.arrayvalue?.size ?: heap.get(argument.heapId!!).doubleArray!!.size
            LiteralValue(DataType.UWORD, wordvalue=arraySize, position=args[0].position)
        }
        DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
            val str = argument.strvalue ?: heap.get(argument.heapId!!).str!!
            LiteralValue(DataType.UWORD, wordvalue=str.length, position=args[0].position)
        }
        DataType.UBYTE, DataType.BYTE,
        DataType.UWORD, DataType.WORD,
        DataType.FLOAT -> throw SyntaxError("len of weird argument ${args[0]}", position)
    }
}

private fun numericLiteral(value: Number, position: Position): LiteralValue {
    val floatNum=value.toDouble()
    val tweakedValue: Number =
            if(floatNum==Math.floor(floatNum) && floatNum in -32768..65535)
                floatNum.toInt()  // we have an integer disguised as a float.
            else
                floatNum

    return when(tweakedValue) {
        is Int -> LiteralValue.optimalNumeric(value.toInt(), position)
        is Short -> LiteralValue.optimalNumeric(value.toInt(), position)
        is Byte -> LiteralValue(DataType.UBYTE, bytevalue = value.toShort(), position = position)
        is Double -> LiteralValue(DataType.FLOAT, floatvalue = value.toDouble(), position = position)
        is Float -> LiteralValue(DataType.FLOAT, floatvalue = value.toDouble(), position = position)
        else -> throw FatalAstException("invalid number type ${value::class}")
    }
}
