package net.azisaba.abToSab

import net.azisaba.abToSab.Util.toMojangUniqueId
import net.azisaba.spicyAzisaBan.punishment.PunishmentType
import net.azisaba.abToSab.Util.toUUID
import util.ResourceLocator
import util.UUIDUtil
import util.base.Bytes
import util.yaml.YamlConfiguration
import xyz.acrylicstyle.sql.DataType
import xyz.acrylicstyle.sql.Sequelize
import xyz.acrylicstyle.sql.Table
import xyz.acrylicstyle.sql.TableDefinition
import xyz.acrylicstyle.sql.options.FindOptions
import xyz.acrylicstyle.sql.options.InsertOptions
import java.io.File
import java.util.Properties
import java.util.UUID
import kotlin.system.exitProcess

object ABToSABApp {
    private lateinit var fallbackUUID: UUID

    @JvmStatic
    fun main(args: Array<String>) {
        val file = File("./config.yml")
        if (!file.exists()) {
            val input = ResourceLocator.getInstance(ABToSABApp::class.java).getResourceAsStream("config.yml")!!
            Bytes.copy(input, file)
            println("Copied default config.yml. Please edit the file and run the app again.")
            return
        }
        println("Loading configuration")
        val cfg = YamlConfiguration(file).asObject()
        fallbackUUID = cfg.getString("fallbackUUID", "00000000-0000-0000-0000-000000000000").toUUID() ?: UUIDUtil.NIL
        println("Fallback UUID: $fallbackUUID")
        val fromCfg = cfg.getObject("from") ?: error("missing 'from'")
        val toCfg = cfg.getObject("to") ?: error("missing 'to'")
        println("From: Connecting to ${fromCfg.getString("type")} database")
        val from = when (fromCfg.getString("type")) {
            "hsql" -> Sequelize("jdbc:hsqldb:file:${fromCfg.getString("location")}")
            "mysql" -> Sequelize(fromCfg.getString("host"), fromCfg.getString("name"), fromCfg.getString("user"), fromCfg.getString("password"))
            else -> error("invalid type: ${fromCfg.getString("type")}")
        }
        val props = Properties()
        props.setProperty("verifyServerCertificate", "false")
        props.setProperty("useSSL", "false")
        if (fromCfg.getString("type") == "mysql") {
            from.authenticate(Sequelize.getMariaDBDriver(), props)
        } else {
            from.authenticate(org.hsqldb.jdbc.JDBCDriver())
        }
        println("To: Connecting to mysql database")
        val to = Sequelize(toCfg.getString("host"), toCfg.getString("name"), toCfg.getString("user"), toCfg.getString("password"))
        to.authenticate(Sequelize.getMariaDBDriver(), props)
        val dupe = arrayOf(
            TableDefinition.Builder("id", DataType.INT).build(),
            TableDefinition.Builder("name", DataType.STRING).build(),
            TableDefinition.Builder("uuid", DataType.STRING).build(),
            TableDefinition.Builder("reason", DataType.STRING).build(),
            TableDefinition.Builder("operator", DataType.STRING).build(),
            TableDefinition.Builder("punishmentType", DataType.STRING).build(),
            TableDefinition.Builder("start", DataType.STRING).build(),
            TableDefinition.Builder("end", DataType.STRING).build(),
        )
        // copy from SpicyAzisaBan/net.azisaba.spicyAzisaBan.sql.SQLConnection
        val dupe2 = arrayOf(
            TableDefinition.Builder("id", DataType.BIGINT).setAutoIncrement(true).setPrimaryKey(true).build(), // punish id
            TableDefinition.Builder("name", DataType.STRING).setAllowNull(false).build(), // player name
            TableDefinition.Builder("target", DataType.STRING).setAllowNull(false).build(), // player uuid or IP if ip ban
            TableDefinition.Builder("reason", DataType.STRING).setAllowNull(false).build(), // punish reason
            TableDefinition.Builder("operator", DataType.STRING).setAllowNull(false).build(), // operator uuid
            TableDefinition.Builder("type", DataType.STRING).setAllowNull(false).build(), // type (see PunishmentType)
            TableDefinition.Builder("start", DataType.BIGINT).setAllowNull(false).build(),
            TableDefinition.Builder("end", DataType.BIGINT).setAllowNull(false).build(), // -1 means permanent, otherwise temporary
            TableDefinition.Builder("server", DataType.STRING).setAllowNull(false).build(), // "global", server or group name
            TableDefinition.Builder("extra", DataType.STRING).setDefaultValue("").setAllowNull(false).build(), // Punishment.Flags
        )
        val fPunishmentHistory = from.define("PunishmentHistory", dupe)
        val fPunishments = from.define("Punishments", dupe)
        val tPunishmentHistory = to.define("punishmentHistory", dupe2)
        val tPunishments = to.define("punishments", dupe2)
        val tUnpunish = to.define("unpunish", arrayOf(
            TableDefinition.Builder("id", DataType.BIGINT).setAutoIncrement(true).setPrimaryKey(true).build(), // unpunish id
            TableDefinition.Builder("punish_id", DataType.BIGINT).setAllowNull(false).build(),
            TableDefinition.Builder("reason", DataType.STRING).setAllowNull(false).build(),
            TableDefinition.Builder("timestamp", DataType.BIGINT).setAllowNull(false).build(),
            TableDefinition.Builder("operator", DataType.STRING).setAllowNull(false).build(),
        ))
        println("Copying ${fromCfg.getString("name")}.PunishmentHistory to ${toCfg.getString("name")}.punishmentHistory")
        copyTable(fPunishmentHistory, tPunishmentHistory, cfg.getString("server"))
        println("Copying ${fromCfg.getString("name")}.Punishments to ${toCfg.getString("name")}.punishments")
        copyTable(fPunishments, tPunishments, cfg.getString("server"))
        println("Checking for removed punishments")
        tPunishmentHistory.findAll(FindOptions.ALL)
            .thenDo { list ->
                list.forEach { td ->
                    val id = td.getLong("id")
                    tPunishments.findOne(FindOptions.Builder().addWhere("id", id).build())
                        .thenDo td2@ { td2 ->
                            if (td2 != null) return@td2
                            println("Adding unpunish record for #$id")
                            tUnpunish.insert(
                                InsertOptions.Builder()
                                    .addValue("punish_id", id)
                                    .addValue("reason", "Imported from AdvancedBan")
                                    .addValue("timestamp", System.currentTimeMillis())
                                    .addValue("operator", UUIDUtil.NIL.toString())
                                    .build()
                            ).complete()
                        }.complete()
                }
            }.onCatch { it.printStackTrace() }.complete()
        println("From: Closing connection")
        from.close()
        println("To: Closing connection")
        to.close()
        println("Goodbye I guess")
        exitProcess(0)
    }

    private fun copyTable(from: Table, to: Table, server: String) {
        from.findAll(FindOptions.ALL)
            .thenDo { list ->
                list.forEach { td ->
                    val id = td.getInteger("id")!!
                    println("${from.name} -> ${to.name}: Importing #$id")
                    val name = td.getString("name")
                    val target = try {
                        UUIDUtil.uuidFromStringWithoutDashes(td.getString("uuid")).toString()
                    } catch (e: IllegalArgumentException) {
                        td.getString("uuid")
                    }
                    val reason = td.getString("reason")
                    val operator = td.getString("operator").toMojangUniqueId().complete() ?: fallbackUUID
                    val punishmentType = PunishmentType.valueOf(td.getString("punishmentType"))
                    if (!punishmentType.isIPBased() && target.toUUID() == null) {
                        return@forEach println("#$id: Invalid UUID: $target")
                    }
                    val start = td.getString("start").toLong()
                    val end = if (punishmentType.temp) td.getString("end").toLong() else -1L
                    to.insert(
                        InsertOptions.Builder()
                            .addValue("id", id.toLong())
                            .addValue("name", if (punishmentType.isIPBased()) target else name)
                            .addValue("target", target)
                            .addValue("reason", reason)
                            .addValue("operator", operator.toString())
                            .addValue("type", punishmentType.name)
                            .addValue("start", start)
                            .addValue("end", end)
                            .addValue("server", server)
                            .addValue("extra", "")
                            .build()
                    ).complete()
                }
            }
            .onCatch { it.printStackTrace() }
            .complete()
    }
}