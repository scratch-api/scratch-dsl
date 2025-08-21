package de.thecommcraft.scratchdsl.builder

interface Block {
    val opcode: String
    val next: Block?
    val previous: Block?
    val parent: BlockStack
    val isHat: Boolean
    val inputs: Map<String, Input>
    val fields: Map<String, Field>
    val blockId: String
}

interface ParentBlock : Block {
    val subBlocks: SubBlocks
}

interface DoubleParentBlock : ParentBlock {
    val secondSubBlocks: SubBlocks
}

data class AssignVariable(
    val variable: Variable,
    val expression: Expression,
    override val blockId: String = randomId()
) : Block {
    override val opcode: String = "data_setvariableto"
    override val fields: Map<String, Field> = mapOf("VARIABLE" to VariableField(variable))
    override val inputs: Map<String, Input> = mapOf("VALUE" to expression.asInput())
    override val isHat: Boolean
        get() = TODO("Not yet implemented")
    override val next: Block?
        get() = TODO("Not yet implemented")
    override val parent: BlockStack
        get() = TODO("Not yet implemented")
    override val previous: Block?
        get() = TODO("Not yet implemented")
}
