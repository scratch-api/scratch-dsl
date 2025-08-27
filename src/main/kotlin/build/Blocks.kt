package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.*

abstract class NormalBlock(override val opcode: String?) : Block {
    override var next: AnyBlock? = null
    private var myId: String? = null
    override var parent: AnyBlock? = null
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

    override fun flattenInto(map: MutableMap<String, AnyBlock>) {
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
            u.flattenInto(map)
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
                put(t, if (u.isNotEmpty()) JsonPrimitive(u.id) else JsonNull)
            }
        }

    fun representFields(): Representation =
        buildJsonObject {
            fields.forEach { (t, u) ->
                if (u == null) return@forEach
                put(t, buildJsonArray {
                    u.fieldValue.value
                    u.fieldValue.id
                })
            }
        }

    override fun represent(): Representation =
        buildJsonObject {
            put("inputs", representInputs())
            put("fields", representFields())
            put("topLevel", topLevel)
            put("next", next?.id)
            put("parent", parent?.id)
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

abstract class NormalBlockBlockHost(opcode: String?, val subStack: BlockStack?) : NormalBlock(opcode), BlockBlockHost {
    val blocks = mutableListOf<AnyBlock>()

    init {
        blockStackInputs["SUBSTACK"] = subStack
    }

    override val stacks = listOfNotNull(subStack)

    override fun addBlock(block: AnyBlock) {
        stacks[0].addBlock(block)
    }
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
    override var parent: AnyBlock? = null
    override var shadow = false
    override var topLevel = true
    override val stacks = listOf(blockStack)

    override var id: String by actualBlock::id

    override fun represent(): Representation = actualBlock.represent()

    override fun flattenInto(map: MutableMap<String, AnyBlock>) =
        actualBlock.flattenInto(map)

    override fun addBlock(block: AnyBlock) =
        blockStack.addBlock(block)

    override fun loadInto(representation: Representation) {
        TODO("Not yet implemented")
    }

    override fun prepareRepresent() {
        actualBlock.topLevel = true
    }
}

fun HatBlockHost.isolated(block: BlockHost.() -> Unit) {
    val hatBlock = IsolatedBlockStackHat(BlockStack().apply(block))
    addHatBlock(hatBlock)
}

fun BlockHost.ifBlock(expression: Expression, block: BlockHost.() -> Unit) {
    val stack = BlockStack().apply(block)
    addBlock(ConditionalBlockBlockHost("control_if", expression, stack))
}

fun BlockHost.notBlock(expression: Expression?) =
    NormalUnaryOp("operator_not", expression)

