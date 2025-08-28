package de.thecommcraft.scratchdsl.build

interface Asset {
    val name: String
    val assetId: String
    val dataFormat: String
}

data class Costume(
    override val name: String,
    override val dataFormat: String,
    override val assetId: String,
    val rotationCenter: Pair<Int, Int>
) : Asset

class Comment

class Sound