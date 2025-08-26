package de.thecommcraft.scratchdsl.oldbuilder

sealed interface MinifiedBlockRepresentation {
    val type: Int
    companion object {

        interface ValueRepresentation : MinifiedBlockRepresentation {
            val value: String
        }

        // NumberRepresentations

        open class NumberRepresentation(
            val number: Double
        ) : ValueRepresentation {
            override val value = number.toString()
            override val type = 4
        }

        class PositiveNumberRepresentation(
            number: Double
        ) : NumberRepresentation(number) {
            override val type = 5
        }

        open class AngleRepresentation(
            val angle: Double
        ) : NumberRepresentation(angle) {
            override val type = 8
        }

        open class IntRepresentation(
            val int: Int
        ) : NumberRepresentation(int.toDouble()) {
            override val type = 7
        }

        class PositiveIntRepresentation(
            int: Int
        ) : IntRepresentation(int) {
            override val type = 6
        }

        // Other ValueRepresentations

        class ColorRepresentation(
            val color: String
        ) : ValueRepresentation {
            override val value = color
            override val type = 9
        }

        class StringRepresentation(
            val string: String
        ) : ValueRepresentation {
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
