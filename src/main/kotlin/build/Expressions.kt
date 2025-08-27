package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

abstract class Expression : Block {
    open val independent = true
    open val isShadow = false
    open fun representAlone(): Representation {
        return JsonPrimitive(getId())
    }
    fun representAsInput(): Representation {
        return buildJsonArray {
            add(if (isShadow) 1 else 2)
            add(representAlone())
        }
    }
}

abstract class ShadowExpression : Expression() {
    fun representAsInputWith(other: Expression?): Representation {
        if (other == null) return representAsInput()
        return buildJsonArray {
            add(3)
            add(other.representAlone())
            add(representAlone())
        }
    }
}