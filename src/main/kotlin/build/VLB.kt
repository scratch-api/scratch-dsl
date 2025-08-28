package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

internal enum class VLBVariant(val numericType: Int) {
    VARIABLE(12),
    LIST(13),
    BROADCAST(11);
}

sealed class VLB(opcode: String?, val name: String, private val variant: VLBVariant) : NormalExpression(opcode), Field {
    override val fieldValue get() = Field.Companion.FieldValue(name, id)

    override val independent = false

    override fun representAlone(): Representation =
        buildJsonArray {
            add(variant.numericType)
            add(name)
            add(id)
        }
}

class Variable internal constructor(name: String) : VLB(null, name, VLBVariant.VARIABLE)

class ScratchList internal constructor(name: String) : VLB(null, name, VLBVariant.LIST)

class Broadcast internal constructor(name: String) : VLB(null, name, VLBVariant.BROADCAST), ShadowExpression