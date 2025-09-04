@file:Suppress("unused")

package de.thecommcraft.scratchdsl

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

    override fun loadInto(representation: Representation) {
        TODO("Not implemented yet")
    }
}

interface HandlesSet : Expression {
    var expressionSetHandler: ((Expression?) -> Block)?
    var expressionChangeHandler: ((Expression?) -> Block)?
}

fun<B: NormalBlock> B.withField(name: String, field: Field) =
    this.apply {
        fields[name] = field
    }

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

fun<B: NormalBlock> B.withMutation(
    name: String,
    value: Representation? = null
) = apply {
    mutation[name] = value
}

fun<B: NormalBlock> B.withDefaultMutation() =
    withMutation("tagName", JsonPrimitive("mutation"))
        .withMutation("children", JsonArray(listOf()))

fun<B: HandlesSet> B.withHandlesSet(block: (Expression?) -> Block) = this.apply {
    expressionSetHandler = block
}

fun<B: HandlesSet> B.withHandlesChange(block: (Expression?) -> Block) = this.apply {
    expressionChangeHandler = block
}

fun Expression.changeShadowOpcode(opcode: String?): Expression {
    if (this is OpcodeSettableShadowExpression) {
        this.opcode = opcode
    }
    return this
}

open class NormalBlockBlockHost internal constructor(opcode: String?, val subStack: BlockStack?) : NormalBlock(opcode), BlockBlockHost {
    val blocks = mutableListOf<AnyBlock>()

    init {
        blockStackInputs["SUBSTACK"] = subStack
    }

    override val stacks = listOfNotNull(subStack)

    override fun<B: AnyBlock> addBlock(block: B) = block.apply(stacks[0]::addBlock)
}

open class ConditionalBlockBlockHost internal constructor(opcode: String?, val expression: Expression?, subStack: BlockStack?) : NormalBlockBlockHost(opcode, subStack) {

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
    private val actualBlock: AnyBlock get() {
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

    override fun loadInto(representation: Representation) {
        TODO("Not yet implemented")
    }

    override fun prepareRepresent(sprite: Sprite) {
        actualBlock.topLevel = true
        blockStack.prepareRepresent(sprite)
    }
}

class HalfIfElse internal constructor(val blockHost: BlockHost, val expression: Expression?, val block: BlockHost.() -> Unit)

sealed interface AnyKeyboardKey {
    val key: String
}

class ChosenKeyboardKey(override val key: String) : AnyKeyboardKey

operator fun Expression.not(): Expression = notBlock(this)

val Double.expr get() = ValueInput.TEXT.of(this.toString())
val Int.expr get() = ValueInput.TEXT.of(this.toString())
val String.expr get() = ValueInput.TEXT.of(this)

class ManuallyMadeBlock(opcode: String?) : NormalBlock(opcode)



fun HatBlockHost.isolated(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = IsolatedBlockStackHat(BlockStack().apply(block))
    return addHatBlock(hatBlock)
}

// Motion

fun BlockHost.moveSteps(steps: Expression?) =
    addBlock(NormalBlock("motion_movesteps")
        .withExpression("STEPS", steps, ValueInput.NUMBER.of("10"))
    )

fun BlockHost.turnRight(degrees: Expression?) =
    addBlock(NormalBlock("motion_turnright")
        .withExpression("DEGREES", degrees, ValueInput.NUMBER.of("15")))

fun BlockHost.turnLeft(degrees: Expression?) =
    addBlock(NormalBlock("motion_turnleft")
        .withExpression("DEGREES", degrees, ValueInput.NUMBER.of("15")))


fun BlockHost.gotoLocation(to: Expression?) =
    addBlock(NormalBlock("motion_goto")
        .withExpression("TO", to?.changeShadowOpcode("motion_goto_menu"), SpecialLocation.random))

fun BlockHost.gotoXY(x: Expression?, y: Expression?) =
    addBlock(NormalBlock("motion_gotoxy")
        .withExpression("X", x, ValueInput.NUMBER.of("0"))
        .withExpression("Y", y, ValueInput.NUMBER.of("0")))

fun BlockHost.glideToLocation(to: Expression?, secs: Expression?) =
    addBlock(NormalBlock("motion_glideto")
        .withExpression("SECS", secs, ValueInput.NUMBER.of("1"))
        .withExpression("TO", to?.changeShadowOpcode("motion_glideto_menu"), null))

fun BlockHost.glideToXY(x: Expression?, y: Expression?, secs: Expression?) =
    addBlock(NormalBlock("motion_glidesecstoxy")
        .withExpression("SECS", secs, ValueInput.NUMBER.of("1"))
        .withExpression("X", x, ValueInput.NUMBER.of("0"))
        .withExpression("Y", y, ValueInput.NUMBER.of("0")))


fun BlockHost.pointInDirection(direction: Expression?) =
    addBlock(NormalBlock("motion_pointindirection")
        .withExpression("DIRECTION", direction, ValueInput.ANGLE.of("90")))

fun BlockHost.pointTowards(towards: Expression?) =
    addBlock(NormalBlock("motion_pointtowards")
        .withExpression("TOWARDS", towards?.changeShadowOpcode("motion_pointtowards_menu"), null))


fun BlockHost.changeXBy(dx: Expression?) =
    addBlock(NormalBlock("motion_changexby")
        .withExpression("DX", dx, ValueInput.NUMBER.of("10")))

fun BlockHost.setXTo(x: Expression?) =
    addBlock(NormalBlock("motion_setx")
        .withExpression("X", x, ValueInput.NUMBER.of("0")))

fun BlockHost.changeYBy(dy: Expression?) =
    addBlock(NormalBlock("motion_changeyby")
        .withExpression("DY", dy, ValueInput.NUMBER.of("10")))

fun BlockHost.setYTo(y: Expression?) =
    addBlock(NormalBlock("motion_sety")
        .withExpression("Y", y, ValueInput.NUMBER.of("0")))


fun BlockHost.ifOnEdgeBounce() =
    addBlock(NormalBlock("motion_ifonedgebounce"))


fun BlockHost.setRotationStyle(rotationStyle: RotationStyle) =
    addBlock(NormalBlock("motion_setrotationstyle")
        .withField("STYLE", Field.of(rotationStyle.value)))


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

val rotation get() =
    HandlesSetNormalExpression("motion_direction")
        .withHandlesSet { direction ->
            NormalBlock("motion_pointindirection")
                .withExpression("DIRECTION", direction, ValueInput.ANGLE.of("90"))
        }
        .withHandlesChange { degrees ->
            NormalBlock("motion_turnright")
                .withExpression("DEGREES", degrees, ValueInput.NUMBER.of("15"))
        }


// Looks

fun BlockHost.switchToCostume(costume: Expression?) =
    addBlock(NormalBlock("looks_switchcostumeto")
        .withExpression("COSTUME", costume, FirstSprite))

fun BlockHost.switchToNextCostume() =
    addBlock(NormalBlock("looks_nextcostume"))

fun BlockHost.switchToBackdrop(backdrop: Expression?) =
    addBlock(NormalBlock("looks_switchbackdropto")
        .withExpression("BACKDROP", backdrop.asBackdrop(), FirstBackdrop))

fun BlockHost.switchToNextBackdrop() =
    addBlock(NormalBlock("looks_nextbackdrop"))


fun BlockHost.changeSizeBy(change: Expression?) =
    addBlock(NormalBlock("looks_changesizeby")
        .withExpression("CHANGE", change, ValueInput.NUMBER.of("10")))

fun BlockHost.setSizeTo(size: Expression?) =
    addBlock(NormalBlock("looks_setsizeto")
        .withExpression("SIZE", size, ValueInput.NUMBER.of("100")))


fun BlockHost.changeLooksEffectBy(effect: LooksEffect, change: Expression?) =
    addBlock(NormalBlock("looks_changeeffectby")
        .withExpression("CHANGE", change, ValueInput.NUMBER.of("25"))
        .withField("EFFECT", Field.of(effect.value)))

fun BlockHost.setLooksEffect(effect: LooksEffect, value: Expression?) =
    addBlock(NormalBlock("looks_seteffectto")
        .withExpression("VALUE", value, ValueInput.NUMBER.of("0"))
        .withField("EFFECT", Field.of(effect.value)))

fun BlockHost.clearGraphicEffects() =
    addBlock(NormalBlock("looks_cleargraphiceffects"))


fun BlockHost.show() =
    addBlock(NormalBlock("looks_show"))

fun BlockHost.hide() =
    addBlock(NormalBlock("looks_hide"))


fun BlockHost.goToLayer(layer: SpecialLayer) =
    addBlock(NormalBlock("looks_gotofrontback")
        .withField("FRONT_BACK", Field.of(layer.value)))

fun BlockHost.changeLayer(layerDirection: LayerDirection, layers: Expression?) =
    addBlock(NormalBlock("looks_goforwardbackwardlayers")
        .withExpression("NUM", layers, ValueInput.INTEGER.of("1"))
        .withField("FORWARD_BACKWARD", Field.of(layerDirection.value)))


val currentCostumeName: CurrentCostume
    get() = CurrentCostume(false)

val currentCostumeNumber: CurrentCostume
    get() = CurrentCostume(true)

val currentCostume: CurrentCostume
    get() = CurrentCostume(false)

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

fun BlockHost.playSoundUntilDone(sound: Expression?) =
    addBlock(NormalBlock("sound_playuntildone")
        .withExpression("SOUND_MENU", sound, FirstSound))

fun BlockHost.playSound(sound: Expression?) =
    addBlock(NormalBlock("sound_play")
        .withExpression("SOUND_MENU", sound, FirstSound))

fun BlockHost.stopAllSounds() =
    addBlock(NormalBlock("sound_stopallsounds"))


fun BlockHost.changeSoundEffectBy(soundEffect: SoundEffect, value: Expression?) =
    addBlock(NormalBlock("sound_changeeffectby")
        .withExpression("VALUE", value, ValueInput.NUMBER.of("10"))
        .withField("EFFECT", Field.of(soundEffect.value)))

fun BlockHost.setSoundEffect(soundEffect: SoundEffect, value: Expression?) =
    addBlock(NormalBlock("sound_seteffectto")
        .withExpression("VALUE", value, ValueInput.NUMBER.of("100"))
        .withField("EFFECT", Field.of(soundEffect.value)))

fun BlockHost.clearSoundEffects() =
    addBlock(NormalBlock("sound_cleareffects"))


fun BlockHost.changeVolumeBy(volume: Expression?) =
    addBlock(NormalBlock("sound_changevolumeby")
        .withExpression("VOLUME", volume, ValueInput.NUMBER.of("-10")))

fun BlockHost.setVolume(volume: Expression?) =
    addBlock(NormalBlock("sound_setvolumeto")
        .withExpression("VOLUME", volume, ValueInput.NUMBER.of("100")))

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

fun HatBlockHost.whenGreenFlagClicked(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = NormalHatBlock("event_whenflagclicked")
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

fun HatBlockHost.whenKeyPressed(key: AnyKeyboardKey, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenkeypressed")
        .withField("KEY_OPTION", Field.of(key.key))
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

fun HatBlockHost.whenClicked(block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenthisspriteclicked")
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

fun HatBlockHost.whenBackdropSwitchesTo(backdrop: Costume, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenbackdropswitchesto")
        .withField("BACKDROP", backdrop)
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}


fun HatBlockHost.whenGreaterThan(compared: WhenGreaterThanComparedValue, value: Expression?, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenbackdropswitchesto")
        .withExpression("VALUE", value, ValueInput.NUMBER.of("10"))
        .withField("WHENGREATERTHANMENU", Field.of(compared.value))
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}


fun HatBlockHost.whenIReceive(broadcast: Broadcast, block: BlockHost.() -> Unit): NormalHatBlock {
    val hatBlock = NormalHatBlock("event_whenbackdropswitchesto")
        .withField("BROADCAST_OPTION", broadcast)
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

fun BlockHost.broadcast(broadcast: Broadcast) =
    addBlock(NormalBlock("event_broadcast")
        .withExpression("BROADCAST_INPUT", broadcast, FirstBroadcast))

fun BlockHost.broadcastAndWait(broadcast: Broadcast) =
    addBlock(NormalBlock("event_broadcastandwait")
        .withExpression("BROADCAST_INPUT", broadcast, FirstBroadcast))

// Control

fun BlockHost.waitDuration(duration: Expression?) =
    addBlock(NormalBlock("control_wait")
        .withExpression("DURATION", duration, ValueInput.POSITIVE_NUMBER.of("1")))

fun BlockHost.repeatBlock(expression: Expression?, block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(NormalBlockBlockHost("control_repeat", stack)
        .withExpression("TIMES", expression, ValueInput.POSITIVE_INTEGER.of("10")))
}

fun BlockHost.foreverBlock(block: BlockHost.() -> Unit): NormalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(NormalBlockBlockHost("control_forever", stack))
}


fun BlockHost.ifBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_if", expression, stack))
}

fun BlockHost.ifElseBlock(expression: Expression?, block: BlockHost.() -> Unit) =
    HalfIfElse(this, expression, block)

infix fun HalfIfElse.elseBlock(block: BlockHost.() -> Unit): IfElseBlock {
    val stack = BlockStack().apply(this.block)
    val secStack = BlockStack().apply(block)
    return blockHost.addBlock(IfElseBlock(expression, stack, secStack))
}

fun BlockHost.waitUntilBlock(expression: Expression?): NormalBlock {
    return addBlock(
        NormalBlock("control_wait_until")
            .withExpression("CONDITION", expression)
    )
}

fun BlockHost.repeatUntilBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_repeat_until", expression, stack))
}

fun BlockHost.whileBlock(expression: Expression?, block: BlockHost.() -> Unit): ConditionalBlockBlockHost {
    val stack = BlockStack().apply(block)
    return addBlock(ConditionalBlockBlockHost("control_while", expression, stack))
}


fun BlockHost.stopBlock(stopType: StopType = StopType.THIS_SCRIPT): NormalBlock {
    val hasNext = stopType == StopType.OTHER_SCRIPTS_IN_SPRITE
    return addBlock(NormalBlock("control_stop")
        .withField("STOP_OPTION", Field.of(stopType.code))
        .withDefaultMutation()
        .withMutation("hasnext", JsonPrimitive(hasNext)))
}


fun HatBlockHost.whenIStartAsClone(block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = NormalHatBlock("control_start_as_clone")
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}

fun BlockHost.createCloneOf(cloneOption: Expression?) =
    addBlock(NormalBlock("control_create_clone_of")
        .withExpression("CLONE_OPTION", cloneOption, CloneTarget.myself))

fun BlockHost.createCloneOf(sprite: Sprite) =
    createCloneOf(sprite.cloneTarget)

fun BlockHost.deleteThisClone() =
    addBlock(NormalBlock("control_delete_this_clone"))

// Sensing

fun BlockHost.touching(expression: Expression?) =
    NormalExpression("sensing_touchingobject")
        .withExpression("TOUCHINGOBJECTMENU", expression, TouchObject.mouse)

fun BlockHost.touching(sprite: Sprite) =
    touching(sprite.touchObject)

fun BlockHost.touchingColor(color: Expression?) =
    NormalExpression("sensing_touchingcolor")
        .withExpression("COLOR", color, ValueInput.COLOUR_PICKER.of("#6deaa0"))

infix fun Expression?.colorTouchingColor(other: Expression?) =
    NormalExpression("sensing_coloristouchingcolor")
        .withExpression("COLOR", this, ValueInput.COLOUR_PICKER.of("#ba2a32"))
        .withExpression("COLOR2", other, ValueInput.COLOUR_PICKER.of("#c385eb"))

fun BlockHost.distanceTo(expression: Expression?) =
    NormalExpression("sensing_distanceto")
        .withExpression("DISTANCETOMENU", expression, DistanceObject.mouse)

fun BlockHost.distanceTo(sprite: Sprite) =
    distanceTo(sprite.distanceObject)


fun BlockHost.askAndWait(question: Expression?) =
    addBlock(NormalBlock("sensing_askandwait")
        .withExpression("QUESTION", question, ValueInput.TEXT.of("What's your name?")))

val BlockHost.answer get() =
    NormalExpression("sensing_answer")


fun BlockHost.keyPressed(key: Expression?) =
    NormalExpression("sensing_keypressed")
        .withExpression("KEY_OPTION", key, KeyboardKey.SPACE.sensingKey)

fun BlockHost.keyPressed(key: KeyboardKey) =
    keyPressed(key.sensingKey)

val BlockHost.mouseDown get() =
    NormalExpression("sensing_mousedown")

val BlockHost.mouseX get() =
    NormalExpression("sensing_mousex")

val BlockHost.mouseY get() =
    NormalExpression("sensing_mousey")


fun BlockHost.setDragMode(dragMode: DragMode) =
    addBlock(NormalBlock("sensing_setdragmode")
        .withField("DRAG_MODE", Field.of(dragMode.value)))


val BlockHost.loudness get() =
    NormalExpression("sensing_loudness")


val BlockHost.timer get() =
    NormalExpression("sensing_timer")

fun BlockHost.resetTimer() =
    addBlock(NormalBlock("sensing_resettimer"))


fun BlockHost.propertyOf(sprite: Expression?, property: Property) =
    NormalExpression("sensing_of")
        .withExpression("OBJECT", sprite, PropertyTarget.stage)
        .withField("PROPERTY", property)

fun BlockHost.propertyOf(sprite: Sprite, property: Property) =
    propertyOf(sprite.propertyTarget, property)

fun BlockHost.propertyOf(sprite: Expression?, property: String) =
    propertyOf(sprite, Property.of(property))

fun BlockHost.propertyOf(sprite: Sprite, property: String) =
    propertyOf(sprite.propertyTarget, Property.of(property))

fun BlockHost.propertyOf(sprite: Expression?, property: Variable) =
    propertyOf(sprite, property.property)

fun BlockHost.propertyOf(sprite: Sprite, property: Variable) =
    propertyOf(sprite.propertyTarget, property.property)


fun BlockHost.current(timeUnit: TimeUnit) =
    NormalExpression("sensing_current")
        .withField("CURRENTMENU", Field.of(timeUnit.value))

val BlockHost.daysSince2000 get() =
    NormalExpression("sensing_dayssince2000")

val BlockHost.username get() =
    NormalExpression("sensing_username")


// Operators


fun round(expression: Expression?) =
    NormalUnaryOp("operator_round", expression, "NUM", ValueInput.NUMBER.of(""))

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

infix fun Expression?.and(other: Expression?) =
    NormalBinaryOp(
        "operator_and",
        this,
        other,
        "OPERAND1",
        "OPERAND2"
    )

infix fun Expression?.or(other: Expression?) =
    NormalBinaryOp(
        "operator_or",
        this,
        other,
        "OPERAND1",
        "OPERAND2"
    )

fun notBlock(expression: Expression?) =
    NormalUnaryOp("operator_not", expression)

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

val Expression?.stringLength get() =
    NormalExpression("operator_length")
        .withExpression("STRING", this, ValueInput.TEXT.of("apple"))

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

fun BlockHost.setVar(variable: Variable, expression: Expression?) =
    addBlock(NormalBlock("data_setvariableto")
        .withExpression("VALUE", expression, ValueInput.TEXT.of("0"))
        .withField("VARIABLE", variable))

fun BlockHost.changeVar(variable: Variable, expression: Expression?) =
    addBlock(NormalBlock("data_changevariableby")
        .withExpression("VALUE", expression, ValueInput.NUMBER.of("1"))
        .withField("VARIABLE", variable))

fun BlockHost.showVar(variable: Variable) =
    addBlock(NormalBlock("data_showvariable")
        .withField("VARIABLE", variable))

fun BlockHost.hideVar(variable: Variable) =
    addBlock(NormalBlock("data_hidevariable")
        .withField("VARIABLE", variable))



fun BlockHost.append(list: ScratchList, value: Expression?) =
    addBlock(NormalBlock("data_addtolist")
        .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
        .withField("LIST", list))


fun BlockHost.deleteAtIndex(list: ScratchList, index: Expression?) =
    addBlock(NormalBlock("data_deleteoflist")
        .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
        .withField("LIST", list))

fun BlockHost.deleteAll(list: ScratchList) =
    addBlock(NormalBlock("data_deletealloflist")
        .withField("LIST", list))

fun BlockHost.insertAtIndex(list: ScratchList, value: Expression?, index: Expression?) =
    addBlock(NormalBlock("data_insertatlist")
        .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
        .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
        .withField("LIST", list))

fun BlockHost.replaceAtIndex(list: ScratchList, value: Expression?, index: Expression?) =
    addBlock(NormalBlock("data_replaceitemoflist")
        .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
        .withExpression("ITEM", value, ValueInput.TEXT.of("thing"))
        .withField("LIST", list))


operator fun ScratchList.get(index: Expression?) =
    HandlesSetNormalUnaryOp("data_itemoflist", index, "INDEX", ValueInput.INTEGER.of("1"))
        .withField("LIST", this)
        .withHandlesSet { expression ->
            NormalBlock("data_replaceitemoflist")
                .withExpression("INDEX", index, ValueInput.INTEGER.of("1"))
                .withExpression("ITEM", expression, ValueInput.TEXT.of("thing"))
                .withField("LIST", this)
        }

fun ScratchList.indexOf(value: Expression?) =
    NormalUnaryOp("data_itemnumoflist", value, "ITEM", ValueInput.TEXT.of("thing"))
        .withField("LIST", this)

val ScratchList.listLength: Expression get() =
    NormalExpression("data_lengthoflist")
        .withField("LIST", this)

infix fun ScratchList.containsItem(value: Expression?) =
    NormalUnaryOp("data_listcontainsitem", value, "ITEM", ValueInput.TEXT.of("thing"))
        .withField("LIST", this)


fun BlockHost.showList(list: ScratchList) =
    addBlock(NormalBlock("data_showlist")
        .withField("LIST", list))

fun BlockHost.hideList(list: ScratchList) =
    addBlock(NormalBlock("data_hidelist")
        .withField("LIST", list))

// My Blocks

class ProcedurePrototypeBuilder(name: String) {
    internal val arguments = mutableListOf<ProcedureArgument>()
    internal var proccode = name
    fun stringNumber(name: String, default: String = ""): ProcedureArgumentStringNumber {
        proccode += " %s"
        val argument = ProcedureArgumentStringNumber(name, default)
        arguments.add(argument)
        return argument
    }
    fun boolean(name: String, default: String = "false"): ProcedureArgumentBoolean {
        proccode += " %b"
        val argument = ProcedureArgumentBoolean(name, default)
        arguments.add(argument)
        return argument
    }
    fun text(text: String) {
        proccode += " "
        proccode += text
    }
}

fun procedure(name: String, warp: Boolean = false, block: ProcedurePrototypeBuilder.() -> Unit): ProcedurePrototype {
    val builder = ProcedurePrototypeBuilder(name).apply(block)
    return ProcedurePrototype(builder.proccode, warp, builder.arguments)
}

internal fun HatBlockHost.makeProcedureDefinition(prototype: ProcedurePrototype, block: BlockHost.() -> Unit): HatBlock {
    val hatBlock = NormalHatBlock("procedures_definition")
        .withExpression("custom_block", prototype)
    hatBlock.blockStack.block()
    return addHatBlock(hatBlock)
}
