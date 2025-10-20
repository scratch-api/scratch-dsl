# A Kotlin DSL for generating Scratch code

## Installation

Gradle:
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.scratchapi:scratchdsl:0.0.1a5")
}
```

## Usage

Code example:

```kotlin
import okio.Path.Companion.toPath
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import org.scratchapi.scratchdsl.*

fun main() =
    build {
        stage {
            val backdrop1 = addBackdrop("path/to/image".toPath(), "name_of_backdrop")
            whenGreenFlagClicked {
                switchToBackdrop(backdrop1)
            }
        }
        sprite {
            name = "sprite_name"
            val costume1 = addCostume("path/to/image".toPath(), "name_of_costume")
            val variable = makeVar(
                "variable_name", // Optional, defaults to a random string
                JsonPrimitive("default_value"), // Optional, defaults to an empty string
                cloud = false // Optional, defaults to false
            )
            val list = makeList(
                "list_name" // Optional, defaults to a random string
            ) {
                add("value1")
                add("value2")
                add("value3")
                // ...
            }
            whenGreenFlagClicked {
                list.deleteAll()
                list.append("value".expr) // .expr is needed for literal values in blocks.
                variable set "value".expr
                switchToCostume(costume1)
            }
        }
    }
        .toProjectJsonContents() // Encode to json
        .output() // Print the json output
```

## API Reference

You can find the API Reference of Scratch DSL [here](https://scratchdsl.scratchapi.org/).

### More coming soon!
