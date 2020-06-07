package com.didiglobal.booster.transform.asm.cha

import com.didiglobal.booster.kotlinx.md5
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

internal class ClassSetCache {

    internal data class Key(val file: File, val hash: String)

    private val table = ConcurrentHashMap<Key, ClassSet>()

    /**
     * Put [file] into cache as [ClassSet]
     *
     * @return the present [ClassSet] if the cache already exists or an new [ClassSet] created from [file]
     */
    @Synchronized
    @Throws(FileNotFoundException::class)
    fun put(file: File): ClassSet {
        if (!file.exists()) {
            throw FileNotFoundException(file.path)
        }

        val entry = findByFile(file)
        val key = Key(file, file.md5())
        if (key == entry?.key) {
            return entry.value
        }

        // release the present cache
        entry?.let {
            this.table.remove(it.key)
            it.value.close()
        }

        return ClassSet.from(file).also {
            this.table[key] = it
        }
    }

    /**
     * Get the cached [ClassSet] by [file]
     *
     * @return the cached [ClassSet]
     */
    operator fun get(file: File): ClassSet? = this.table[Key(file, file.md5())]

    fun findByHash(hash: String) = this.table.entries.find { (k, _) ->
        k.hash == hash
    }

    fun findByFile(file: File) = this.table.entries.find { (k, _) ->
        k.file == file
    }


}
