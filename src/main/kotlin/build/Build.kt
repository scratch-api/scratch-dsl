package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random

interface HasId {
    var id: String
}

interface Flattenable {
    fun flattenInto(map: MutableMap<String, AnyBlock>)
}

interface HatBlockHost {
    fun addHatBlock(hatBlock: HatBlock)
}

interface BlockHost {
    fun addBlock(block: AnyBlock)
    val stacks: List<BlockStack>
}

interface Representable<R: Representation> {
    fun represent(): R
}

interface Loadable<R: Representation> {
    fun loadInto(representation: R)
}

typealias AnyBlock = Block

interface Block : Representable<Representation>, Loadable<Representation>, HasId, Flattenable {
    var next: AnyBlock?
    val opcode: String?
    var topLevel: Boolean
    var parent: AnyBlock?
    var shadow: Boolean
}

interface Field {
    companion object {
        data class FieldValue(val value: String, val id: String?)
    }
    val fieldValue: FieldValue
}

interface BlockBlockHost : Block, BlockHost

class BlockStack(private val myId: String = makeId(), val contents: MutableList<Block> = mutableListOf()) : HasId, Flattenable, BlockHost {
    fun isEmpty() = contents.isEmpty()
    fun isNotEmpty() = contents.isNotEmpty()
    override var id: String = myId
    override fun flattenInto(map: MutableMap<String, AnyBlock>) {
        var previous: AnyBlock? = null
        contents.forEach {
            previous?.next = it
            previous = it
            it.flattenInto(map)
        }
    }

    override fun addBlock(block: AnyBlock) {
        contents.add(block)
    }

    override val stacks = listOf(this)
}

interface HatBlock : BlockBlockHost {
    fun prepareRepresent() {

    }
}

class BuildRoot(val hatBlocks: MutableList<HatBlock> = mutableListOf()) : HatBlockHost, Representable<Representation> {
    override fun addHatBlock(hatBlock: HatBlock) {
        hatBlocks.add(hatBlock)
    }

    override fun represent(): Representation {
        val blocks = mutableMapOf<String, AnyBlock>()
        hatBlocks.forEach { hatBlock ->
            hatBlock.stacks.forEach { blockStack ->
                blockStack.flattenInto(blocks)
            }
        }
        return buildJsonObject {
            put("blocks", JsonObject(blocks.mapValues { (t, u) -> u.represent() }))
        }
    }
}

const val ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

var currentIdIdx = 0
var isModifying = true

fun makeId(): String {
    if (!isModifying) {
        var name = ""
        val chars = ALLOWED_CHARS.length
        var residue = currentIdIdx
        do {
            name += ALLOWED_CHARS[residue.mod(chars)]
            residue /= chars
        } while (residue > 0)
        currentIdIdx += 1
        return name
    }
    val rawBinary = Random.nextBytes(16)
    return rawBinary.map { it ->
        ALLOWED_CHARS[it.toInt().mod(ALLOWED_CHARS.length)]
    }.joinToString("")
}

fun build(block: BuildRoot.() -> Unit): BuildRoot {
    return BuildRoot().apply(block)
}
