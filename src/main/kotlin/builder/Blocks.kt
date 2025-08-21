package de.thecommcraft.scratchdsl.builder

interface Block {
    val opcode: String
    var next: Block?
    var previous: Block?
    val parent: Blocks
        get() = currentParent
    val fields: Map<String, Field>
    val inputs: Map<String, Input>
    val blockId: String
    var currentParent: Blocks
    fun makeParent(parent: Blocks) {
        currentParent = parent
    }
}

interface HatBlock : Block {
    override val parent: BlockStack
        get() {
            currentParent.let {
                if (it is BlockStack) return it
                throw IllegalStateException("parent must be BlockStack")
            }
        }

    @Deprecated("Do not use.", replaceWith = ReplaceWith("parent with type BlockStack"))
    override fun makeParent(parent: Blocks) {
        if (parent !is BlockStack) throw IllegalStateException("parent must be BlockStack")
        super.makeParent(parent)
    }

    fun makeParent(parent: BlockStack) {
        super.makeParent(parent)
    }
}

interface ParentBlock : Block {
    val subBlocks: SubBlocks
}

interface DoubleParentBlock : ParentBlock {
    val secondSubBlocks: SubBlocks
}

data class MoveSteps(
    val amount: Expression,
    override var previous: Block?,
    override val blockId: String = randomId()
) : Block {
    override lateinit var currentParent: Blocks
    override val opcode = "motion_movesteps"
    override val fields = mapOf<String, Field>()
    override val inputs = mapOf("STEPS" to amount.asInput())
    override var next: Block? = null
}

data class TurnRight(
    val angle: Expression,
    override var previous: Block?,
    override val blockId: String = randomId(),
) : Block {
    override lateinit var currentParent: Blocks
    override val opcode = "motion_turnright"
    override val fields = mapOf<String, Field>()
    override val inputs = mapOf("DEGREES" to angle.asInput().withValueDefault(number = 15.0))
    override var next: Block? = null
}

data class TurnLeft(
    val angle: Expression,
    override var previous: Block?,
    override val blockId: String = randomId(),
) : Block {
    override lateinit var currentParent: Blocks
    override val opcode = "motion_turnleft"
    override val fields = mapOf<String, Field>()
    override val inputs = mapOf("DEGREES" to angle.asInput().withValueDefault(number = 15.0))
    override var next: Block? = null
}

//data class GoToSelected(
//    val selected: String,
//    override var previous: Block?,
//    override val blockId: String = randomId(),
//) : Block {
//    override lateinit var currentParent: Blocks
//    override val opcode = "motion_turnleft"
//    override val fields = mapOf<String, Field>()
//    override val inputs = mapOf("DEGREES" to angle.asInput())
//    override var next: Block? = null
//}

data class GoToXY(
    val x: Expression,
    val y: Expression,
    override var previous: Block?,
    override val blockId: String = randomId(),
) : Block {
    override lateinit var currentParent: Blocks
    override val opcode = "motion_turnleft"
    override val fields = mapOf<String, Field>()
    override val inputs = mapOf(
        "X" to x.asInput(),
        "Y" to y.asInput()
    )
    override var next: Block? = null
}

data class WhenGreenFlagClicked(
    override val blockId: String = randomId(),
) : HatBlock {
    override lateinit var currentParent: Blocks
    override val opcode = "event_whenflagclicked"
    override val fields = mapOf<String, Field>()
    override val inputs = mapOf<String, Input>()
    override var next: Block? = null
    override var previous: Block? = null
}

data class AssignVariable(
    val variable: Variable,
    val expression: Expression,
    override var previous: Block? = null,
    override val blockId: String = randomId(),
) : Block {
    override lateinit var currentParent: Blocks
    override val opcode = "data_setvariableto"
    override val fields = mapOf("VARIABLE" to VariableField(variable))
    override val inputs = mapOf("VALUE" to expression.asInput())
    override var next: Block? = null
}

