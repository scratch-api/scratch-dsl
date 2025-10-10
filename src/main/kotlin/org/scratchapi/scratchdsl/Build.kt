@file:Suppress("unused")

package org.scratchapi.scratchdsl

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.open
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString
import kotlinx.io.files.Path as PathB
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.random.Random
import okhttp3.*
import okio.*
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath

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

    fun ProcedurePrototypeBuilder.impl(block: BlockHost.() -> Unit): Procedure {
        val procedurePrototype = ProcedurePrototype(proccode, warp, arguments)
        makeProcedureDefinition(procedurePrototype, block)
        return Procedure(procedurePrototype)
            .apply {
                proc = this
            }
    }
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
        (this.expressionSetHandler?.let {
            addBlock(it(this + value))
        } ?: (NormalBlock("motion_movesteps")
            .withExpression("STEPS", shadowExpression = ValueInput.NUMBER.of("0"))))


    infix fun ScratchList.append(value: Expression?) =
        this@BlockHost.append(this, value)

    fun ScratchList.deleteAll() =
        this@BlockHost.deleteAll(this)

    fun ScratchList.deleteAtIndex(value: Expression?) =
        this@BlockHost.deleteAtIndex(this, value)

    fun ScratchList.insertAtIndex(value: Expression?, index: Expression?) =
        this@BlockHost.insertAtIndex(this, value, index)

    fun Procedure.call(vararg arguments: Expression?): NormalBlock {
        val block = NormalBlock("procedures_call")
            .withDefaultMutation()
            .withMutation("argumentids", JsonPrimitive(Json.encodeToString(buildJsonArray {
                this@call.procedurePrototype.arguments.forEach { argument ->
                    add(argument.argumentId)
                }
            })))
            .withMutation("proccode", JsonPrimitive(procedurePrototype.proccode))
            .withMutation("warp", JsonPrimitive(Json.encodeToString(procedurePrototype.warp)))
        procedurePrototype.arguments.forEach { argument ->
            if (argument is ProcedureArgumentStringNumberShadow) block.withExpression(argument.argumentId, null, ValueInput.TEXT.of(""))
        }
        procedurePrototype.arguments.zip(arguments).forEach { (argument, expr) ->
            if (argument is ProcedureArgumentStringNumberShadow) {
                block.withExpression(argument.argumentId, expr, ValueInput.TEXT.of(""))
            } else {
                block.withExpression(argument.argumentId, expr)
            }
        }
        return addBlock(block)
    }
}

interface Representable<R: Representation> {
    fun represent(): R
}

//interface Loadable<R: Representation> {
//    fun loadInto(representation: R)
//}

typealias AnyBlock = Block

interface Block : Representable<Representation>, HasId, Flattenable {
    var next: AnyBlock?
    val opcode: String?
    var topLevel: Boolean
    var parent: String?
    var shadow: Boolean
    fun cloneBlock(): Block
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

class BlockStack(
    myId: String = IdGenerator.makeId(),
    val contents: MutableList<Block> = mutableListOf()
) : HasId, Flattenable, BlockHost {
    fun isEmpty() = contents.isEmpty()
    fun isNotEmpty() = contents.isNotEmpty()
    override var id: String = myId
    override fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String?) {
        var previous: AnyBlock? = null
        contents.forEach {
            previous?.next = it
            previous = it
            it.flattenInto(map, parentId)
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

    fun cloneBlockStack(): BlockStack {
        return BlockStack(contents = contents.map(Block::cloneBlock).toMutableList())
    }
}

interface HatBlock : BlockBlockHost

class BuildRoot internal constructor() : Representable<Representation> {
    val globalVariables = mutableMapOf<String, VLB>()
    val globalLists = mutableMapOf<String, VLB>()
    val globalBroadcasts = mutableMapOf<String, Broadcast>()
    val stage = StageBuilder(this)
    private val stageSprite = stage.spriteBuilder
    val sprites = mutableListOf<SpriteBuilder>()
    val targets
        get() = listOf(stageSprite) + sprites
    var monitorData: Representation = buildJsonArray {  }
        private set
    private val extensions = mutableListOf<Representation>()

    /**
     * Where the builder will look for assets to be included in a sb3 file.
     */
    val assetDirectories = mutableListOf<Path>()

    override fun represent() = buildJsonObject {
        targets.forEach(SpriteBuilder::prepareRepresent)
        put("targets", buildJsonArray {
            targets.forEach { target ->
                add(target.represent())
            }
        })
        put("monitors", monitorData)
        put("extensions", JsonArray(extensions))
        put("meta", buildJsonObject {
            put("agent", "")
            put("tool", buildJsonObject {
                put("url", "https://github.com/scratch-api/scratch-dsl")
            })
            put("semver", "3.0.0")
            put("vm", "0.2.0")
        })
    }

    fun stage(block: StageBuilder.() -> Unit) =
        stage.apply(block)

    fun sprite(block: SpriteBuilder.() -> Unit) = SpriteBuilder(this).apply {
        sprites.add(this)
        block()
    }

    /**
     * Can be obtained by first building without it, then configuring the monitors in the scratch editor and
     * extracting it from the project.json.
     *
     * Not required.
     */
    fun attachMonitorData(json: String) {
        monitorData = Json.decodeFromString<Representation>(json)
    }

    /**
     * Can be obtained by first building without it, then configuring the monitors in the scratch editor and
     * extracting it from the project.json.
     *
     * Not required.
     */
    fun attachMonitorData(json: Representation) {
        monitorData = json
    }

    fun extractMonitorDataFrom(path: Path) {
        val projectJson =
            if (path.extension == "zip") {
                defaultFileSystem.openZip(path).read("project.json".toPath()) { readUtf8() }
            } else {
                path.readText()
            }
        val decodedProjectJson = Json.parseToJsonElement(projectJson)
        decodedProjectJson.jsonObject["monitors"]?.let { attachMonitorData(it) }
    }

    fun addExtension(extensionRepresentation: Representation) {
        extensions.add(extensionRepresentation)
    }

    fun makeGlobalVar(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonPrimitive = JsonPrimitive(""),
        cloud: Boolean = false
    ) =
        stage.makeVar(name, value, cloud)

    fun makeGlobalList(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonArray
    ) =
        stage.makeList(name, value)

    fun makeGlobalList(
        name: String = IdGenerator.makeRandomId(6),
        block: JsonArrayBuilder.() -> Unit
    ) =
        stage.makeList(name, block)

    fun makeBroadcast(name: String = IdGenerator.makeRandomId(6)) =
        stage.makeBroadcast(name)

    fun makeLocalVarSlot(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonPrimitive = JsonPrimitive(""),
        cloud: Boolean = false
    ) =
        VariableSlot(name, value, cloud)

    fun makeLocalListSlot(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonArray
    ) =
        ScratchListSlot(name, value)

    fun makeLocalListSlot(
        name: String = IdGenerator.makeRandomId(6),
        block: JsonArrayBuilder.() -> Unit
    ) =
        ScratchListSlot(name, block)

    fun makeLocalBroadcastSlot(name: String = IdGenerator.makeRandomId(6)) =
        BroadcastSlot(name)

    fun addBackdrop(
        name: String,
        dataFormat: String,
        assetId: String,
        rotationCenter: Pair<Double, Double>? = null,
        bitmapResolution: Int = 1,
        path: Path? = null
    ) = stage.addBackdrop(name, dataFormat, assetId, rotationCenter, bitmapResolution, path)

    fun addBackdrop(
        path: Path,
        name: String
    ) = stage.addBackdrop(path, name)
}

val httpClient = OkHttpClient()

fun getHttp(url: String): ByteArray? {
    val request = Request.Builder()
        .url(url)
        .build()

    val response = httpClient.newCall(request).execute()
    return response.body?.bytes()
}

class StageBuilder internal constructor(root: BuildRoot) : HatBlockHost {
    internal val spriteBuilder = SpriteBuilder(root)

    init {
        spriteBuilder.isStage = true
    }

    override fun <B : HatBlock> addHatBlock(hatBlock: B) = spriteBuilder.addHatBlock(hatBlock)

    private val backdropList = mutableListOf<Backdrop>()

    val backdrops: List<Backdrop> = backdropList

    fun makeVar(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonPrimitive = JsonPrimitive(""),
        cloud: Boolean = false
    ) = spriteBuilder.makeVar(name, value, cloud)

    fun makeList(
        name: String = IdGenerator.makeRandomId(6),
        value: JsonArray
    ) = spriteBuilder.makeList(name, value)

    fun makeList(
        name: String = IdGenerator.makeRandomId(6),
        block: JsonArrayBuilder.() -> Unit
    ) = spriteBuilder.makeList(name, block)

    fun makeBroadcast(
        name: String = IdGenerator.makeRandomId(6)
    ) = spriteBuilder.makeLocalBroadcast(name)

    fun addBackdrop(
        name: String,
        dataFormat: String,
        assetId: String,
        rotationCenter: Pair<Double, Double>? = null,
        bitmapResolution: Int = 1,
        path: Path? = null
    ): Backdrop {
        val backdrop = spriteBuilder.addCostume(name, dataFormat, assetId, rotationCenter, bitmapResolution, path).asBackdrop()
        backdropList.add(backdrop)
        return backdrop
    }

    fun addBackdrop(
        path: Path,
        name: String
    ): Backdrop {
        val backdrop = spriteBuilder.addCostume(path, name).asBackdrop()
        backdropList.add(backdrop)
        return backdrop
    }

    fun addSound(
        name: String,
        dataFormat: String,
        assetId: String,
        rate: Int? = null,
        sampleCount: Int? = null,
        path: Path? = null
    ) = spriteBuilder.addSound(name, dataFormat, assetId, rate, sampleCount, path)

    fun addSound(
        path: Path,
        name: String
    ) = spriteBuilder.addSound(path, name)
}

class SpriteBuilder internal constructor(val root: BuildRoot) : HatBlockHost, Representable<Representation> {
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

    lateinit var allBlocks: Map<String, Block>

    private var shouldScrambleNames = false

    override fun<B: HatBlock> addHatBlock(hatBlock: B) = hatBlock.apply(hatBlocks::add)

    fun prepareRepresent() {
        if (costumes.isEmpty()) {
            val data = "<svg version=\"1.1\" width=\"2\" height=\"2\" viewBox=\"-1 -1 2 2\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\\n  <!-- Exported by Scratch - http://scratch.mit.edu/ -->\\n</svg>"
                .toByteArray()
            +Costume("costume1", "svg", "de342ccf4bbe18d30bcacafb819dd91f", data=data)
        }
        if (broadcasts.isEmpty() && isStage) {
            root.stage.makeBroadcast("message1")
        }
        hatBlocks.forEach {
            it.prepareRepresent(this)
        }
        val blocks = mutableMapOf<String, AnyBlock>()
        hatBlocks.forEach { hatBlock ->
            hatBlock.stacks.forEach { blockStack ->
                blockStack.flattenInto(blocks)
            }
        }
        allBlocks = blocks
    }

    override fun represent(): Representation {
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
            put("blocks", JsonObject(allBlocks.mapValues { (_, u) -> u.represent() }))
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
        jsonArray: JsonArray
    ) = ScratchList(if (shouldScrambleNames) IdGenerator.makeRandomId(6) else name).apply {

        if (this.name in lists || this.name in root.globalLists) {
            throw IllegalArgumentException("This name is already used.")
        }
        lists[this.name] = this to jsonArray
    }

    fun makeList(
        name: String = IdGenerator.makeRandomId(6),
        block: JsonArrayBuilder.() -> Unit
    ) = makeList(name, buildJsonArray(block))


    fun makeLocalBroadcast(
        name: String = IdGenerator.makeRandomId(6)
    ) = Broadcast(if (shouldScrambleNames) IdGenerator.makeRandomId(6) else name).apply {
        if (this.name in broadcasts || this.name in root.globalBroadcasts) {
            throw IllegalArgumentException("This name is already used.")
        }
        broadcasts[this.name] = this
    }

    fun makeVar(variableSlot: VariableSlot) =
        makeVar(variableSlot.name, variableSlot.value, variableSlot.cloud).apply {
            id = variableSlot.id
        }

    fun makeList(listSlot: ScratchListSlot) =
        makeList(listSlot.name, listSlot.value).apply {
            id = listSlot.id
        }

    fun makeBroadcast(broadcastSlot: BroadcastSlot) =
        makeLocalBroadcast(broadcastSlot.name).apply {
            id = broadcastSlot.id
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
        rotationCenter: Pair<Double, Double>? = null,
        bitmapResolution: Int = 1,
        path: Path? = null
    ) = +Costume(name, dataFormat, assetId, rotationCenter, bitmapResolution, path)

    fun addCostume(
        path: Path,
        name: String,
        bitmapResolution: Int = 1,
        rotationCenter: Pair<Double, Double>? = null
    ) = +loadCostume(path, name, bitmapResolution, rotationCenter)

    fun addSound(
        name: String,
        dataFormat: String,
        assetId: String,
        rate: Int? = null,
        sampleCount: Int? = null,
        path: Path? = null
    ) = +Sound(name, dataFormat, assetId, rate, sampleCount, path)

    fun addSound(
        path: Path,
        name: String
    ) = +loadSound(path, name)
}

typealias Sprite = SpriteBuilder

fun build(block: BuildRoot.() -> Unit) =
    BuildRoot().apply(block)

data class ProjectJson(
    val string: String,
    val buildRoot: BuildRoot
)

fun BuildRoot.toProjectJsonContents() = ProjectJson(Json.encodeToString(represent()), this)

/**
 * Writes the project json into a file at the path.
 */
fun BuildRoot.writeToProjectJsonPath(path: Path) = toProjectJsonContents().writeToProjectJsonPath(path)

/**
 * Writes the project json into an existing scratch project.
 */
fun BuildRoot.modifyProject(path: Path) {
    toProjectJsonContents().modifyProject(path)
}

/**
 * Uses the project json and the included assets to create a sb3 file.
 */
fun BuildRoot.writeTo(path: Path) {
    toProjectJsonContents().writeTo(path)
}

internal data class AssetResource(
    val source: Source? = null,
    val path: Path? = null
) : AutoCloseable {
    override fun close() {
        source?.close()
    }
}

internal fun BuildRoot.locateResources(assets: List<Asset>): Map<Asset, Path> {
    val paths = mutableMapOf<Asset, Path>()
    assetDirectories.forEach { dir ->
        if (!dir.metaData().isDirectory) return@forEach
        val files = defaultFileSystem.list(dir)
            .filter { it.metaData().isRegularFile }.associateBy { path -> path.name }
        assets.forEach { asset ->
            files[asset.md5Ext]?.let {
                paths[asset] = it
            } ?: files["${asset.name}.${asset.dataFormat}"]?.let {
                paths[asset] = it
            }
        }
    }
    return paths
}

internal fun BuildRoot.locateResource(asset: Asset): Path {
    assetDirectories.forEach { dir ->
        if (!dir.metaData().isDirectory) return@forEach
        defaultFileSystem.list(dir).forEach entries@ { path ->
            if (!path.metaData().isRegularFile) return@entries
            if (
                path.name == asset.md5Ext ||
                path.name == "${asset.name}.${asset.dataFormat}"
            ) {
                return path
            }
        }
    }
    throw IllegalArgumentException("Asset ${asset.name}.${asset.dataFormat} could not be located. Please pass its path or include the directory it is in in the assetDirectories of your BuildRoot.")
}

internal fun BuildRoot.getResources(): Map<String, AssetResource> {
    val requiresSearch = mutableListOf<Asset>()
    val resources = mutableMapOf<String, AssetResource>()
    targets.forEach { target ->
        target.costumes.forEach find@ { (_, costume) ->
            resources[costume.md5Ext] = if (costume.path != null) {
                AssetResource(path = costume.path)
            } else if (costume.data != null) {
                AssetResource(source = Buffer().apply { write(costume.data) })
            } else {
                requiresSearch.add(costume)
                return@find
            }
        }
        target.sounds.forEach find@ { (_, sound) ->
            resources[sound.md5Ext] = if (sound.path != null) {
                AssetResource(path = sound.path)
            } else if (sound.data != null) {
                AssetResource(source = Buffer().apply { write(sound.data) })
            } else {
                requiresSearch.add(sound)
                return@find
            }
        }
    }
    locateResources(requiresSearch).forEach { (asset, path) ->
        resources[asset.md5Ext] = AssetResource(path = path)
    }
    return resources
}


/**
 * Writes the project json into a file at the path.
 */
fun ProjectJson.writeToProjectJsonPath(path: Path) = path.writeText(string)

/**
 * Writes the project json into an existing scratch project.
 */
fun ProjectJson.modifyProject(path: Path, addResources: Boolean = false) {
    if (addResources) {
        val resources = buildRoot.getResources()
        try {
            Buffer().apply { writeString(string) }.use { textSource ->
                Zip.open(PathB(path.toString()), mode=Zip.Mode.Append).use { zip ->
                    zip.entryFromSource(PathB("project.json"), textSource)
                    resources.forEach { (name, resource) ->
                        resource.source?.let {
                            zip.entryFromSource(PathB(name), it)
                        } ?: resource.path?.let {
                            zip.entryFromPath(PathB(name), PathB(it.toString()))
                        }
                    }
                }
            }
        } finally {
            resources.values.forEach(AutoCloseable::close)
        }
        return
    }
    Buffer().apply { writeString(string) }.use { textSource ->
        Zip.open(PathB(path.toString()), mode=Zip.Mode.Append).use { zip ->
            zip.entryFromSource(PathB("project.json"), textSource)
        }
    }
}

/**
 * Uses the project json and the included assets to create a sb3 file.
 */
fun ProjectJson.writeTo(path: Path) {
    val resources = buildRoot.getResources()
    try {
        Buffer().apply { writeString(string) }.use { textSource ->

            Zip.open(PathB(path.toString()), mode=Zip.Mode.Write).use { zip ->
                zip.entryFromSource(PathB("project.json"), textSource)
                resources.forEach { (name, resource) ->
                    resource.source?.let {
                        zip.entryFromSource(PathB(name), it)
                    } ?: resource.path?.let {
                        zip.entryFromPath(PathB(name), PathB(it.toString()))
                    }
                }
            }
        }
    } finally {
        resources.values.forEach(AutoCloseable::close)
    }
}

/**
 * Prints out the string.
 */
fun ProjectJson.output() = println(string)

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

internal val Path.extension get() = name.split(".").last()

internal fun<T> Path.writeFile(mustCreate: Boolean = false, writerAction: BufferedSink.() -> T) = defaultFileSystem.write(this, mustCreate, writerAction)

internal fun Path.writeText(text: String): Unit = writeFile { write(text.encodeUtf8()) }

internal fun<T> Path.readFile(readerAction: BufferedSource.() -> T) = defaultFileSystem.read(this, readerAction)

internal fun Path.readText() = readFile { readUtf8() }

internal fun Path.metaData() = defaultFileSystem.metadata(this)

val defaultFileSystem by lazy { FileSystem.SYSTEM }
