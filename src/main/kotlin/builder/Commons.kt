package de.thecommcraft.scratchdsl.builder

import kotlin.random.Random

data class Sprite(
    val stacks: List<BlockStack>
)

interface BlockParent

interface Blocks : BlockParent {
    val blocks: List<Block>
}

data class BlockStack(
    override val blocks: List<Block>
) : Blocks

data class SubBlocks(
    override val blocks: List<Block>
) : Blocks

data class InputRepresentation(
    val type: InputType,
    val inputId: String?,
    val inputBlockRepresentation: MinifiedBlockRepresentation?,
    val obscuredShadowId: String?,
    val obscuredShadowBlockRepresentation: MinifiedBlockRepresentation?
) {
    companion object {
        enum class InputType(val value: Int) {
            SHADOW(1),
            NO_SHADOW(2),
            OBSCURED_SHADOW(3)
        }
    }
}

const val ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun randomId(): String {
    val rawBinary = Random.nextBytes(16)
    return rawBinary.map { it ->
        ALLOWED_CHARS[it.toInt().mod(ALLOWED_CHARS.length)]
    }.joinToString("")
}