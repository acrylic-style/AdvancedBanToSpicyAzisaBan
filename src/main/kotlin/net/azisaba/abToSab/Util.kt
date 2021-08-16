package net.azisaba.abToSab

import util.UUIDUtil
import util.promise.rewrite.Promise
import xyz.acrylicstyle.mcutil.mojang.MojangAPI
import java.sql.Connection
import java.util.UUID

object Util {
    private val insertLock = Object()

    fun Connection.insert(fn: () -> Unit): Long {
        synchronized(insertLock) {
            fn()
            val statement = createStatement()
            val sql = "SELECT LAST_INSERT_ID()"
            val result = statement.executeQuery(sql)
            if (!result.next()) return -1L
            val r = result.getObject(1) as Number
            statement.close()
            return r.toLong()
        }
    }

    fun String.toMojangUniqueId(): Promise<UUID> {
        if (this == "CONSOLE") return Promise.resolve(UUIDUtil.NIL)
        return MojangAPI.getUniqueId(this)
            .onCatch {}
            .then { it ?: UUIDUtil.NIL }
    }

    fun String.isValidIPAddress(): Boolean {
        val numbers = this.split(".").mapNotNull {
            try {
                Integer.parseInt(it, 10)
            } catch (e: NumberFormatException) { null }
        }
        if (numbers.size != 4) return false
        return numbers.all { it in 0..255 }
    }

    fun String.toUUID() = try {
        UUID.fromString(this)!!
    } catch (e: IllegalArgumentException) {
        null
    }
}
