package de.thecommcraft.scratchdsl.build

/**
 * A host of blocks that are BlockHost
 */
interface BlockBlockHostHost {
    fun addBlockBlockHost(blockBlockHost: BlockBlockHost<*, *>)
}

interface BlockHost : BlockBlockHostHost {
    fun addBlock(block: AnyBlock)
}

interface Representable {
    fun represent(): Representation
}

interface Derepresentable<D: Derepresentable<D, R>, R: Representation> {
    fun derepresent(representation: R): D
}

typealias AnyBlock = Block<*, *>

interface Block<B: Block<B, R>, R: Representation> : Representable, Derepresentable<B, R>

interface BlockBlockHost<B: BlockBlockHost<B, R>, R: Representation> : Block<B, R>, BlockHost

data class BuildRoot(val blockStacks: List<BlockHost> = listOf()) {

}

fun build(block: BuildRoot.() -> Unit): BuildRoot {
    return BuildRoot().apply(block)
}