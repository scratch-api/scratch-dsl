package org.scratchapi.scratchdsl

import kotlinx.serialization.json.*
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.*

interface Asset {
    val name: String
    val assetId: String
    val dataFormat: String
    val path: Path?
    val data: ByteArray?
    val md5Ext: String
        get() = "$assetId.$dataFormat"
    fun JsonObjectBuilder.extraAttributes() {

    }
    fun representAsset() =
        buildJsonObject {
            put("assetId", assetId)
            put("dataFormat", dataFormat)
            put("md5ext", md5Ext)
            put("name", name)
            extraAttributes()
        }
}

fun getChecksum(path: Path): String {
    val data = Files.readAllBytes(path)
    val hash = MessageDigest.getInstance("MD5").digest(data)
    return BigInteger(1, hash).toString(16)
}

internal fun loadCostume(path: Path, name: String, bitmapResolution: Int = 1, rotationCenter: Pair<Double, Double>? = null): Costume {
    val checksum = getChecksum(path)
    return Costume(
        name,
        path.extension,
        checksum,
        path = path,
        rotationCenter = rotationCenter,
        bitmapResolution = bitmapResolution
    )
}

data class Costume internal constructor(
    override val name: String,
    override val dataFormat: String,
    override val assetId: String,
    val rotationCenter: Pair<Double, Double>? = null,
    val bitmapResolution: Int = 1,
    override val path: Path? = null,
    override val data: ByteArray? = null
) : Asset, Field, NormalShadowExpressionShouldCopy("looks_costume") {
    override val fieldValue: Field.Companion.FieldValue = Field.Companion.FieldValue(name)
    override fun JsonObjectBuilder.extraAttributes() {
        put("bitmapResolution", bitmapResolution)
        rotationCenter?.let {
            put("rotationCenterX", it.first)
            put("rotationCenterY", it.second)
        }
    }

    init {
        fields["COSTUME"] = this
    }

    override fun representAlone() =
        JsonPrimitive(id)

    override fun makeCopy() =
        copy()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Costume

        if (name != other.name) return false
        if (dataFormat != other.dataFormat) return false
        if (assetId != other.assetId) return false
        if (rotationCenter != other.rotationCenter) return false
        if (path != other.path) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false
        if (fieldValue != other.fieldValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + dataFormat.hashCode()
        result = 31 * result + assetId.hashCode()
        result = 31 * result + (rotationCenter?.hashCode() ?: 0)
        result = 31 * result + (path?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + fieldValue.hashCode()
        return result
    }
}

internal fun Costume.asBackdrop() = Backdrop(name, this)

internal fun Expression?.asBackdrop(): Expression? {
    if (this is Costume) {
        return asBackdrop()
    }
    return this
}

data class Backdrop internal constructor(
    val name: String,
    val costume: Costume
) : NormalShadowExpressionShouldCopy("looks_backdrops"), Field {
    override val fieldValue: Field.Companion.FieldValue = Field.Companion.FieldValue(name)

    init {
        fields["BACKDROP"] = this
    }

    override fun makeCopy() = copy()

    override fun representAlone() =
        JsonPrimitive(id)
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
            val newId = IdGenerator.makeId()
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

internal fun loadSound(path: Path, name: String): Sound {
    val checksum = getChecksum(path)
    return Sound(name, path.extension, checksum, path = path)
}

data class Sound internal constructor(
    override val name: String,
    override val dataFormat: String,
    override val assetId: String,
    val rate: Int? = null,
    val sampleCount: Int? = null,
    override val path: Path? = null,
    override val data: ByteArray? = null
) : Asset, Field, NormalShadowExpressionShouldCopy("sound_sounds_menu") {
    override val fieldValue: Field.Companion.FieldValue = Field.Companion.FieldValue("name")
    override fun JsonObjectBuilder.extraAttributes() {
        rate?.let {
            put("rate", it)
        }
        sampleCount?.let {
            put("sampleCount", it)
        }
    }

    init {
        fields["SOUND_MENU"] = this
    }

    override fun representAlone() =
        JsonPrimitive(id)

    override fun makeCopy() =
        copy()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sound

        if (name != other.name) return false
        if (dataFormat != other.dataFormat) return false
        if (assetId != other.assetId) return false
        if (rate != other.rate) return false
        if (sampleCount != other.sampleCount) return false
        if (path != other.path) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false
        if (fieldValue != other.fieldValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + dataFormat.hashCode()
        result = 31 * result + assetId.hashCode()
        result = 31 * result + (rate ?: 0)
        result = 31 * result + (sampleCount ?: 0)
        result = 31 * result + (path?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + fieldValue.hashCode()
        return result
    }
}