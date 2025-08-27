package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

fun main() {
    println(Json.encodeToString(build {
        isolated {
            ifBlock(VLB()) {

            }
        }
    }.represent()))
}