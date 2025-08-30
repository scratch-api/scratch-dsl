package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add

// "assetId": "cd21514d0531fdffb22204e0ec5ed84a",
//          "bitmapResolution": 1,
//          "dataFormat": "svg",
//          "md5ext": "cd21514d0531fdffb22204e0ec5ed84a.svg",
//          "name": "costume1",
//          "rotationCenterX": 0,
//          "rotationCenterY": 0

fun main() {
    println(Json.encodeToString(build {
        val costumeA = addCostume("costume1", "svg", "cd21514d0531fdffb22204e0ec5ed84a")
        val variable = makeVar()
        whenGreenFlagClicked {
            gotoLocation(this@build.asSpecialLocation)
        }
    }.represent()))
}