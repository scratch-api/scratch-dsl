import org.scratchapi.scratchdsl.*

fun main() =
    build {
        sprite {
            val costume = addCostume("C:\\Users\\simon\\Downloads\\mkris.png".path, "Test Costume")
            val var1 = makeVar("var1")
            whenGreenFlagClicked {
                val blocks = createBlockStack {
                    var1 changeBy 15.1.expr
                }
                repeat(10) { blockStack(blocks.cloneBlockStack()) }
            }
        }
    }
        .writeTo("C:\\Users\\simon\\IdeaProjects\\scratch-dsl\\src\\main\\resources\\aw.sb3".path)