package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add

fun main() {
    println(Json.encodeToString(build {
        val variable = makeVar("myVar", JsonPrimitive(1))
        val list = makeList("myList") {
            add("2")
            add(1)
        }
        isolated {
            setVar(variable, 4.expr % 2.expr)
        }
    }.represent()))
}