package com.oderommanager.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object GameCodeGenerator {

    private const val PREFIX = "0"
    private val CHARSET = ('A'..'Z') + ('0'..'9')

    fun generateCandidate(): String {
        return PREFIX + (1..3).map { CHARSET.random() }.joinToString("")
    }

    fun isCodeInImgsFolder(
        context: Context,
        sdCardUri: Uri,
        firmwareImgsPath: String,
        code: String
    ): Boolean {
        return try {
            val sdRoot = DocumentFile.fromTreeUri(context, sdCardUri) ?: return false

            // Navigate to IMGS folder
            var current: DocumentFile = sdRoot
            for (segment in firmwareImgsPath.split("/")) {
                current = current.findFile(segment) ?: return false
            }

            // Navigate to [code[0]] / [code[1]]
            val firstLetter = current.findFile(code[0].toString()) ?: return false
            val secondLetter = firstLetter.findFile(code[1].toString()) ?: return false

            secondLetter.findFile("$code.bmp") != null
        } catch (e: Exception) {
            false
        }
    }

    fun scanUsedCodesInImgs(
        context: Context,
        sdCardUri: Uri,
        firmwareImgsPath: String
    ): Set<String> {
        val used = mutableSetOf<String>()
        return try {
            val sdRoot = DocumentFile.fromTreeUri(context, sdCardUri) ?: return used

            var imgsFolder: DocumentFile = sdRoot
            for (segment in firmwareImgsPath.split("/")) {
                imgsFolder = imgsFolder.findFile(segment) ?: return used
            }

            imgsFolder.listFiles().forEach { firstDir ->
                if (firstDir.isDirectory) {
                    firstDir.listFiles().forEach { secondDir ->
                        if (secondDir.isDirectory) {
                            secondDir.listFiles().forEach { bmpFile ->
                                val name = bmpFile.name
                                if (name != null && name.endsWith(".bmp", ignoreCase = true)) {
                                    used.add(name.substringBeforeLast('.').uppercase())
                                }
                            }
                        }
                    }
                }
            }
            used
        } catch (e: Exception) {
            used
        }
    }
}
