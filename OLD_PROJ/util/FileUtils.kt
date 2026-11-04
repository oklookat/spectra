package com.oklookat.spectra.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {
    @Throws(IOException::class)
    fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { `is` ->
            FileOutputStream(dest).use { os ->
                val buffer = ByteArray(8192)
                var length: Int
                while ((`is`.read(buffer).also { length = it }) > 0) {
                    os.write(buffer, 0, length)
                }
            }
        }
    }
}
