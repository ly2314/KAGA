/*
 * GPLv3 License
 *
 *  Copyright (c) KAGA by waicool20
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.waicool20.kaga.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.waicool20.kaga.Kaga
import com.waicool20.kaga.util.IniConfig
import com.waicool20.kaga.util.fromObject
import com.waicool20.kaga.util.toObject
import javafx.beans.property.*
import javafx.collections.FXCollections
import org.ini4j.Wini
import org.slf4j.LoggerFactory
import tornadofx.*
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.regex.Pattern


class KancolleAutoProfile(
        name: String, val general: General, val scheduledSleep: ScheduledSleep,
        val scheduledStop: ScheduledStop, val expeditions: Expeditions,
        val pvp: Pvp, val sortie: Sortie, val submarineSwitch: SubmarineSwitch,
        val lbas: Lbas, val quests: Quests
) {
    private val logger = LoggerFactory.getLogger(KancolleAutoProfile::class.java)
    @JsonIgnore var nameProperty = SimpleStringProperty(name)
    @get:JsonProperty var name by nameProperty

    fun path(): Path = Kaga.CONFIG_DIR.resolve("$name-config.ini")

    fun save(path: Path = path()) {
        logger.info("Saving KancolleAuto profile")
        logger.debug("Saving to $path")
        if (Files.notExists(path)) {
            logger.debug("Profile not found, creating file $path")
            Files.createDirectories(path.parent)
            Files.createFile(path)
        }
        val string = asIniString().replace("true", "True").replace("false", "False")
        Files.write(path, string.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
        logger.info("Saving KancolleAuto profile was successful")
        logger.debug("Saved $this to $path")
    }

    fun asIniString(): String {
        val writer = StringWriter()
        getIni().store(writer)
        return writer.toString()
    }

    private fun getIni(): Wini {
        with(Wini()) {
            add("General").fromObject(general)
            add("ScheduledSleep").fromObject(scheduledSleep)
            add("ScheduledStop").fromObject(scheduledStop)
            add("Expeditions").fromObject(expeditions)
            add("PvP").fromObject(pvp)
            add("Combat").fromObject(sortie)
            add("SubmarineSwitch").fromObject(submarineSwitch)
            add("LBAS").fromObject(lbas)
            quests.quests.setAll(quests.quests.map(String::toLowerCase))
            add("Quests").fromObject(quests)
            return this
        }
    }

    fun delete(): Boolean {
        with(path()) {
            if (Files.exists(this)) {
                Files.delete(this)
                logger.info("Deleted profile")
                logger.debug("Deleted ${this@KancolleAutoProfile} from $this")
                return true
            } else {
                logger.warn("File doesn't exist, can't delete!")
                logger.debug("Couldn't delete $this")
                return false
            }
        }
    }

    companion object Loader {
        private val loaderLogger = LoggerFactory.getLogger(KancolleAutoProfile.Loader::class.java)
        val DEFAULT_NAME = "[Current Profile]"

        @JvmStatic fun load(path: Path = Kaga.CONFIG.kancolleAutoRootDirPath.resolve("config.ini")): KancolleAutoProfile? {
            if (Files.exists(path)) {
                loaderLogger.info("Attempting to load KancolleAuto Profile")
                loaderLogger.debug("Loading KancolleAuto Profile from $path")
                val matcher = Pattern.compile("(.+?)-config\\.ini").matcher(path.fileName.toString())
                val name = if (matcher.matches()) {
                    matcher.group(1)
                } else {
                    var backupPath = path.resolveSibling("config.ini.bak")
                    var index = 0
                    while (Files.exists(backupPath)) {
                        backupPath = path.resolveSibling("config.ini.bak${index++}")
                    }
                    loaderLogger.info("Copied backup of existing configuration to $backupPath")
                    Files.copy(path, backupPath)
                    DEFAULT_NAME
                }
                val ini = Wini(path.toFile())

                val general = ini["General"]?.toObject(General::class.java)
                val scheduledSleep = ini["ScheduledSleep"]?.toObject(ScheduledSleep::class.java)
                val scheduledStop = ini["ScheduledStop"]?.toObject(ScheduledStop::class.java)
                val expeditions = ini["Expeditions"]?.toObject(Expeditions::class.java)
                val pvp = ini["PvP"]?.toObject(Pvp::class.java)
                val sortie = ini["Combat"]?.toObject(Sortie::class.java)
                val submarineSwitch = ini["SubmarineSwitch"]?.toObject(SubmarineSwitch::class.java)
                val lbas = ini["LBAS"]?.toObject(Lbas::class.java)
                val quests = ini["Quests"]?.toObject(Quests::class.java)

                if (general == null || scheduledSleep == null || scheduledStop == null ||
                        expeditions == null || pvp == null || sortie == null ||
                        submarineSwitch == null || lbas == null || quests == null) {
                    return null
                }
                with(KancolleAutoProfile(name, general, scheduledSleep, scheduledStop,
                        expeditions, pvp, sortie, submarineSwitch, lbas, quests)) {
                    loaderLogger.info("Loading KancolleAuto profile was successful")
                    loaderLogger.debug("Loaded $this")
                    return this
                }
            }
            loaderLogger.warn("File does not exist!")
            loaderLogger.debug("Could not load $path")
            return null
        }
    }

    enum class RecoveryMethod {BROWSER, KC3, KCV, KCT, EO, NONE }

    enum class ScheduledStopMode {TIME, EXPEDITION, SORTIE, PVP }

    enum class CombatFormation(val prettyString: String) {
        LINE_AHEAD("Line Ahead"), DOUBLE_LINE("Double Line"), DIAMOND("Diamond"),
        ECHELON("Echelon"), LINE_ABREAST("Line Abreast"), COMBINEDFLEET_1("Cruising Formation 1 (Anti-Sub)"),
        COMBINEDFLEET_2("Cruising Formation 2 (Forward)"), COMBINEDFLEET_3("Cruising Formation 3 (Ring)"), COMBINEDFLEET_4("Cruising Formation 4 (Battle)");

        companion object {
            fun fromPrettyString(string: String) = CombatFormation.values().first { it.prettyString.equals(string, true) }
        }

        override fun toString(): String = name.toLowerCase()
    }

    enum class Submarines(val prettyString: String, val isSSV: Boolean?) {
        ALL("All", null), SS("SS", null), SSV("SSV", null), I_8("I-8", false),
        I_8_KAI("I-8 Kai", true), I_13("I-13", true), I_14("I-14", true),
        I_19("I-19", false), I_19_KAI("I-19 Kai", true), I_26("I-26", false),
        I_26_KAI("I-26 Kai", true), I_58("I-58", false), I_58_KAI("I-58 Kai", true),
        I_168("I-168", false), I_401("I-401", true), MARUYU("Maruyu", false),
        RO_500("Ro-500", false), U_511("U-511", false);

        override fun toString() = prettyString.toLowerCase().replace(" ", "-")
    }

    class General(
            program: String, recoveryMethod: RecoveryMethod, basicRecovery: Boolean,
            sleepCycle: Int, paranoia: Int, sleepModifier: Int
    ) {
        @JsonIgnore @IniConfig(key = "Program") val programProperty = SimpleStringProperty(program)
        @JsonIgnore @IniConfig(key = "RecoveryMethod") val recoveryMethodProperty = SimpleObjectProperty<RecoveryMethod>(recoveryMethod)
        @JsonIgnore @IniConfig(key = "BasicRecovery") val basicRecoveryProperty: BooleanProperty = SimpleBooleanProperty(basicRecovery)
        val offset = (TimeZone.getDefault().rawOffset - TimeZone.getTimeZone("Japan")
                .rawOffset) / 3600000
        @JsonIgnore @IniConfig(key = "JSTOffset", read = false) val jstOffsetProperty: IntegerProperty = SimpleIntegerProperty(offset)
        @JsonIgnore @IniConfig(key = "SleepCycle") val sleepCycleProperty: IntegerProperty = SimpleIntegerProperty(sleepCycle)
        @JsonIgnore @IniConfig(key = "Paranoia") val paranoiaProperty: IntegerProperty = SimpleIntegerProperty(paranoia)
        @JsonIgnore @IniConfig(key = "SleepModifier") val sleepModifierProperty: IntegerProperty = SimpleIntegerProperty(sleepModifier)

        @get:JsonProperty var program by programProperty
        @get:JsonProperty var recoveryMethod by recoveryMethodProperty
        @get:JsonProperty var basicRecovery by basicRecoveryProperty
        @get:JsonProperty var jstOffset by jstOffsetProperty
        @get:JsonProperty var sleepCycle by sleepCycleProperty
        @get:JsonProperty var paranoia by paranoiaProperty
        @get:JsonProperty var sleepModifier by sleepModifierProperty
    }

    class ScheduledSleep(enabled: Boolean, startTime: String, length: Double) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "StartTime") val startTimeProperty = SimpleStringProperty(startTime)
        @JsonIgnore @IniConfig(key = "SleepLength") val lengthProperty = SimpleDoubleProperty(length)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var startTime by startTimeProperty
        @get:JsonProperty var length by lengthProperty
    }

    class ScheduledStop(enabled: Boolean, mode: ScheduledStopMode, count: Int) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "Mode") val modeProperty = SimpleObjectProperty(mode)
        @JsonIgnore @IniConfig(key = "Count") val countProperty = SimpleIntegerProperty(count)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var mode by modeProperty
        @get:JsonProperty var count by countProperty
    }

    class Expeditions(enabled: Boolean, fleet2: String, fleet3: String, fleet4: String) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "Fleet2") val fleet2Property = SimpleStringProperty(fleet2)
        @JsonIgnore @IniConfig(key = "Fleet3") val fleet3Property = SimpleStringProperty(fleet3)
        @JsonIgnore @IniConfig(key = "Fleet4") val fleet4Property = SimpleStringProperty(fleet4)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var fleet2 by fleet2Property
        @get:JsonProperty var fleet3 by fleet3Property
        @get:JsonProperty var fleet4 by fleet4Property
    }

    class Pvp(enabled: Boolean, fleetComp: Int) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "FleetComp") val fleetCompProperty = SimpleIntegerProperty(fleetComp)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var fleetComp by fleetCompProperty
    }

    class Sortie(
            enabled: Boolean, fleetComps: List<Int>, area: String, subarea: String, combinedFleet: Boolean,
            nodes: Int, nodeSelects: List<String>, formations: List<CombatFormation>,
            nightBattles: List<Boolean>, retreatLimit: Int, repairLimit: Int, repairTimeLimit: String,
            checkFatigue: Boolean, portCheck: Boolean, medalStop: Boolean, lastNodePush: Boolean
    ) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "FleetComps") val fleetCompsProperty = SimpleListProperty(FXCollections.observableArrayList(fleetComps))
        @JsonIgnore @IniConfig(key = "Area") val areaProperty = SimpleStringProperty(area)
        @JsonIgnore @IniConfig(key = "Subarea") val subareaProperty = SimpleStringProperty(subarea)
        @JsonIgnore @IniConfig(key = "CombinedFleet") val combinedFleetProperty = SimpleBooleanProperty(combinedFleet)
        @JsonIgnore @IniConfig(key = "Nodes") val nodesProperty = SimpleIntegerProperty(nodes)
        @JsonIgnore @IniConfig(key = "NodeSelects") val nodeSelectsProperty = SimpleListProperty(FXCollections.observableArrayList(nodeSelects))
        @JsonIgnore @IniConfig(key = "Formations") val formationsProperty = SimpleListProperty(FXCollections.observableArrayList(formations))
        @JsonIgnore @IniConfig(key = "NightBattles") val nightBattlesProperty = SimpleListProperty(FXCollections.observableArrayList(nightBattles))
        @JsonIgnore @IniConfig(key = "RetreatLimit") val retreatLimitProperty = SimpleIntegerProperty(retreatLimit)
        @JsonIgnore @IniConfig(key = "RepairLimit") val repairLimitProperty = SimpleIntegerProperty(repairLimit)
        @JsonIgnore @IniConfig(key = "RepairTimeLimit") val repairTimeLimitProperty = SimpleStringProperty(repairTimeLimit)
        @JsonIgnore @IniConfig(key = "CheckFatigue") val checkFatigueProperty = SimpleBooleanProperty(checkFatigue)
        @JsonIgnore @IniConfig(key = "PortCheck") val portCheckProperty = SimpleBooleanProperty(portCheck)
        @JsonIgnore @IniConfig(key = "MedalStop") val medalStopProperty = SimpleBooleanProperty(medalStop)
        @JsonIgnore @IniConfig(key = "LastNodePush") val lastNodePushProperty = SimpleBooleanProperty(lastNodePush)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var fleetComps by fleetCompsProperty
        @get:JsonProperty var area by areaProperty
        @get:JsonProperty var subarea by subareaProperty
        @get:JsonProperty var combinedFleet by combinedFleetProperty
        @get:JsonProperty var nodes by nodesProperty
        @get:JsonProperty var nodeSelects by nodeSelectsProperty
        @get:JsonProperty var formations by formationsProperty
        @get:JsonProperty var nightBattles by nightBattlesProperty
        @get:JsonProperty var retreatLimit by retreatLimitProperty
        @get:JsonProperty var repairLimit by repairLimitProperty
        @get:JsonProperty var repairTimeLimit by repairTimeLimitProperty
        @get:JsonProperty var checkFatigue by checkFatigueProperty
        @get:JsonProperty var portCheck by portCheckProperty
        @get:JsonProperty var medalStop by medalStopProperty
        @get:JsonProperty var lastNodePush by lastNodePushProperty
    }

    class SubmarineSwitch(enabled: Boolean, enabledSubs: List<Submarines>, replaceLimit: Int, fatigueSwitch: Boolean) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "EnabledSubs") val enabledSubsProperty = SimpleListProperty(FXCollections.observableArrayList(enabledSubs))
        @JsonIgnore @IniConfig(key = "ReplaceLimit") val replaceLimitProperty = SimpleIntegerProperty(replaceLimit)
        @JsonIgnore @IniConfig(key = "FatigueSwitch") val fatigueSwitchProperty = SimpleBooleanProperty(fatigueSwitch)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var enabledSubs by enabledSubsProperty
        @get:JsonProperty var replaceLimit by replaceLimitProperty
        @get:JsonProperty var fatigueSwitch by fatigueSwitchProperty
    }

    class Lbas(
            enabled: Boolean, enabledGroups: Set<Int>, group1Nodes: List<String>,
            group2Nodes: List<String>, group3Nodes: List<String>
    ) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "EnabledGroups") val enabledGroupsProperty = SimpleSetProperty(FXCollections.observableSet(enabledGroups))
        @JsonIgnore @IniConfig(key = "Group1Nodes") val group1NodesProperty = SimpleListProperty(FXCollections.observableArrayList(group1Nodes))
        @JsonIgnore @IniConfig(key = "Group2Nodes") val group2NodesProperty = SimpleListProperty(FXCollections.observableArrayList(group2Nodes))
        @JsonIgnore @IniConfig(key = "Group3Nodes") val group3NodesProperty = SimpleListProperty(FXCollections.observableArrayList(group3Nodes))

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var enabledGroups by enabledGroupsProperty
        @get:JsonProperty var group1Nodes by group1NodesProperty
        @get:JsonProperty var group2Nodes by group2NodesProperty
        @get:JsonProperty var group3Nodes by group3NodesProperty
    }

    class Quests(enabled: Boolean, quests: List<String>, checkSchedule: Int) {
        @JsonIgnore @IniConfig(key = "Enabled") val enabledProperty = SimpleBooleanProperty(enabled)
        @JsonIgnore @IniConfig(key = "Quests") val questsProperty = SimpleListProperty(FXCollections.observableArrayList(quests))
        @JsonIgnore @IniConfig(key = "CheckSchedule") val checkScheduleProperty = SimpleIntegerProperty(checkSchedule)

        @get:JsonProperty var enabled by enabledProperty
        @get:JsonProperty var quests by questsProperty
        @get:JsonProperty var checkSchedule by checkScheduleProperty
    }

    override fun toString(): String = ObjectMapper().writeValueAsString(this)
}
