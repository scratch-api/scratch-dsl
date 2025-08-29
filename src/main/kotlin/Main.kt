package de.thecommcraft.scratchdsl

import de.thecommcraft.scratchdsl.build.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add

fun main() {
    println(Json.encodeToString(build {
        val sumVar = makeVar("sum")
        val indexVar = makeVar("index")
        val numbers = makeList("numbers") { }
        isolated {
            numbers.deleteAll()
            indexVar set 0.expr
            whileBlock (indexVar lessThan 10.expr) {
                indexVar set indexVar + 1.expr
                numbers append indexVar
            }
        }
        isolated {
            sumVar set 0.expr
            indexVar set 0.expr
            whileBlock (indexVar lessThan numbers.length) {
                indexVar set indexVar + 1.expr
                sumVar set sumVar + numbers[indexVar]
            }
        }
    }.represent()))
}