@file:Suppress("unused")

package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.random.Random

interface HasId {
    var id: String
}

interface Flattenable {
    /**
     * Should recursively call flattenInto on all Flattenable below.
     */
    fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String? = null)

    /**
     * Should recursively call prepareRepresent on all Flattenable below.
     */
    fun prepareRepresent(sprite: Sprite)
}

interface HatBlockHost {
    fun<B: HatBlock> addHatBlock(hatBlock: B): B
}

interface BlockHost {
    fun<B: AnyBlock> addBlock(block: B): B
    val stacks: List<BlockStack>

    operator fun ScratchList.set(index: Expression?, value: Expression?) =
        replaceAtIndex(this, value, index)

    infix fun HandlesSet.set(value: Expression?) =
        this.expressionSetHandler?.let {
            addBlock(it(value))
        } ?: (NormalBlock("motion_movesteps")
            .withExpression("STEPS", shadowExpression = ValueInput.NUMBER.of("0")))

    infix fun HandlesSet.changeBy(value: Expression?) =
        this.expressionChangeHandler?.let {
            addBlock(it(value))
        } ?:
        this.expressionSetHandler?.let {
            addBlock(it(this + value))
        } ?: (NormalBlock("motion_movesteps")
            .withExpression("STEPS", shadowExpression = ValueInput.NUMBER.of("0")))


    infix fun ScratchList.append(value: Expression?) =
        this@BlockHost.append(this, value)

    fun ScratchList.deleteAll() =
        this@BlockHost.deleteAll(this)

    fun ScratchList.deleteAtIndex(value: Expression?) =
        this@BlockHost.deleteAtIndex(this, value)

    fun ScratchList.insertAtIndex(value: Expression?, index: Expression?) =
        this@BlockHost.insertAtIndex(this, value, index)
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
    var parent: String?
    var shadow: Boolean
}

interface Field {
    companion object {
        data class FieldValue(val value: String, val id: String? = null)
        fun of(value: String, id: String? = null): Field =
            object : Field {
                override val fieldValue = FieldValue(value, id)
            }
    }
    val fieldValue: FieldValue
}

interface BlockBlockHost : Block, BlockHost

class BlockStack(myId: String = IdGenerator.makeId(), val contents: MutableList<Block> = mutableListOf()) : HasId, Flattenable, BlockHost {
    fun isEmpty() = contents.isEmpty()
    fun isNotEmpty() = contents.isNotEmpty()
    override var id: String = myId
    override fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String?) {
        var previous: AnyBlock? = null
        contents.forEach {
            previous?.next = it
            previous = it
            it.parent = parentId
            it.flattenInto(map)
        }
    }

    override fun prepareRepresent(sprite: Sprite) {
        contents.forEach {
            if (it is HatBlock) return@forEach
            it.prepareRepresent(sprite)
        }
    }

    override fun<B: AnyBlock> addBlock(block: B) = block.apply(contents::add)

    override val stacks = listOf(this)
}

interface HatBlock : BlockBlockHost

class BuildRoot {
    val globalVariables = mutableMapOf<String, VLB>()
    val globalLists = mutableMapOf<String, VLB>()
    val globalBroadcasts = mutableMapOf<String, Broadcast>()
    val targets = mutableListOf<SpriteBuilder>()
    val stage get() = targets[0]
    val sprites get() = targets.subList(1, targets.size - 1)
}

class SpriteBuilder(val root: BuildRoot) : HatBlockHost, Representable<Representation> {
    val hatBlocks = mutableListOf<HatBlock>()
    val variables = mutableMapOf<String, Triple<Variable, JsonPrimitive, Boolean>>()
    val lists = mutableMapOf<String, Pair<ScratchList, JsonArray>>()
    val broadcasts = mutableMapOf<String, Broadcast>()
    val costumes = mutableMapOf<String, Costume>()
    val sounds = mutableMapOf<String, Sound>()
    val comments = mutableListOf<Comment>()
    var name = "Sprite-${IdGenerator.makeId().substring(0..<8)}"
    var startCostume = 0
    var isStage = false
    var startLayer = 1
    var startAudioTempo = 60
    var startTextToSpeechLanguage: String? = null
    var startVideoState = "on"
    var startVideoTranparency = 50
    var startVolume = 100

    private var shouldScrambleNames = false

    override fun<B: HatBlock> addHatBlock(hatBlock: B) = hatBlock.apply(hatBlocks::add)

    override fun represent(): Representation {
        hatBlocks.forEach {
            it.prepareRepresent(this)
        }
        val blocks = mutableMapOf<String, AnyBlock>()
        hatBlocks.forEach { hatBlock ->
            hatBlock.stacks.forEach { blockStack ->
                blockStack.flattenInto(blocks)
            }
        }
        return buildJsonObject {
            put("broadcasts", buildJsonObject {
                broadcasts.forEach { (_, u) ->
                    put(u.id, u.name)
                }
            })
            put("variables", buildJsonObject {
                variables.forEach { (_, u) ->
                    put(u.first.id, buildJsonArray {
                        add(u.first.name)
                        add(u.second)
                        if (u.third) add(true)
                    })
                }
            })
            put("lists", buildJsonObject {
                lists.forEach { (_, u) ->
                    put(u.first.id, buildJsonArray {
                        add(u.first.name)
                        add(u.second)
                    })
                }
            })
            put("comments", buildJsonObject {
                comments.forEach { c ->
                    put(c.id, c.represent())
                }
            })
            put("costumes", buildJsonArray {
                costumes.forEach { (_, u) ->
                    add(u.representAsset())
                }
            })
            put("sounds", buildJsonArray {
                sounds.forEach { (_, u) ->
                    add(u.representAsset())
                }
            })
            put("name", name)
            put("currentCostume", startCostume)
            put("isStage", isStage)
            put("layerOrder", startLayer)
            put("tempo", startAudioTempo)
            put("textToSpeechLanguage", startTextToSpeechLanguage)
            put("videoState", startVideoState)
            put("videoTransparency", startVideoTranparency)
            put("volume", startVolume)
            put("blocks", JsonObject(blocks.mapValues { (_, u) -> u.represent() }))
        }
    }

    fun makeVar(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonPrimitive = JsonPrimitive(""),
        cloud: Boolean = false
    ) = Variable(if (shouldScrambleNames) IdGenerator.makeRandomId(6) else name).apply {
        if (this.name in variables || this.name in root.globalVariables) {
            throw IllegalArgumentException("This name is already used.")
        }
        variables[this.name] = Triple(this, value, cloud)
    }

    fun makeList(
        name: String = IdGenerator.makeRandomId(6),
        block: JsonArrayBuilder.() -> Unit
    ) = ScratchList(if (shouldScrambleNames) IdGenerator.makeRandomId(6) else name).apply {

        if (this.name in lists || this.name in root.globalLists) {
            throw IllegalArgumentException("This name is already used.")
        }
        lists[this.name] = this to buildJsonArray(block)
    }

    fun makeLocalBroadcast(
        name: String = IdGenerator.makeRandomId(6)
    ) = Broadcast(if (shouldScrambleNames) IdGenerator.makeRandomId(6) else name).apply {
        if (this.name in broadcasts || this.name in root.globalBroadcasts) {
            throw IllegalArgumentException("This name is already used.")
        }
        broadcasts[this.name] = this
    }

    fun scrambleLocalNamesAfter() {
        shouldScrambleNames = true
    }

    operator fun Costume.unaryPlus() = apply {
        costumes[name] = this
    }

    operator fun Sound.unaryPlus() = apply {
        sounds[name] = this
    }

    fun addCostume(
        name: String,
        dataFormat: String,
        assetId: String,
        rotationCenter: Pair<Double, Double>? = null
    ) = +Costume(name, dataFormat, assetId, rotationCenter)

    fun addCostume(
        path: Path,
        name: String
    ) = +loadCostume(path, name)

    fun addSound(
        name: String,
        dataFormat: String,
        assetId: String,
        rate: Int? = null,
        sampleCount: Int? = null
    ) = +Sound(name, dataFormat, assetId, rate, sampleCount)

    fun addSound(
        path: Path,
        name: String
    ) = +loadSound(path, name)
}

typealias Sprite = SpriteBuilder

fun build(block: SpriteBuilder.() -> Unit): SpriteBuilder {
    return SpriteBuilder(BuildRoot()).apply(block)
}

object IdGenerator {
    const val ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    var currentIdIdx = 0
    /**
     * If true, all ids will be randomly generated and not counting up.
     */
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
        return makeRandomId()
    }

    fun makeRandomId(length: Int = 16): String {
        val rawBinary = Random.nextBytes(length)
        return rawBinary.map { it ->
            ALLOWED_CHARS[it.toInt().mod(ALLOWED_CHARS.length)]
        }.joinToString("")
    }
}