package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add

fun main() {
    println(Json.encodeToString(build {
        val outVal = makeVar("outVal")
        val myList = makeList("myList") {
            add("2")
            add(1)
        }
        isolated {
            myList[2.expr] set 3.expr
        }
    }.represent()))
}