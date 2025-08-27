package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

enum class VLBVariant(val numericType: Int) {
    VARIABLE(12),
    LIST(13),
    BROADCAST(11);
    fun of(name: String) = VLB(null, name, this)
}

open class VLB(opcode: String?, val name: String, val variant: VLBVariant) : Expression(opcode), Field {
    override val fieldValue get() = Field.Companion.FieldValue(name, id)

    override fun representAlone(): Representation =
        buildJsonArray {
            add(variant.numericType)
            add(name)
            add(id)
        }
}