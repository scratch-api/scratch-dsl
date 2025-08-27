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

fun BlockHost.repeatBlock(expression: Expression?, block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(NormalBlockBlockHost("control_repeat", stack)
        .withExpression("TIMES", expression, ValueInput.POSITIVE_INTEGER.of("10")))
}

// VLB

fun BlockHost.setVar(variable: VLB, expression: Expression?) =
    addBlock(NormalBlock("data_setvariableto").apply {
        expressionInputs["VALUE"] = when (expression) {
            is ShadowExpression -> expression to null
            else -> ValueInput
                .TEXT
                .of("0") to expression
        }
        fields["VARIABLE"] = variable
    })



// Operators

fun notBlock(expression: Expression?) =
    NormalUnaryOp("operator_not", expression)

fun round(expression: Expression?) =
    NormalUnaryOp("operator_round", expression, "NUM", ValueInput.NUMBER.of(""))

infix fun Expression.equals(other: Expression?): Expression {
    return NormalBinaryOp(
        "operator_equals",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )
}

