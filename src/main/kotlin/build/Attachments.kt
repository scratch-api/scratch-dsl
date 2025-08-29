package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.extension


interface Asset : Representable<JsonObject> {
    val name: String
    val assetId: String
    val dataFormat: String
    fun JsonObjectBuilder.extraAttributes() {

    }
    override fun represent() =
        buildJsonObject {
            put("assetId", assetId)
            put("dataFormat", dataFormat)
            put("md5ext", "$assetId.$dataFormat")
            put("name", name)
            extraAttributes()
        }
}

fun getChecksum(path: Path): String {
    val data = Files.readAllBytes(path)
    val hash = MessageDigest.getInstance("MD5").digest(data)
    return BigInteger(1, hash).toString(16)
}

fun loadCostume(path: Path, name: String): Costume {
    val checksum = getChecksum(path)
    return Costume(name, path.extension, checksum)
}

data class Costume(
    override val name: String,
    override val dataFormat: String,
    override val assetId: String,
    val rotationCenter: Pair<Double, Double>? = null
) : Asset {
    override fun JsonObjectBuilder.extraAttributes() {
        rotationCenter?.let {
            put("rotationCenterX", it.first)
            put("rotationCenterY", it.second)
        }
    }
}

data class Comment(
    val block: Block?,
    val width: Double,
    val height: Double,
    val minimized: Boolean,
    val text: String,
    val position: Pair<Double, Double>
) : Representable<JsonObject>, HasId {
    private var myId: String? = null

    override var id: String
        get() {
            myId?.let {
                return it
            }
            val newId = makeId()
            myId = newId
            return newId
        }
        set(value) { myId = value }

    override fun represent() = buildJsonObject {
        put("blockId", block?.id)
        put("width", width)
        put("height", height)
        put("minimized", minimized)
        put("text", text)
        put("x", position.first)
        put("y", position.second)
    }
}

fun loadSound(path: Path, name: String): Sound {
    val checksum = getChecksum(path)
    return Sound(name, path.extension, checksum)
}

data class Sound(
    override val name: String,
    override val dataFormat: String,
    override val assetId: String,
    val rate: Int? = null,
    val sampleCount: Int? = null
) : Asset {
    override fun JsonObjectBuilder.extraAttributes() {
        rate?.let {
            put("rate", it)
        }
        sampleCount?.let {
            put("sampleCount", it)
        }
    }
}