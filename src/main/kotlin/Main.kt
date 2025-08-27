package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    val variable = VLBVariant.VARIABLE.of("meine Variable")
    println(Json.encodeToString(build {
        isolated {
            repeatBlock(5.expr) {
                setVar(variable, 2.expr)
            }
        }
    }.represent()))
}