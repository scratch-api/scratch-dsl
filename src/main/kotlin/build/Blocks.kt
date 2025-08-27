package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.*
import java.util.UUID

abstract class NormalBlock : Block {
    private var myId: String? = null
    private val shadowlessExpressionInputs = mutableMapOf<String, Expression>()
    private val expressionInputs = mutableMapOf<String, Pair<ShadowExpression, Expression?>>()
    private val blockBlockHostInputs = mutableMapOf<String, BlockBlockHost>()

    override fun getId(): String {
        myId?.let {
            return it
        }
        val newId = UUID.randomUUID().toString()
        myId = newId
        return newId
    }

    override fun flattenInto(map: MutableMap<String, AnyBlock>) {
        map[getId()] = this
        shadowlessExpressionInputs.forEach { (_, u) ->
            if (u.independent) u.flattenInto(map)
        }
        expressionInputs.forEach { _, (s, e) ->
            if (s.independent) s.flattenInto(map)
            if (e?.independent == true) e.flattenInto(map)
        }
        blockBlockHostInputs.forEach { (_, u) ->
            u.flattenInto(map)
        }
    }

    fun representInputs(): Representation =
        buildJsonObject {
            shadowlessExpressionInputs.forEach { (t, u) ->
                put(t, u.representAsInput())
            }
            expressionInputs.forEach { t, (s, e) ->
                put(t, )
            }
            blockBlockHostInputs.forEach { (t, u) ->
                put(t, JsonPrimitive(u.getId()))
            }
        }
}

abstract class NormalBlockBlockHost : BlockBlockHost {
    val blocks = mutableListOf<AnyBlock>()

    open fun addInputs(jsonObjectBuilder: JsonObjectBuilder) {
        jsonObjectBuilder.put("SUBSTACK", buildJsonArray {
            add(2)
            add(blocks[0].getId())
        })
    }

    override fun represent(): Representation {
        return buildJsonObject {
            addInputs(this)
        }
    }
}

abstract class ConditionalBlockBlockHost() : NormalBlockBlockHost() {
    abstract val conditionExpression: Block

    override fun addInputs(jsonObjectBuilder: JsonObjectBuilder) {
        super.addInputs(jsonObjectBuilder)
        jsonObjectBuilder.put("CONDITION", buildJsonArray {
            add(2)
            add(conditionExpression.getId())
        })
    }
}


fun BlockHost.ifBlock() = 1