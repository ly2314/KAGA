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

package com.waicool20.kaga.util

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread


class StreamGobbler(val process: Process?) {
    fun run() {
        val handler = Thread.UncaughtExceptionHandler { _, throwable ->
            if (throwable.message != "Stream closed") throw throwable // Ignore stream closed errors
        }
        thread { BufferedReader(InputStreamReader(process?.inputStream)).forEachLine(::println) }.uncaughtExceptionHandler = handler
        thread { BufferedReader(InputStreamReader(process?.errorStream)).forEachLine(::println) }.uncaughtExceptionHandler = handler
    }
}
