package de.thecommcraft.scratchdsl.builder

interface Field {
    fun representAsList(): List<String> {
        val pair = representAsPair()
        pair.second?.let {
            return listOf(pair.first, it)
        }
        return listOf(pair.first)
    }
    fun representAsPair(): Pair<String, String?>
}

sealed interface SingleValueFieldValue {
    val value: String
}

open class SingleValueField<T : SingleValueFieldValue>(val value: T): Field {
    override fun representAsPair(): Pair<String, String?> {
        return value.value to null
    }
}

class VariableField(
    val variable: Variable
) : Field {
    override fun representAsPair(): Pair<String, String> {
        return variable.name to variable.variableId
    }
}

class ListField(
    val list: ScratchList
) : Field {
    override fun representAsPair(): Pair<String, String> {
        return list.name to list.listId
    }
}
