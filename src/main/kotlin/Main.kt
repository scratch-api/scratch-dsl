package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    println(Json.encodeToString(build {
        sprite {
            whenGreenFlagClicked {
                gotoLocation(this@sprite.specialLocation)
            }
        }
    }))
}