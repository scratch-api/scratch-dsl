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

data class BlocksBuilder(
    private val blocks: MutableList<Block>,
    val parentBlocksBuilder: BlocksBuilderHost,
    val rootBlocksBuilderHost: RootBlocksBuilderHost
) : BlocksBuilderHost {
    override fun register(block: Block) {
        parentBlocksBuilder.register(block)
    }
    operator fun<B: Block> B.unaryPlus(): B = apply {
        register(this)
        blocks.add(this)
    }
    var Variable.value: Expression
        get() = throw IllegalAccessError("Not allowed!")
        set(value) {
            +AssignVariable(variable = this, expression = value)
        }
}