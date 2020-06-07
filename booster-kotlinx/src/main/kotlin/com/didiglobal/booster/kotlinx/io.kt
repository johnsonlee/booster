package com.didiglobal.booster.kotlinx

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.math.BigInteger
import java.security.MessageDigest

fun File.file(vararg path: String) = File(this, path.joinToString(File.separator))

/**
 * Create a new file if not exists
 *
 * @author johnsonlee
 */
fun File.touch(): File {
    if (!this.exists()) {
        this.parentFile?.mkdirs()
        this.createNewFile()
    }
    return this
}

/**
 * Return the first line of file
 */
fun File.head(): String? = inputStream().use { it.head() }

fun File.md5(): String = inputStream().use {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val digest = MessageDigest.getInstance("MD5")

    while (-1 != it.read(buffer, 0, buffer.size)) {
        digest.update(buffer)
    }

    return BigInteger(1, digest.digest()).toString(16).padStart(32, '0')
}

/**
 * Returns the first line of input stream
 */
fun InputStream.head(): String? = BufferedReader(InputStreamReader(this)).head()

/**
 * Returns the first line of reader
 */
fun Reader.head(): String? = BufferedReader(this).readLine()

/**
 * Redirect this input stream to the specified file
 *
 * @author johnsonlee
 */
fun InputStream.redirect(file: File): Long = file.touch().outputStream().use { this.copyTo(it) }

/**
 * Redirect this byte data to the specified file
 *
 * @author johnsonlee
 */
fun ByteArray.redirect(file: File): Long = this.inputStream().use { it.redirect(file) }

/**
 * Redirect this byte data to the specified output stream
 *
 * @author johnsonlee
 */
fun ByteArray.redirect(output: OutputStream): Long = this.inputStream().copyTo(output)
