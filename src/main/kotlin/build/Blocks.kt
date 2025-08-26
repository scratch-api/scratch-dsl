package de.thecommcraft.scratchdsl.build

abstract class NormalBlockBlockHost<B: NormalBlockBlockHost<B>>() : BlockBlockHost<B> {

}

fun BlockHost.ifBlock() = 1