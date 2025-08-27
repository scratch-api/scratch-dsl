package de.thecommcraft.scratchdsl.build


@Suppress("UNCHECKED_CAST")
fun <T> Any?.unsafeCast(): T = this as T

/**
 * A host of blocks that are BlockHost
 */
interface BlockBlockHostHost {
    fun addBlockBlockHost(blockBlockHost: BlockBlockHost)
}

interface BlockHost : BlockBlockHostHost {
    fun addBlock(block: AnyBlock)
    fun getBlocks(): List<Block>
}

interface Representable<R: Representation> {
    fun represent(): R
}

interface Loadable<R: Representation> {
    fun loadInto(representation: R)
}

typealias AnyBlock = Block

interface Block : Representable<Representation>, Loadable<Representation> {
    fun getId(): String
    fun flattenInto(map: MutableMap<String, AnyBlock>)
}

interface BlockBlockHost : Block, BlockHost

data class BuildRoot(val blockStacks: List<BlockHost> = listOf()) {

}

fun build(block: BuildRoot.() -> Unit): BuildRoot {
    return BuildRoot().apply(block)
}