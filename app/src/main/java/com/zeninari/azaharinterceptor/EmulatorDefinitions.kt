package com.zeninari.azaharinterceptor

enum class EmulatorRouteType {
    URI,
    PATH
}

data class EmulatorCommand(
    val description: String,
    val command: String
)

data class EmulatorDefinition(
    val id: String,
    val name: String,
    val routeType: EmulatorRouteType,
    val packages: List<String>,
    val commands: List<EmulatorCommand>
)

object EmulatorDefinitions {

    val all = listOf(

        EmulatorDefinition(
            id = "AZAHAR",
            name = "Azahar (Standalone)",
            routeType = EmulatorRouteType.URI,
            packages = listOf(
                "org.azahar_emu.azahar",
                "io.github.lime3ds.android"
            ),
            commands = listOf(
                EmulatorCommand(
                    description = "Azahar",
                    command = "org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity --activity-clear-task --activity-clear-top -a android.intent.action.VIEW -d %ROM_URI%"
                ),
                EmulatorCommand(
                    description = "Azahar Play Store",
                    command = "io.github.lime3ds.android/org.citra.citra_emu.activities.EmulationActivity --activity-clear-task --activity-clear-top -a android.intent.action.VIEW -d %ROM_URI%"
                ),
                EmulatorCommand(
                    description = "Azahar Debug",
                    command = "org.azahar_emu.azahar.debug/org.citra.citra_emu.activities.EmulationActivity --activity-clear-task --activity-clear-top -a android.intent.action.VIEW -d %ROM_URI%"
                )
            )
        ),

        EmulatorDefinition(
            id = "AZAHARPLUS",
            name = "AzaharPlus (Standalone)",
            routeType = EmulatorRouteType.URI,
            packages = listOf(
                "io.github.azaharplus.android"
            ),
            commands = listOf(
                EmulatorCommand(
                    description = "AzaharPlus",
                    command = "io.github.azaharplus.android/org.citra.citra_emu.activities.EmulationActivity --activity-clear-task --activity-clear-top -a android.intent.action.VIEW -d %ROM_URI%"
                )
            )
        )
    )
}