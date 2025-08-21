package de.thecommcraft.scratchdsl.builder

interface BlocksBuilderHost {
    fun register(block: Block)
}

data class RootBlocksBuilderHost(
    val allBlocks: MutableMap<String, Block> = mutableMapOf()
) : BlocksBuilderHost {
    operator fun get(blockId: String) = allBlocks[blockId]
    override fun register(block: Block) {
        allBlocks[block.blockId] = block
    }
}

fun String.toExpr() = Value(string = this)
fun Int.toExpr() = Value(int = this)
fun Double.toExpr() = Value(number = this)

data class BlocksBuilder(
    override val blocks: MutableList<Block>,
    val parentBlocksBuilder: BlocksBuilderHost,
    val rootBlocksBuilderHost: RootBlocksBuilderHost
) : BlocksBuilderHost, Blocks {
    override fun register(block: Block) {
        if (blocks.isNotEmpty()) {
            block.previous = blocks.last()
            blocks.last().next = block
        }
        block.makeParent(this)
        parentBlocksBuilder.register(block)
        blocks.add(block)
    }
    operator fun<B: Block> B.unaryPlus(): B = apply(::register)
    var Variable.value: Expression
        get() = throw IllegalAccessError("Not allowed!")
        set(value) {
            +AssignVariable(variable = this, expression = value)
        }
}