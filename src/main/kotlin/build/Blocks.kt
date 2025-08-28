package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.*

open class NormalBlock(override val opcode: String?) : Block {
    override var next: AnyBlock? = null
    private var myId: String? = null
    override var parent: String? = null
    override var shadow = false
    override var topLevel = false
    val shadowlessExpressionInputs = mutableMapOf<String, Expression?>()
    val expressionInputs = mutableMapOf<String, Pair<ShadowExpression, Expression?>?>()
    val blockStackInputs = mutableMapOf<String, BlockStack?>()
    val fields = mutableMapOf<String, Field?>()
    val mutation = mutableMapOf<String, Representation?>()

    override var id: String
        get() {
            myId?.let {
                return it
            }
            val newId = makeId()
            myId = newId
            return newId
        }
        set(value) { myId = value }

    override fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String?) {
        map[id] = this
        shadowlessExpressionInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            if (u.independent) u.flattenInto(map)
        }
        expressionInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            val (s, e) = u
            if (s.independent) s.flattenInto(map)
            if (e?.independent == true) e.flattenInto(map)
        }
        blockStackInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            u.flattenInto(map, id)
        }
    }

    fun representInputs(): Representation =
        buildJsonObject {
            shadowlessExpressionInputs.forEach { (t, u) ->
                if (u == null) return@forEach
                put(t, u.representAsInput())
            }
            expressionInputs.forEach { (t, u) ->
                if (u == null) return@forEach
                val (s, e) = u
                put(t, s.representAsInputWith(e))
            }
            blockStackInputs.forEach { (t, u) ->
                if (u == null) return@forEach
                if (u.isEmpty()) return@forEach
                u.id = u.contents[0].id
                put(t, buildJsonArray {
                    add(2)
                    add(JsonPrimitive(u.id))
                })
            }
        }

    fun representFields(): Representation =
        buildJsonObject {
            fields.forEach { (t, u) ->
                if (u == null) return@forEach
                put(t, buildJsonArray {
                    add(u.fieldValue.value)
                    add(u.fieldValue.id)
                })
            }
        }

    override fun represent(): Representation =
        buildJsonObject {
            put("inputs", representInputs())
            put("fields", representFields())
            if (mutation.isNotEmpty()) {
                put("mutation", buildJsonObject {
                    mutation.forEach { (t, u) ->
                        if (u == null) return@forEach
                        put(t, u)
                    }
                })
            }
            put("topLevel", topLevel)
            put("next", next?.id)
            put("parent", parent)
            put("shadow", shadow)
            put("opcode", opcode)
            if (topLevel) {
                put("x", 0)
                put("y", 0)
            }
        }

    override fun loadInto(representation: Representation) {
        TODO("Not implemented yet")
    }
}

fun<B: NormalBlock> B.withField(name: String, field: Field) =
    this.apply {
        fields[name] = field
    }

fun<B: NormalBlock> B.withExpression(
    name: String,
    expression: Expression? = null,
    shadowExpression: ShadowExpression? = null
) = apply {
    if (shadowExpression != null && expression !is ShadowExpression) {
        expressionInputs[name] = shadowExpression to expression
    } else {
        if (expression is ValueShadowExpression && shadowExpression is ValueShadowExpression) {
            shadowlessExpressionInputs[name] = ValueShadowExpression(expression.value, shadowExpression.opcode)
            return@apply
        }
        shadowlessExpressionInputs[name] = expression
    }
}

fun<B: NormalBlock> B.withMutation(
    name: String,
    value: Representation? = null
) = apply {
    mutation[name] = value
}

fun<B: NormalBlock> B.withDefaultMutation() =
    withMutation("tagName", JsonPrimitive("mutation"))
        .withMutation("children", JsonArray(listOf()))

open class NormalBlockBlockHost(opcode: String?, val subStack: BlockStack?) : NormalBlock(opcode), BlockBlockHost {
    val blocks = mutableListOf<AnyBlock>()

    init {
        blockStackInputs["SUBSTACK"] = subStack
    }

    override val stacks = listOfNotNull(subStack)

    override fun<B: AnyBlock> addBlock(block: B) = block.apply(stacks[0]::addBlock)
}

open class ConditionalBlockBlockHost(opcode: String?, val expression: Expression?, subStack: BlockStack?) : NormalBlockBlockHost(opcode, subStack) {

    init {
        shadowlessExpressionInputs["CONDITION"] = expression
    }
}

class IfElseBlock(
    expression: Expression?,
    subStack: BlockStack?,
    secondarySubStack: BlockStack
) : ConditionalBlockBlockHost("control_if_else", expression, subStack) {
    init {
        blockStackInputs["SUBSTACK2"] = secondarySubStack
    }
}

class IsolatedBlockStackHat(val blockStack: BlockStack) : HatBlock {
    private val actualBlock: AnyBlock get() {
        if (blockStack.contents.size == 0) throw IllegalStateException("You need to add blocks to the BlockStack before doing that.")
        return blockStack.contents[0]
    }
    override val opcode get() = actualBlock.opcode
    override var next: AnyBlock?
        get() = blockStack.contents.getOrNull(1)
        set(value) { }
    override var parent: String? = null
    override var shadow = false
    override var topLevel = true
    override val stacks = listOf(blockStack)

    override var id: String
        get() = actualBlock.id
        set(value) {
            actualBlock.id = value
        }

    override fun represent(): Representation = actualBlock.represent()

    override fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String?) =
        actualBlock.flattenInto(map)

    override fun<B: AnyBlock> addBlock(block: B) = block.apply(blockStack::addBlock)

    override fun loadInto(representation: Representation) {
        TODO("Not yet implemented")
    }

    override fun prepareRepresent() {
        actualBlock.topLevel = true
    }
}

class HalfIfElse internal constructor(val blockHost: BlockHost, val expression: Expression?, val block: BlockHost.() -> Unit)

// Hats

fun HatBlockHost.isolated(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = IsolatedBlockStackHat(BlockStack().apply(block))
    return addHatBlock(hatBlock)
}

// Control

fun BlockHost.ifBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_if", expression, stack))
}

fun BlockHost.ifElseBlock(expression: Expression?, block: BlockHost.() -> Unit) =
    HalfIfElse(this, expression, block)

infix fun HalfIfElse.elseBlock(block: BlockHost.() -> Unit): IfElseBlock {
    val stack = BlockStack().apply(this.block)
    val secStack = BlockStack().apply(block)
    return blockHost.addBlock(IfElseBlock(expression, stack, secStack))
}

fun BlockHost.untilBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_repeat_until", expression, stack))
}

fun BlockHost.whileBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_while", expression, stack))
}

fun BlockHost.repeatBlock(expression: Expression?, block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(NormalBlockBlockHost("control_repeat", stack)
        .withExpression("TIMES", expression, ValueInput.POSITIVE_INTEGER.of("10")))
}

fun BlockHost.foreverBlock(block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(NormalBlockBlockHost("control_forever", stack))
}

fun BlockHost.waitForBlock(expression: Expression?): NormalBlock {
    return addBlock(NormalBlock("control_wait")
        .withExpression("DURATION", expression, ValueInput.POSITIVE_NUMBER.of("1")))
}

fun BlockHost.waitUntilBlock(expression: Expression?): NormalBlock {
    return addBlock(NormalBlock("control_wait_until")
        .withExpression("CONDITION", expression))
}

enum class StopType(val code: String) {
    ALL("all"),
    THIS_SCRIPT("this script"),
    OTHER_SCRIPTS_IN_SPRITE("other scripts in sprite")
}

fun BlockHost.stopBlock(stopType: StopType = StopType.THIS_SCRIPT): NormalBlock {
    val hasNext = stopType == StopType.OTHER_SCRIPTS_IN_SPRITE
    return addBlock(NormalBlock("control_stop")
        .withField("STOP_OPTION", Field.of(stopType.code))
        .withDefaultMutation()
        .withMutation("hasnext", JsonPrimitive(hasNext)))
}

// VLB

fun BlockHost.setVar(variable: Variable, expression: Expression?) =
    addBlock(NormalBlock("data_setvariableto")
        .withExpression("VALUE", expression, ValueInput.TEXT.of("0"))
        .withField("VARIABLE", variable))

fun BlockHost.changeVar(variable: Variable, expression: Expression?) =
    addBlock(NormalBlock("data_changevariableby")
        .withExpression("VALUE", expression, ValueInput.NUMBER.of("1"))
        .withField("VARIABLE", variable))


fun BlockHost.append(list: ScratchList, value: Expression?) =
    addBlock(NormalBlock("data_addtolist")
        .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
        .withField("LIST", list))

fun BlockHost.deleteAtIndex(list: ScratchList, index: Expression?) =
    addBlock(NormalBlock("data_deleteoflist")
        .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
        .withField("LIST", list))

fun BlockHost.deleteAll(list: ScratchList) =
    addBlock(NormalBlock("data_deletealloflist")
        .withField("LIST", list))

fun BlockHost.insertAtIndex(list: ScratchList, value: Expression?, index: Expression?) =
    addBlock(NormalBlock("data_insertatlist")
        .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
        .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
        .withField("LIST", list))

fun BlockHost.replaceAtIndex(list: ScratchList, value: Expression?, index: Expression?) =
    addBlock(NormalBlock("data_replaceitemoflist")
        .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
        .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
        .withField("LIST", list))

// Operators

fun notBlock(expression: Expression?) =
    NormalUnaryOp("operator_not", expression)

fun round(expression: Expression?) =
    NormalUnaryOp("operator_round", expression, "NUM", ValueInput.NUMBER.of(""))

operator fun Expression?.plus(other: Expression?) =
    NormalBinaryOp(
        "operator_add",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

operator fun Expression?.minus(other: Expression?) =
    NormalBinaryOp(
        "operator_subtract",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

operator fun Expression?.times(other: Expression?) =
    NormalBinaryOp(
        "operator_multiply",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

operator fun Expression?.div(other: Expression?) =
    NormalBinaryOp(
        "operator_divide",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

operator fun Expression?.rangeTo(other: Expression?) =
    NormalBinaryOp(
        "operator_random",
        this,
        other,
        "FROM",
        "TO",
        ValueInput.NUMBER.of("1"),
        ValueInput.NUMBER.of("10")
    )

infix fun Expression?.greaterThan(other: Expression?) =
    NormalBinaryOp(
        "operator_gt",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )

infix fun Expression?.lessThan(other: Expression?) =
    NormalBinaryOp(
        "operator_lt",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )

infix fun Expression?.equals(other: Expression?) =
    NormalBinaryOp(
        "operator_equals",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )

infix fun Expression?.and(other: Expression?) =
    NormalBinaryOp(
        "operator_and",
        this,
        other,
        "OPERAND1",
        "OPERAND2"
    )

infix fun Expression?.or(other: Expression?) =
    NormalBinaryOp(
        "operator_or",
        this,
        other,
        "OPERAND1",
        "OPERAND2"
    )

infix fun Expression?.join(other: Expression?) =
    NormalBinaryOp(
        "operator_join",
        this,
        other,
        "STRING1",
        "STRING2",
        ValueInput.TEXT.of("apple "),
        ValueInput.TEXT.of("banana")
    )

infix fun Expression?.letterOf(other: Expression?) =
    NormalBinaryOp(
        "operator_letter_of",
        this,
        other,
        "LETTER",
        "STRING",
        ValueInput.POSITIVE_INTEGER.of("1"),
        ValueInput.TEXT.of("apple")
    )

infix fun Expression?.contains(other: Expression?) =
    NormalBinaryOp(
        "operator_contains",
        this,
        other,
        "STRING1",
        "STRING2",
        ValueInput.TEXT.of("apple"),
        ValueInput.TEXT.of("a")
    )

operator fun Expression?.rem(other: Expression?) =
    NormalBinaryOp(
        "operator_mod",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )
