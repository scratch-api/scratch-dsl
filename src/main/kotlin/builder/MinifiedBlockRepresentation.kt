package de.thecommcraft.scratchdsl.builder

sealed interface MinifiedBlockRepresentation {
    val type: Int
    companion object {

        interface ValueRepresentation<V> : MinifiedBlockRepresentation {
            val value: V
        }

        // NumberRepresentations

        open class NumberRepresentation(
            override val value: Double
        ) : ValueRepresentation<Double> {
            override val type = 4
        }

        class PositiveNumberRepresentation(
            value: Double
        ) : NumberRepresentation(value) {
            override val type = 5
        }

        open class AngleRepresentation(
            val angle: Double
        ) : NumberRepresentation(angle) {
            override val type = 8
        }

        open class IntRepresentation(
            override val value: Int
        ) : ValueRepresentation<Int> {
            override val type = 7
        }

        class PositiveIntRepresentation(
            value: Int
        ) : IntRepresentation(value) {
            override val type = 6
        }

        // Other ValueRepresentations

        class ColorRepresentation(
            val color: String
        ) : ValueRepresentation<String> {
            override val value = color
            override val type = 9
        }

        class StringRepresentation(
            val string: String
        ) : ValueRepresentation<String> {
            override val value = string
            override val type = 10
        }

        // NameIdRepresentations

        interface NameIdRepresentation : MinifiedBlockRepresentation {
            val name: String
            val id: String
        }

//        class BroadcastRepresentation(
//
//        )

        class VariableRepresentation(
            variable: Variable
        ) : NameIdRepresentation {
            override val type = 12
            override val name = variable.name
            override val id = variable.variableId
        }

        class ListRepresentation(
            list: ScratchList
        ) : NameIdRepresentation {
            override val type = 13
            override val name = list.name
            override val id = list.listId
        }
    }
}
