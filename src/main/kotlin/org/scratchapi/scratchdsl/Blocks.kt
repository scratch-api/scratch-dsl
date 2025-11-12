@file:Suppress("unused")

package org.scratchapi.scratchdsl

import kotlinx.serialization.json.*

open class NormalBlock internal constructor(override val opcode: String?) : Block {
    override var next: AnyBlock? = null
    private var myId: String? = null
    override var parent: String? = null
    override var shadow = false
    override var topLevel = false
    val shadowlessExpressionInputs = mutableMapOf<String, Expression?>()
    val expressionInputs = mutableMapOf<String, Pair<ShadowExpression, Expression?>?>()
    val blockStackInputs = mutableMapOf<String, BlockStack?>()
    val fields = mutableMapOf<String, Field?>()
    val mutation = mutableMapOf<String, Representation?>()

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

    override fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String?) {
        parent = parentId
        map[id] = this
        shadowlessExpressionInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            if (u.independent) u.flattenInto(map, id)
        }
        expressionInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            val (s, e) = u
            if (s.independent) s.flattenInto(map, id)
            if (e?.independent == true) e.flattenInto(map, id)
        }
        blockStackInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            u.flattenInto(map, id)
        }
    }

    fun representInputs(): Representation =
        buildJsonObject {
            shadowlessExpressionInputs.forEach { (t, u) ->
                if (u == null) return@forEach
                put(t, u.representAsInput())
            }
            expressionInputs.forEach { (t, u) ->
                if (u == null) return@forEach
                val (s, e) = u
                put(t, s.representAsInputWith(e))
            }
            blockStackInputs.forEach { (t, u) ->
                if (u == null) return@forEach
                if (u.isEmpty()) return@forEach
                u.id = u.contents[0].id
                put(t, buildJsonArray {
                    add(2)
                    add(JsonPrimitive(u.id))
                })
            }
        }

    fun representFields(): Representation =
        buildJsonObject {
            fields.forEach { (t, u) ->
                if (u == null) return@forEach
                put(t, buildJsonArray {
                    add(u.fieldValue.value)
                    add(u.fieldValue.id)
                })
            }
        }

    override fun represent(): Representation =
        buildJsonObject {
            put("inputs", representInputs())
            put("fields", representFields())
            if (mutation.isNotEmpty()) {
                put("mutation", buildJsonObject {
                    mutation.forEach { (t, u) ->
                        if (u == null) return@forEach
                        put(t, u)
                    }
                })
            }
            put("topLevel", topLevel)
            put("next", next?.id)
            put("parent", parent)
            put("shadow", shadow)
            put("opcode", opcode)
            if (topLevel) {
                put("x", 0)
                put("y", 0)
            }
        }

    override fun prepareRepresent(sprite: Sprite) {
        shadowlessExpressionInputs.forEach { (t, u) ->
            if (u == null) return@forEach
            u.prepareRepresent(sprite)
            val newU = if (u is NonShadowShouldCopy) u.makeCopy() else u
            shadowlessExpressionInputs[t] = newU
        }
        expressionInputs.forEach { (t, u) ->
            if (u == null) return@forEach
            val (s, e) = u
            s.prepareRepresent(sprite)
            e?.prepareRepresent(sprite)
            val newS = if (s is ShadowShouldCopy) s.makeCopy() else s
            val newE = if (e is NonShadowShouldCopy) e.makeCopy() else e
            expressionInputs[t] = newS to newE
        }
        blockStackInputs.forEach { (_, u) ->
            if (u == null) return@forEach
            u.prepareRepresent(sprite)
        }
    }

//    override fun loadInto(representation: Representation) {
//
//    }

    override fun cloneBlock(): Block {
        val cloned = NormalBlock(opcode)
        shadowlessExpressionInputs.forEach { (t, u) ->
            cloned.shadowlessExpressionInputs[t] = u?.cloneExpression()
        }
        expressionInputs.forEach { (t, u) ->
            cloned.expressionInputs[t] = u?.let {
                it.first.cloneShadowExpression() to it.second?.cloneExpression()
            }
        }
        blockStackInputs.forEach { (t, u) ->
            cloned.blockStackInputs[t] = u?.cloneBlockStack()
        }
        fields.forEach { (t, u) ->
            cloned.fields[t] = u
        }
        return cloned
    }
}

interface HandlesSet : Expression {
    var expressionSetHandler: ((Expression?) -> Block)?
    var expressionChangeHandler: ((Expression?) -> Block)?
}

/**
 * Adds a field to a block.
 * @param name The name of the field.
 * @param field The field to add.
 */
fun<B: NormalBlock> B.withField(name: String, field: Field) =
    this.apply {
        fields[name] = field
    }

/**
 * Adds an expression input to a block.
 * @param name The name of the input.
 * @param expression The expression to add.
 * @param shadowExpression The shadow expression to use if the main expression is null. Unless it is null, it will always be included as a shadow.
 */
fun<B: NormalBlock> B.withExpression(
    name: String,
    expression: Expression? = null,
    shadowExpression: ShadowExpression? = null
) = apply {
    if (shadowExpression != null && expression !is ShadowExpression) {
        expressionInputs[name] = shadowExpression to expression
    } else {
        if (expression is ValueShadowExpression && shadowExpression is ValueShadowExpression) {
            shadowlessExpressionInputs[name] = ValueShadowExpression(expression.value, shadowExpression.opcode)
            return@apply
        }
        shadowlessExpressionInputs[name] = expression
    }
}

/**
 * Adds mutation data to a block.
 * @param name The name of the mutation entry.
 * @param value The value of the mutation entry.
 */
fun<B: NormalBlock> B.withMutation(
    name: String,
    value: Representation? = null
) = apply {
    mutation[name] = value
}

/**
 * Adds default mutation entries to a block.
 */
fun<B: NormalBlock> B.withDefaultMutation() =
    withMutation("tagName", JsonPrimitive("mutation"))
        .withMutation("children", JsonArray(listOf()))

/**
 * Defines a block to be used to set an expression to a certain value.
 * @param block The code block that returns the block to execute.
 */
fun<B: HandlesSet> B.withHandlesSet(block: (Expression?) -> Block) = this.apply {
    expressionSetHandler = block
}

/**
 * Defines a block to be used to change an expression to a certain value.
 * @param block The code block that returns the block to execute.
 */
fun<B: HandlesSet> B.withHandlesChange(block: (Expression?) -> Block) = this.apply {
    expressionChangeHandler = block
}

/**
 * Changes the opcode of a shadow expression.
 * @param opcode The new opcode.
 */
fun Expression.changeShadowOpcode(opcode: String?): Expression {
    if (this is OpcodeSettableShadowExpression) {
        this.opcode = opcode
    }
    return this
}

open class NormalBlockBlockHost internal constructor(opcode: String?, subStack: BlockStack?) : NormalBlock(opcode),
    BlockBlockHost {
    val blocks = mutableListOf<AnyBlock>()

    init {
        blockStackInputs["SUBSTACK"] = subStack
    }

    override val stacks = listOfNotNull(subStack)

    override fun<B: AnyBlock> addBlock(block: B) = block.apply(stacks[0]::addBlock)
}

open class ConditionalBlockBlockHost internal constructor(opcode: String?, expression: Expression?, subStack: BlockStack?) : NormalBlockBlockHost(opcode, subStack) {

    init {
        shadowlessExpressionInputs["CONDITION"] = expression
    }
}

class IfElseBlock internal constructor(
    expression: Expression?,
    subStack: BlockStack?,
    secondarySubStack: BlockStack
) : ConditionalBlockBlockHost("control_if_else", expression, subStack) {
    init {
        blockStackInputs["SUBSTACK2"] = secondarySubStack
    }
}

class NormalHatBlock internal constructor(opcode: String?) : NormalBlock(opcode), HatBlock {
    val blockStack = BlockStack()
    override var topLevel: Boolean = true
    override val stacks = listOf(blockStack)
    init {
        addBlock(this)
    }
    override fun <B : AnyBlock> addBlock(block: B) =
        blockStack.addBlock(block)

    override fun prepareRepresent(sprite: Sprite) {
        super.prepareRepresent(sprite)
        blockStack.prepareRepresent(sprite)
    }
}

class IsolatedBlockStackHat internal constructor(val blockStack: BlockStack) : HatBlock {
    private val actualBlock: AnyBlock
        get() {
            if (blockStack.contents.size == 0) throw IllegalStateException("You need to add blocks to the BlockStack before doing that.")
            return blockStack.contents[0]
        }
    override val opcode get() = actualBlock.opcode
    override var next: AnyBlock?
        get() = blockStack.contents.getOrNull(1)
        set(_) { }
    override var parent: String? = null
    override var shadow = false
    override var topLevel = true
    override val stacks = listOf(blockStack)

    override var id: String
        get() = actualBlock.id
        set(value) {
            actualBlock.id = value
        }

    override fun represent(): Representation = actualBlock.represent()

    override fun flattenInto(map: MutableMap<String, AnyBlock>, parentId: String?) =
        actualBlock.flattenInto(map)

    override fun<B: AnyBlock> addBlock(block: B) = block.apply(blockStack::addBlock)

//    override fun loadInto(representation: Representation) {
//
//    }

    override fun prepareRepresent(sprite: Sprite) {
        actualBlock.topLevel = true
        blockStack.prepareRepresent(sprite)
    }

    override fun cloneBlock(): Block {
        return IsolatedBlockStackHat(blockStack.cloneBlockStack())
    }
}

class HalfIfElse internal constructor(val blockHost: BlockHost, val expression: Expression?, val block: BlockHost.() -> Unit)

sealed interface AnyKeyboardKey {
    val key: String
}

class ChosenKeyboardKey(override val key: String) : AnyKeyboardKey

/**
 * Creates a 'not' operator block.
 * @receiver The boolean expression to negate.
 * @return A 'not' expression block.
 */
operator fun Expression.not(): Expression = notBlock(this)

/** Converts a Double to a text expression. */
val Double.expr get() = ValueInput.TEXT.of(this.toString())
/** Converts an Int to a text expression. */
val Int.expr get() = ValueInput.TEXT.of(this.toString())
/** Converts a String to a text expression. */
val String.expr get() = ValueInput.TEXT.of(this)

/**
 * Can be used to work with blocks that are not in the standard block categories.
 */
class ManuallyMadeBlock(opcode: String?) : NormalBlock(opcode)

/**
 * Creates an isolated stack of blocks that is not attached to any hat block.
 * @param block The blocks to be placed in the isolated stack.
 * @return The created hat block wrapping the isolated stack.
 */
fun HatBlockHost.isolated(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = IsolatedBlockStackHat(BlockStack().apply(block))
    return addHatBlock(hatBlock)
}

// Motion

/**
 * Adds a 'move steps' block.
 * @param steps The number of steps to move.
 */
fun BlockHost.moveSteps(steps: Expression?) =
    addBlock(
        NormalBlock("motion_movesteps")
            .withExpression("STEPS", steps, ValueInput.NUMBER.of("10"))
    )

/**
 * Adds a 'turn right' block.
 * @param degrees The number of degrees to turn right.
 */
fun BlockHost.turnRight(degrees: Expression?) =
    addBlock(
        NormalBlock("motion_turnright")
            .withExpression("DEGREES", degrees, ValueInput.NUMBER.of("15")))

/**
 * Adds a 'turn left' block.
 * @param degrees The number of degrees to turn left.
 */
fun BlockHost.turnLeft(degrees: Expression?) =
    addBlock(
        NormalBlock("motion_turnleft")
            .withExpression("DEGREES", degrees, ValueInput.NUMBER.of("15")))


/**
 * Adds a 'go to location' block.
 * @param to The target location, either a [SpecialLocation] or an [Expression] that resolves to the name of a special location.
 */
fun BlockHost.gotoLocation(to: Expression?) =
    addBlock(
        NormalBlock("motion_goto")
            .withExpression("TO", to?.changeShadowOpcode("motion_goto_menu"), SpecialLocation.default))

/**
 * Adds a 'go to x y' block.
 * @param x The x-coordinate.
 * @param y The y-coordinate.
 */
fun BlockHost.gotoXY(x: Expression?, y: Expression?) =
    addBlock(
        NormalBlock("motion_gotoxy")
            .withExpression("X", x, ValueInput.NUMBER.of("0"))
            .withExpression("Y", y, ValueInput.NUMBER.of("0")))

/**
 * Adds a 'glide to location' block.
 * @param to The target location, either a [SpecialLocation] or an [Expression] that resolves to the name of a special location.
 * @param secs The duration of the glide in seconds.
 */
fun BlockHost.glideToLocation(to: Expression?, secs: Expression?) =
    addBlock(
        NormalBlock("motion_glideto")
            .withExpression("SECS", secs, ValueInput.NUMBER.of("1"))
            .withExpression("TO", to?.changeShadowOpcode("motion_glideto_menu"), SpecialLocation.default))

/**
 * Adds a 'glide to x, y' block.
 * @param x The target x-coordinate.
 * @param y The target y-coordinate.
 * @param secs The duration of the glide in seconds.
 */
fun BlockHost.glideToXY(x: Expression?, y: Expression?, secs: Expression?) =
    addBlock(
        NormalBlock("motion_glidesecstoxy")
            .withExpression("SECS", secs, ValueInput.NUMBER.of("1"))
            .withExpression("X", x, ValueInput.NUMBER.of("0"))
            .withExpression("Y", y, ValueInput.NUMBER.of("0")))


/**
 * Adds a 'point in direction' block.
 * @param direction The direction to point in degrees.
 */
fun BlockHost.pointInDirection(direction: Expression?) =
    addBlock(
        NormalBlock("motion_pointindirection")
            .withExpression("DIRECTION", direction, ValueInput.ANGLE.of("90")))

/**
 * Adds a 'point towards' block.
 * @param towards The target location, either a [SpecialDirection] or an [Expression] that resolves to the name of a special direction.
 */
fun BlockHost.pointTowards(towards: Expression?) =
    addBlock(
        NormalBlock("motion_pointtowards")
            .withExpression("TOWARDS", towards?.changeShadowOpcode("motion_pointtowards_menu"), SpecialDirection.default))

/**
 * Adds a 'change x by' block.
 * @param dx The amount to change the x-coordinate by.
 */
fun BlockHost.changeXBy(dx: Expression?) =
    addBlock(
        NormalBlock("motion_changexby")
            .withExpression("DX", dx, ValueInput.NUMBER.of("10")))

/**
 * Adds a 'set x to' block.
 * @param x The new x-coordinate.
 */
fun BlockHost.setXTo(x: Expression?) =
    addBlock(
        NormalBlock("motion_setx")
            .withExpression("X", x, ValueInput.NUMBER.of("0")))

/**
 * Adds a 'change y by' block.
 * @param dy The amount to change the y-coordinate by.
 */
fun BlockHost.changeYBy(dy: Expression?) =
    addBlock(
        NormalBlock("motion_changeyby")
            .withExpression("DY", dy, ValueInput.NUMBER.of("10")))

/**
 * Adds a 'set y to' block.
 * @param y The new y-coordinate.
 */
fun BlockHost.setYTo(y: Expression?) =
    addBlock(
        NormalBlock("motion_sety")
            .withExpression("Y", y, ValueInput.NUMBER.of("0")))


/**
 * Adds an 'if on edge, bounce' block.
 */
fun BlockHost.ifOnEdgeBounce() =
    addBlock(NormalBlock("motion_ifonedgebounce"))


/**
 * Adds a 'set rotation style' block.
 * @param rotationStyle The desired rotation style.
 */
fun BlockHost.setRotationStyle(rotationStyle: RotationStyle) =
    addBlock(
        NormalBlock("motion_setrotationstyle")
            .withField("STYLE", Field.of(rotationStyle.value)))


/** A reporter for the sprite's x-position. Can also be used to set or change the x-position. */
val xPosition get() =
    HandlesSetNormalExpression("motion_xposition")
        .withHandlesSet { x ->
            NormalBlock("motion_setx")
                .withExpression("X", x, ValueInput.NUMBER.of("0"))
        }
        .withHandlesChange { dx ->
            NormalBlock("motion_changexby")
                .withExpression("DX", dx, ValueInput.NUMBER.of("10"))
        }

/** A reporter for the sprite's y-position. Can also be used to set or change the y-position. */
val yPosition get() =
    HandlesSetNormalExpression("motion_yposition")
        .withHandlesSet { y ->
            NormalBlock("motion_sety")
                .withExpression("Y", y, ValueInput.NUMBER.of("0"))
        }
        .withHandlesChange { dy ->
            NormalBlock("motion_changeyby")
                .withExpression("DY", dy, ValueInput.NUMBER.of("10"))
        }

/** A reporter for the sprite's direction. Can also be used to set or change the direction. */
val direction get() =
    HandlesSetNormalExpression("motion_direction")
        .withHandlesSet { direction ->
            NormalBlock("motion_pointindirection")
                .withExpression("DIRECTION", direction, ValueInput.ANGLE.of("90"))
        }
        .withHandlesChange { degrees ->
            NormalBlock("motion_turnright")
                .withExpression("DEGREES", degrees, ValueInput.NUMBER.of("15"))
        }

@Deprecated("\"rotation\" is a bad name. ", replaceWith = ReplaceWith("direction"))
val rotation inline get() =
    direction


// Looks

/**
 * Adds a 'say for secs' block.
 * @param message The message to display.
 * @param secs The duration to display the message.
 */
fun BlockHost.sayForSecs(message: Expression?, secs: Expression?) =
    addBlock(NormalBlock("looks_sayforsecs")
        .withExpression("MESSAGE", message, ValueInput.TEXT.of("Hello!"))
        .withExpression("SECS", secs, ValueInput.NUMBER.of("2")))

/**
 * Adds a 'say' block.
 * @param message The message to display.
 */
fun BlockHost.say(message: Expression?) =
    addBlock(NormalBlock("looks_say")
        .withExpression("MESSAGE", message, ValueInput.TEXT.of("Hello!"))
    )

/**
 * Adds a 'think for secs' block.
 * @param message The message to think.
 * @param secs The duration to display the thought bubble.
 */
fun BlockHost.thinkForSecs(message: Expression?, secs: Expression?) =
    addBlock(NormalBlock("looks_thinkforsecs")
        .withExpression("MESSAGE", message, ValueInput.TEXT.of("Hmm..."))
        .withExpression("SECS", secs, ValueInput.NUMBER.of("2")))

/**
 * Adds a 'think' block.
 * @param message The message to think.
 */
fun BlockHost.think(message: Expression?) =
    addBlock(NormalBlock("looks_think")
        .withExpression("MESSAGE", message, ValueInput.TEXT.of("Hmm...")))


/**
 * Adds a 'switch costume to' block.
 * @param costume The costume to switch to.
 */
fun BlockHost.switchToCostume(costume: Expression?) =
    addBlock(
        NormalBlock("looks_switchcostumeto")
            .withExpression("COSTUME", costume, FirstSprite))

/**
 * Adds a 'next costume' block.
 */
fun BlockHost.switchToNextCostume() =
    addBlock(NormalBlock("looks_nextcostume"))

/**
 * Adds a 'switch backdrop to' block.
 * @param backdrop The backdrop to switch to.
 */
fun BlockHost.switchToBackdrop(backdrop: Expression?) =
    addBlock(
        NormalBlock("looks_switchbackdropto")
            .withExpression("BACKDROP", backdrop.asBackdrop(), FirstBackdrop))

/**
 * Adds a 'next backdrop' block.
 */
fun BlockHost.switchToNextBackdrop() =
    addBlock(NormalBlock("looks_nextbackdrop"))


/**
 * Adds a 'change size by' block.
 * @param change The amount to change the size by.
 */
fun BlockHost.changeSizeBy(change: Expression?) =
    addBlock(
        NormalBlock("looks_changesizeby")
            .withExpression("CHANGE", change, ValueInput.NUMBER.of("10")))

/**
 * Adds a 'set size to' block.
 * @param size The new size percentage.
 */
fun BlockHost.setSizeTo(size: Expression?) =
    addBlock(
        NormalBlock("looks_setsizeto")
            .withExpression("SIZE", size, ValueInput.NUMBER.of("100")))


/**
 * Adds a 'change effect by' block.
 * @param effect The graphic effect to change.
 * @param change The amount to change the effect by.
 */
fun BlockHost.changeLooksEffectBy(effect: LooksEffect, change: Expression?) =
    addBlock(
        NormalBlock("looks_changeeffectby")
            .withExpression("CHANGE", change, ValueInput.NUMBER.of("25"))
            .withField("EFFECT", Field.of(effect.value)))

/**
 * Adds a 'set effect to' block.
 * @param effect The graphic effect to set.
 * @param value The value to set the effect to.
 */
fun BlockHost.setLooksEffect(effect: LooksEffect, value: Expression?) =
    addBlock(
        NormalBlock("looks_seteffectto")
            .withExpression("VALUE", value, ValueInput.NUMBER.of("0"))
            .withField("EFFECT", Field.of(effect.value)))

/**
 * Adds a 'clear graphic effects' block.
 */
fun BlockHost.clearGraphicEffects() =
    addBlock(NormalBlock("looks_cleargraphiceffects"))


/**
 * Adds a 'show' block.
 */
fun BlockHost.show() =
    addBlock(NormalBlock("looks_show"))

/**
 * Adds a 'hide' block.
 */
fun BlockHost.hide() =
    addBlock(NormalBlock("looks_hide"))


/**
 * Adds a 'go to layer' block.
 * @param layer The layer to move to (front or back).
 */
fun BlockHost.goToLayer(layer: SpecialLayer) =
    addBlock(
        NormalBlock("looks_gotofrontback")
            .withField("FRONT_BACK", Field.of(layer.value)))

/**
 * Adds a 'go forward/backward layers' block.
 * @param layerDirection The direction to move (forward or backward).
 * @param layers The number of layers to move.
 */
fun BlockHost.changeLayer(layerDirection: LayerDirection, layers: Expression?) =
    addBlock(
        NormalBlock("looks_goforwardbackwardlayers")
            .withExpression("NUM", layers, ValueInput.INTEGER.of("1"))
            .withField("FORWARD_BACKWARD", Field.of(layerDirection.value)))


/** A reporter for the current costume's name. */
val currentCostumeName: CurrentCostume
    get() = CurrentCostume(false)

/** A reporter for the current costume's number. */
val currentCostumeNumber: CurrentCostume
    get() = CurrentCostume(true)

/** A reporter for the current costume's name. */
val currentCostume: CurrentCostume
    get() = CurrentCostume(false)

/** A reporter for the sprite's size. Can also be used to set or change the size. */
val BlockHost.size get() =
    HandlesSetNormalExpression("looks_size")
        .withHandlesSet { size ->
            NormalBlock("looks_setsizeto")
                .withExpression("SIZE", size, ValueInput.NUMBER.of("100"))
        }
        .withHandlesChange { change ->
            NormalBlock("looks_changesizeby")
                .withExpression("CHANGE", change, ValueInput.NUMBER.of("10"))
        }

// Sound

/**
 * Adds a 'play sound until done' block.
 * @param sound The sound to play.
 */
fun BlockHost.playSoundUntilDone(sound: Expression?) =
    addBlock(
        NormalBlock("sound_playuntildone")
            .withExpression("SOUND_MENU", sound, FirstSound))

/**
 * Adds a 'start sound' block.
 * @param sound The sound to start playing.
 */
fun BlockHost.playSound(sound: Expression?) =
    addBlock(
        NormalBlock("sound_play")
            .withExpression("SOUND_MENU", sound, FirstSound))

/**
 * Adds a 'stop all sounds' block.
 */
fun BlockHost.stopAllSounds() =
    addBlock(NormalBlock("sound_stopallsounds"))


/**
 * Adds a 'change sound effect by' block.
 * @param soundEffect The sound effect to change.
 * @param value The amount to change the effect by.
 */
fun BlockHost.changeSoundEffectBy(soundEffect: SoundEffect, value: Expression?) =
    addBlock(
        NormalBlock("sound_changeeffectby")
            .withExpression("VALUE", value, ValueInput.NUMBER.of("10"))
            .withField("EFFECT", Field.of(soundEffect.value)))

/**
 * Adds a 'set sound effect to' block.
 * @param soundEffect The sound effect to set.
 * @param value The value to set the effect to.
 */
fun BlockHost.setSoundEffect(soundEffect: SoundEffect, value: Expression?) =
    addBlock(
        NormalBlock("sound_seteffectto")
            .withExpression("VALUE", value, ValueInput.NUMBER.of("100"))
            .withField("EFFECT", Field.of(soundEffect.value)))

/**
 * Adds a 'clear sound effects' block.
 */
fun BlockHost.clearSoundEffects() =
    addBlock(NormalBlock("sound_cleareffects"))


/**
 * Adds a 'change volume by' block.
 * @param volume The amount to change the volume by.
 */
fun BlockHost.changeVolumeBy(volume: Expression?) =
    addBlock(
        NormalBlock("sound_changevolumeby")
            .withExpression("VOLUME", volume, ValueInput.NUMBER.of("-10")))

/**
 * Adds a 'set volume to' block.
 * @param volume The new volume percentage.
 */
fun BlockHost.setVolume(volume: Expression?) =
    addBlock(
        NormalBlock("sound_setvolumeto")
            .withExpression("VOLUME", volume, ValueInput.NUMBER.of("100")))

/** A reporter for the volume. Can also be used to set or change the volume. */
val BlockHost.volume get() =
    HandlesSetNormalExpression("sound_volume")
        .withHandlesSet { volume ->
            NormalBlock("sound_setvolumeto")
                .withExpression("VOLUME", volume, ValueInput.NUMBER.of("100"))
        }
        .withHandlesChange { volume ->
            NormalBlock("sound_changevolumeby")
                .withExpression("VOLUME", volume, ValueInput.NUMBER.of("-10"))
        }

// Events

/**
 * Creates a 'when green flag clicked' hat block.
 * @param block The script to run when the green flag is clicked.
 */
fun HatBlockHost.whenGreenFlagClicked(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = NormalHatBlock("event_whenflagclicked")
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

/**
 * Creates a 'when key pressed' hat block.
 * @param key The key that triggers the script.
 * @param block The script to run.
 */
fun HatBlockHost.whenKeyPressed(key: AnyKeyboardKey, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenkeypressed")
        .withField("KEY_OPTION", Field.of(key.key))
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

/**
 * Creates a 'when this sprite clicked' hat block.
 * @param block The script to run when the sprite is clicked.
 */
fun HatBlockHost.whenClicked(block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenthisspriteclicked")
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

/**
 * Creates a 'when backdrop switches to' hat block.
 * @param backdrop The backdrop that triggers the script.
 * @param block The script to run.
 */
fun HatBlockHost.whenBackdropSwitchesTo(backdrop: Costume, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenbackdropswitchesto")
        .withField("BACKDROP", backdrop)
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}


/**
 * Creates a 'when loudness >' or 'when timer >' hat block.
 * @param compared The value to compare against (loudness or timer).
 * @param value The threshold value.
 * @param block The script to run.
 */
fun HatBlockHost.whenGreaterThan(compared: WhenGreaterThanComparedValue, value: Expression?, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenbackdropswitchesto")
        .withExpression("VALUE", value, ValueInput.NUMBER.of("10"))
        .withField("WHENGREATERTHANMENU", Field.of(compared.value))
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}


/**
 * Creates a 'when I receive' hat block.
 * @param broadcast The broadcast message that triggers the script.
 * @param block The script to run.
 */
fun HatBlockHost.whenIReceive(broadcast: Broadcast, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenbackdropswitchesto")
        .withField("BROADCAST_OPTION", broadcast)
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

/**
 * Adds a 'broadcast' block.
 * @param broadcast The message to broadcast.
 */
fun BlockHost.broadcast(broadcast: Broadcast) =
    addBlock(
        NormalBlock("event_broadcast")
            .withExpression("BROADCAST_INPUT", broadcast, FirstBroadcast))

/**
 * Adds a 'broadcast and wait' block.
 * @param broadcast The message to broadcast.
 */
fun BlockHost.broadcastAndWait(broadcast: Broadcast) =
    addBlock(
        NormalBlock("event_broadcastandwait")
            .withExpression("BROADCAST_INPUT", broadcast, FirstBroadcast))

// Control

/**
 * Adds a 'wait' block.
 * @param duration The time to wait in seconds.
 */
fun BlockHost.waitDuration(duration: Expression?) =
    addBlock(
        NormalBlock("control_wait")
            .withExpression("DURATION", duration, ValueInput.POSITIVE_NUMBER.of("1")))

/**
 * Adds a 'repeat' loop.
 * @param expression The number of times to repeat.
 * @param block The script to repeat.
 */
fun BlockHost.repeatBlock(expression: Expression?, block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(
        NormalBlockBlockHost("control_repeat", stack)
            .withExpression("TIMES", expression, ValueInput.POSITIVE_INTEGER.of("10")))
}

/**
 * Adds a 'forever' loop.
 * @param block The script to run forever.
 */
fun BlockHost.foreverBlock(block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(NormalBlockBlockHost("control_forever", stack))
}


/**
 * Adds an 'if' block.
 * @param expression The condition to check.
 * @param block The script to run if the condition is true.
 */
fun BlockHost.ifBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_if", expression, stack))
}

/**
 * Starts an 'if-else' block.
 * @param expression The condition to check.
 * @param block The script to run if the condition is true.
 */
fun BlockHost.ifElseBlock(expression: Expression?, block: BlockHost.() -> Unit) =
    HalfIfElse(this, expression, block)

/**
 * Defines the 'else' part of an 'if-else' block.
 * @param block The script to run if the condition is false.
 */
infix fun HalfIfElse.elseBlock(block: BlockHost.() -> Unit): IfElseBlock {
    val stack = BlockStack().apply(this.block)
    val secStack = BlockStack().apply(block)
    return blockHost.addBlock(IfElseBlock(expression, stack, secStack))
}

/**
 * Adds a 'wait until' block.
 * @param expression The condition to wait for.
 */
fun BlockHost.waitUntilBlock(expression: Expression?): NormalBlock {
    return addBlock(
        NormalBlock("control_wait_until")
            .withExpression("CONDITION", expression)
    )
}

/**
 * Adds a 'repeat until' loop.
 * @param expression The condition to stop the loop.
 * @param block The script to repeat.
 */
fun BlockHost.repeatUntilBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_repeat_until", expression, stack))
}

/**
 * Adds a 'while' loop. This is a custom block concept, not a default Scratch block.
 * @param expression The condition to continue the loop.
 * @param block The script to repeat.
 */
fun BlockHost.whileBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_while", expression, stack))
}


/**
 * Adds a 'stop' block.
 * @param stopType The scope of what to stop (e.g., this script, all).
 */
fun BlockHost.stopBlock(stopType: StopType = StopType.THIS_SCRIPT): NormalBlock {
    val hasNext = stopType == StopType.OTHER_SCRIPTS_IN_SPRITE
    return addBlock(
        NormalBlock("control_stop")
            .withField("STOP_OPTION", Field.of(stopType.code))
            .withDefaultMutation()
            .withMutation("hasnext", JsonPrimitive(hasNext)))
}


/**
 * Creates a 'when I start as a clone' hat block.
 * @param block The script for the clone to run.
 */
fun HatBlockHost.whenIStartAsClone(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = NormalHatBlock("control_start_as_clone")
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

/**
 * Adds a 'create clone of' block.
 * @param cloneOption The sprite to clone.
 */
fun BlockHost.createCloneOf(cloneOption: Expression?) =
    addBlock(
        NormalBlock("control_create_clone_of")
            .withExpression("CLONE_OPTION", cloneOption, CloneTarget.myself))

/**
 * Adds a 'create clone of' block for a specific sprite.
 * @param sprite The sprite to clone.
 */
fun BlockHost.createCloneOf(sprite: Sprite) =
    createCloneOf(sprite.cloneTarget)

/**
 * Adds a 'delete this clone' block.
 */
fun BlockHost.deleteThisClone() =
    addBlock(NormalBlock("control_delete_this_clone"))

// Sensing

/**
 * Creates a 'touching' boolean reporter.
 * @param expression The object to check for touching.
 */
fun BlockHost.touching(expression: Expression?) =
    NormalExpression("sensing_touchingobject")
        .withExpression("TOUCHINGOBJECTMENU", expression, TouchObject.mouse)

/**
 * Creates a 'touching' boolean reporter for a specific sprite.
 * @param sprite The sprite to check for touching.
 */
fun BlockHost.touching(sprite: Sprite) =
    touching(sprite.touchObject)

/**
 * Creates a 'touching color' boolean reporter.
 * @param color The color to check for.
 */
fun BlockHost.touchingColor(color: Expression?) =
    NormalExpression("sensing_touchingcolor")
        .withExpression("COLOR", color, ValueInput.COLOUR_PICKER.of("#6deaa0"))

/**
 * Creates a 'color is touching color' boolean reporter.
 * @param other The other color.
 */
infix fun Expression?.colorTouchingColor(other: Expression?) =
    NormalExpression("sensing_coloristouchingcolor")
        .withExpression("COLOR", this, ValueInput.COLOUR_PICKER.of("#ba2a32"))
        .withExpression("COLOR2", other, ValueInput.COLOUR_PICKER.of("#c385eb"))

/**
 * Creates a 'distance to' reporter.
 * @param expression The object to measure the distance to.
 */
fun BlockHost.distanceTo(expression: Expression?) =
    NormalExpression("sensing_distanceto")
        .withExpression("DISTANCETOMENU", expression, DistanceObject.mouse)

/**
 * Creates a 'distance to' reporter for a specific sprite.
 * @param sprite The sprite to measure the distance to.
 */
fun BlockHost.distanceTo(sprite: Sprite) =
    distanceTo(sprite.distanceObject)


/**
 * Adds an 'ask and wait' block.
 * @param question The question to ask.
 */
fun BlockHost.askAndWait(question: Expression?) =
    addBlock(
        NormalBlock("sensing_askandwait")
            .withExpression("QUESTION", question, ValueInput.TEXT.of("What's your name?")))

/** A reporter for the user's last answer. */
val BlockHost.answer get() =
    NormalExpression("sensing_answer")


/**
 * Creates a 'key pressed' boolean reporter.
 * @param key The key to check.
 */
fun BlockHost.keyPressed(key: Expression?) =
    NormalExpression("sensing_keypressed")
        .withExpression("KEY_OPTION", key, KeyboardKey.SPACE.sensingKey)

/**
 * Creates a 'key pressed' boolean reporter for a specific key.
 * @param key The key to check.
 */
fun BlockHost.keyPressed(key: KeyboardKey) =
    keyPressed(key.sensingKey)

/** A boolean reporter for whether the mouse is down. */
val BlockHost.mouseDown get() =
    NormalExpression("sensing_mousedown")

/** A reporter for the mouse's x-position. */
val BlockHost.mouseX get() =
    NormalExpression("sensing_mousex")

/** A reporter for the mouse's y-position. */
val BlockHost.mouseY get() =
    NormalExpression("sensing_mousey")


/**
 * Adds a 'set drag mode' block.
 * @param dragMode The desired drag mode.
 */
fun BlockHost.setDragMode(dragMode: DragMode) =
    addBlock(
        NormalBlock("sensing_setdragmode")
            .withField("DRAG_MODE", Field.of(dragMode.value)))


/** A reporter for the microphone loudness. */
val BlockHost.loudness get() =
    NormalExpression("sensing_loudness")


/** A reporter for the timer value. */
val BlockHost.timer get() =
    NormalExpression("sensing_timer")

/**
 * Adds a 'reset timer' block.
 */
fun BlockHost.resetTimer() =
    addBlock(NormalBlock("sensing_resettimer"))


/**
 * Creates a 'property of' reporter.
 * @param sprite The sprite or stage to get the property from.
 * @param property The property to get.
 */
fun BlockHost.propertyOf(sprite: Expression?, property: Property) =
    NormalExpression("sensing_of")
        .withExpression("OBJECT", sprite, PropertyTarget.stage)
        .withField("PROPERTY", property)

/**
 * Creates a 'property of' reporter for a specific sprite.
 * @param sprite The sprite to get the property from.
 * @param property The property to get.
 */
fun BlockHost.propertyOf(sprite: Sprite, property: Property) =
    propertyOf(sprite.propertyTarget, property)

/**
 * Creates a 'property of' reporter using a string for the property name.
 * @param sprite The sprite or stage.
 * @param property The name of the property.
 */
fun BlockHost.propertyOf(sprite: Expression?, property: String) =
    propertyOf(sprite, Property.of(property))

/**
 * Creates a 'property of' reporter for a specific sprite using a string for the property name.
 * @param sprite The sprite.
 * @param property The name of the property.
 */
fun BlockHost.propertyOf(sprite: Sprite, property: String) =
    propertyOf(sprite.propertyTarget, Property.of(property))

/**
 * Creates a 'property of' reporter using a variable or list.
 * @param sprite The sprite or stage.
 * @param property The variable or list.
 */
fun BlockHost.propertyOf(sprite: Expression?, property: VariableLike) =
    propertyOf(sprite, property.property)

/**
 * Creates a 'property of' reporter for a specific sprite using a variable or list.
 * @param sprite The sprite.
 * @param property The variable or list.
 */
fun BlockHost.propertyOf(sprite: Sprite, property: VariableLike) =
    propertyOf(sprite.propertyTarget, property.property)


/**
 * Creates a 'current' time reporter.
 * @param timeUnit The unit of time to report (e.g., year, month, day).
 */
fun BlockHost.current(timeUnit: TimeUnit) =
    NormalExpression("sensing_current")
        .withField("CURRENTMENU", Field.of(timeUnit.value))

/** A reporter for the number of days since 2000. */
val BlockHost.daysSince2000 get() =
    NormalExpression("sensing_dayssince2000")

/** A reporter for the user's username. */
val BlockHost.username get() =
    NormalExpression("sensing_username")


// Operators


/**
 * Creates a 'round' operator block.
 * @param expression The number to round.
 */
fun round(expression: Expression?) =
    NormalUnaryOp("operator_round", expression, "NUM", ValueInput.NUMBER.of(""))

/**
 * Creates an addition operator block.
 */
operator fun Expression?.plus(other: Expression?) =
    NormalBinaryOp(
        "operator_add",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

/**
 * Creates a subtraction operator block.
 */
operator fun Expression?.minus(other: Expression?) =
    NormalBinaryOp(
        "operator_subtract",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

/**
 * Creates a multiplication operator block.
 */
operator fun Expression?.times(other: Expression?) =
    NormalBinaryOp(
        "operator_multiply",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

/**
 * Creates a division operator block.
 */
operator fun Expression?.div(other: Expression?) =
    NormalBinaryOp(
        "operator_divide",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

/**
 * Creates a 'pick random' operator block.
 */
operator fun Expression?.rangeTo(other: Expression?) =
    NormalBinaryOp(
        "operator_random",
        this,
        other,
        "FROM",
        "TO",
        ValueInput.NUMBER.of("1"),
        ValueInput.NUMBER.of("10")
    )

/**
 * Creates a 'greater than' comparison block.
 */
infix fun Expression?.greaterThan(other: Expression?) =
    NormalBinaryOp(
        "operator_gt",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )

/**
 * Creates a 'less than' comparison block.
 */
infix fun Expression?.lessThan(other: Expression?) =
    NormalBinaryOp(
        "operator_lt",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )

/**
 * Creates an 'equals' comparison block.
 */
infix fun Expression?.equals(other: Expression?) =
    NormalBinaryOp(
        "operator_equals",
        this,
        other,
        "OPERAND1",
        "OPERAND2",
        ValueInput.TEXT.of(""),
        ValueInput.TEXT.of("50")
    )

/**
 * Creates an 'and' logical operator block.
 */
infix fun Expression?.and(other: Expression?) =
    NormalBinaryOp(
        "operator_and",
        this,
        other,
        "OPERAND1",
        "OPERAND2"
    )

/**
 * Creates an 'or' logical operator block.
 */
infix fun Expression?.or(other: Expression?) =
    NormalBinaryOp(
        "operator_or",
        this,
        other,
        "OPERAND1",
        "OPERAND2"
    )

/**
 * Creates a 'not' logical operator block.
 */
fun notBlock(expression: Expression?) =
    NormalUnaryOp("operator_not", expression)

/**
 * Creates a 'join' operator block.
 */
infix fun Expression?.join(other: Expression?) =
    NormalBinaryOp(
        "operator_join",
        this,
        other,
        "STRING1",
        "STRING2",
        ValueInput.TEXT.of("apple "),
        ValueInput.TEXT.of("banana")
    )

/**
 * Creates a 'letter of' operator block.
 */
infix fun Expression?.letterOf(other: Expression?) =
    NormalBinaryOp(
        "operator_letter_of",
        this,
        other,
        "LETTER",
        "STRING",
        ValueInput.POSITIVE_INTEGER.of("1"),
        ValueInput.TEXT.of("apple")
    )

/**
 * Creates a 'length of' operator block.
 */
val Expression?.stringLength get() =
    NormalExpression("operator_length")
        .withExpression("STRING", this, ValueInput.TEXT.of("apple"))

/**
 * Creates a 'contains' operator block.
 */
infix fun Expression?.containsString(other: Expression?) =
    NormalBinaryOp(
        "operator_contains",
        this,
        other,
        "STRING1",
        "STRING2",
        ValueInput.TEXT.of("apple"),
        ValueInput.TEXT.of("a")
    )

/**
 * Creates a 'mod' operator block.
 */
operator fun Expression?.rem(other: Expression?) =
    NormalBinaryOp(
        "operator_mod",
        this,
        other,
        "NUM1",
        "NUM2",
        ValueInput.NUMBER.of(""),
        ValueInput.NUMBER.of("")
    )

// Mathops are defined using
// MathOps.<OPERATION>.of(<expression>)

// VLB

/**
 * Adds a 'set variable to' block.
 * @param variable The variable to set.
 * @param expression The value to set the variable to.
 */
fun BlockHost.setVar(variable: Variable, expression: Expression?) =
    addBlock(
        NormalBlock("data_setvariableto")
            .withExpression("VALUE", expression, ValueInput.TEXT.of("0"))
            .withField("VARIABLE", variable))

/**
 * Adds a 'change variable by' block.
 * @param variable The variable to change.
 * @param expression The amount to change the variable by.
 */
fun BlockHost.changeVar(variable: Variable, expression: Expression?) =
    addBlock(
        NormalBlock("data_changevariableby")
            .withExpression("VALUE", expression, ValueInput.NUMBER.of("1"))
            .withField("VARIABLE", variable))

/**
 * Adds a 'show variable' block.
 * @param variable The variable to show.
 */
fun BlockHost.showVar(variable: Variable) =
    addBlock(
        NormalBlock("data_showvariable")
            .withField("VARIABLE", variable))

/**
 * Adds a 'hide variable' block.
 * @param variable The variable to hide.
 */
fun BlockHost.hideVar(variable: Variable) =
    addBlock(
        NormalBlock("data_hidevariable")
            .withField("VARIABLE", variable))



/**
 * Adds an 'add to list' block.
 * @param list The list to add to.
 * @param value The value to add.
 */
fun BlockHost.append(list: ScratchList, value: Expression?) =
    addBlock(
        NormalBlock("data_addtolist")
            .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
            .withField("LIST", list))


/**
 * Adds a 'delete of list' block.
 * @param list The list to delete from.
 * @param index The index of the item to delete.
 */
fun BlockHost.deleteAtIndex(list: ScratchList, index: Expression?) =
    addBlock(
        NormalBlock("data_deleteoflist")
            .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
            .withField("LIST", list))

/**
 * Adds a 'delete all of list' block.
 * @param list The list to clear.
 */
fun BlockHost.deleteAll(list: ScratchList) =
    addBlock(
        NormalBlock("data_deletealloflist")
            .withField("LIST", list))

/**
 * Adds an 'insert at of list' block.
 * @param list The list to insert into.
 * @param value The value to insert.
 * @param index The index to insert at.
 */
fun BlockHost.insertAtIndex(list: ScratchList, value: Expression?, index: Expression?) =
    addBlock(
        NormalBlock("data_insertatlist")
            .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
            .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
            .withField("LIST", list))

/**
 * Adds a 'replace item of list' block.
 * @param list The list to modify.
 * @param value The new value.
 * @param index The index of the item to replace.
 */
fun BlockHost.replaceAtIndex(list: ScratchList, value: Expression?, index: Expression?) =
    addBlock(
        NormalBlock("data_replaceitemoflist")
            .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
            .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
            .withField("LIST", list))


/**
 * Creates an 'item of list' reporter.
 * @param index The index of the item to get.
 */
operator fun ScratchList.get(index: Expression?) =
    HandlesSetNormalUnaryOp("data_itemoflist", index, "INDEX", ValueInput.INTEGER.of("1"))
        .withField("LIST", this)
        .withHandlesSet { expression ->
            NormalBlock("data_replaceitemoflist")
                .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
                .withExpression("ITEM", expression, ValueInput.TEXT.of("thing"))
                .withField("LIST", this)
        }

/**
 * Creates an 'item # of in list' reporter.
 * @param value The value to find the index of.
 */
fun ScratchList.indexOf(value: Expression?) =
    NormalUnaryOp("data_itemnumoflist", value, "ITEM", ValueInput.TEXT.of("thing"))
        .withField("LIST", this)

/** A reporter for the length of a list. */
val ScratchList.listLength: Expression
    get() =
        NormalExpression("data_lengthoflist")
            .withField("LIST", this)

/**
 * Creates a 'list contains' boolean reporter.
 * @param value The value to check for.
 */
infix fun ScratchList.containsItem(value: Expression?) =
    NormalUnaryOp("data_listcontainsitem", value, "ITEM", ValueInput.TEXT.of("thing"))
        .withField("LIST", this)


/**
 * Adds a 'show list' block.
 * @param list The list to show.
 */
fun BlockHost.showList(list: ScratchList) =
    addBlock(
        NormalBlock("data_showlist")
            .withField("LIST", list))

/**
 * Adds a 'hide list' block.
 * @param list The list to hide.
 */
fun BlockHost.hideList(list: ScratchList) =
    addBlock(
        NormalBlock("data_hidelist")
            .withField("LIST", list))

// My Blocks

class ProcedurePrototypeBuilder(name: String, val warp: Boolean) {
    internal val arguments = mutableListOf<ProcedureArgument>()
    internal var proccode = name
    internal var proc: Procedure? = null
    /**
     * Adds a string or number input to the custom block definition.
     * @param name The name of the argument.
     * @param default The default value.
     */
    fun stringNumber(name: String, default: String = ""): ProcedureArgumentStringNumber {
        proccode += " %s"
        val argument = ProcedureArgumentStringNumberShadow(name, default)
        arguments.add(argument)
        return ProcedureArgumentStringNumber(name, default, argument.argumentId)
    }
    /**
     * Adds a number input to the custom block definition.
     * @param name The name of the argument.
     * @param default The default value.
     */
    fun number(name: String, default: String = "1"): ProcedureArgumentStringNumber {
        proccode += " %n"
        val argument = ProcedureArgumentStringNumberShadow(name, default)
        arguments.add(argument)
        return ProcedureArgumentStringNumber(name, default, argument.argumentId)
    }
    /**
     * Adds a boolean input to the custom block definition.
     * @param name The name of the argument.
     * @param default The default value.
     */
    fun boolean(name: String, default: String = "false"): ProcedureArgumentBoolean {
        proccode += " %b"
        val argument = ProcedureArgumentBooleanShadow(name, default)
        arguments.add(argument)
        return ProcedureArgumentBoolean(name, default, argument.argumentId)
    }
    /**
     * Adds a label to the custom block definition.
     * @param text The text of the label.
     */
    fun text(text: String) {
        proccode += " "
        proccode += text
    }
    /**
     * Manually sets the proccode for the custom block definition.
     * @param text The new proccode.
     */
    fun setProccode(text: String) {
        proccode = text
    }
}

/**
 * Defines a custom block procedure.
 * @param name The initial name/label of the procedure.
 * @param warp If true, the procedure will run without screen refresh.
 * @param block The builder lambda to define arguments and labels.
 * @return A lazy-initialized procedure object that can be called.
 */
fun HatBlockHost.procedure(name: String, warp: Boolean = false, block: ProcedurePrototypeBuilder.() -> Unit): Lazy<Procedure> {
    return lazy {
        val builder = ProcedurePrototypeBuilder(name, warp).apply(block)
        builder.proc?.let {
            return@lazy it
        }
        builder.impl { }
    }
}

internal class ProcedureDefinitionBlock internal constructor(val builderBlock: BlockHost.() -> Unit) : NormalBlock("procedures_definition"), HatBlock {
    val blockStack = BlockStack()
    override var topLevel: Boolean = true
    override val stacks = listOf(blockStack)
    init {
        addBlock(this)
    }
    override fun <B : AnyBlock> addBlock(block: B) =
        blockStack.addBlock(block)

    override fun prepareRepresent(sprite: Sprite) {
        blockStack.builderBlock()
        super.prepareRepresent(sprite)
        blockStack.prepareRepresent(sprite)
    }
}

internal fun HatBlockHost.makeProcedureDefinition(prototype: ProcedurePrototype, block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = ProcedureDefinitionBlock(block)
        .withExpression("custom_block", null, prototype)
    return addHatBlock(hatBlock)
}

/**
 * Creates a block stack *without* adding it to the current script.
 * @param block The content of the block stack.
 * @return The created [BlockStack].
 */
fun createBlockStack(block: BlockHost.() -> Unit) = BlockStack().apply(block)

/**
 * Creates a block stack and adds its contents to the current script.
 * @param block The content of the block stack.
 * @return The created [BlockStack].
 */
fun BlockHost.blockStack(block: BlockHost.() -> Unit) = blockStack(createBlockStack(block))

/**
 * Adds an existing block stack's contents to the current script.
 * @param blockStack The block stack to add.
 * @return The given [BlockStack].
 */
fun BlockHost.blockStack(blockStack: BlockStack) = blockStack.contents.forEach(::addBlock)
