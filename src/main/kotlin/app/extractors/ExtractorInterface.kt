// Copyright 2017 Sourcerer Inc. All Rights Reserved.
// Author: Anatoly Kislov (anatoly@sourcerer.io)
// Author: Liubov Yaronskaya (lyaronskaya@sourcerer.io)

package app.extractors

import app.model.DiffFile
import app.model.CommitStats

interface ExtractorInterface {
    companion object {
        fun getLibraries(name: String): Set<String> {
            return ExtractorInterface::class.java.classLoader
                .getResourceAsStream("data/libraries/${name}_libraries.txt")
                .bufferedReader().readLines().toSet()
        }
    }

    fun extract(files: List<DiffFile>): List<CommitStats> {
        files.map { file ->
            file.old.imports = extractImports(file.old.content)
            file.new.imports = extractImports(file.new.content)
            file
        }

        val oldLibraryToCount = mutableMapOf<String, Int>()
        val newLibraryToCount = mutableMapOf<String, Int>()
        val oldFilesImports = files.fold(mutableSetOf<String>()) { acc, file ->
            acc.addAll(file.old.imports)
            acc
        }
        val newFilesImports = files.fold(mutableSetOf<String>()) { acc, file ->
            acc.addAll(file.new.imports)
            acc
        }

        oldFilesImports.forEach { oldLibraryToCount[it] = 0}
        newFilesImports.forEach { newLibraryToCount[it] = 0}


        files.filter { file -> file.language.isNotBlank() }
            .forEach { file ->
                val oldFileLibraries = mutableListOf<String>()
                file.old.content.forEach {
                    val lineLibs = getLineLibraries(it, file.old.imports)
                    oldFileLibraries.addAll(lineLibs)
                }
                file.old.imports.forEach { import ->
                    val numLines = oldFileLibraries.count { it == import }
                    oldLibraryToCount[import] =
                        oldLibraryToCount[import] as Int + numLines
                }

                val newFileLibraries = mutableListOf<String>()
                file.new.content.forEach {
                    val lineLibs = getLineLibraries(it, file.new.imports)
                    newFileLibraries.addAll(lineLibs)
                }
                file.new.imports.forEach { import ->
                    val numLines = newFileLibraries.count { it == import }
                    newLibraryToCount[import] =
                            newLibraryToCount[import] as Int + numLines
                }
            }

        val allImports = mutableSetOf<String>()
        allImports.addAll(oldFilesImports + newFilesImports)

        val libraryStats = allImports.map {
            CommitStats(
                numLinesAdded = oldLibraryToCount.getOrDefault(it, 0),
                numLinesDeleted = newLibraryToCount.getOrDefault(it, 0),
                type = Extractor.TYPE_LIBRARY,
                tech = it)
        }

        return files.filter { file -> file.language.isNotBlank() }
                    .groupBy { file -> file.language }
                    .map { (language, files) -> CommitStats(
                        numLinesAdded = files.fold(0) { total, file ->
                            total + file.getAllAdded().size },
                        numLinesDeleted = files.fold(0) { total, file ->
                            total + file.getAllDeleted().size },
                        type = Extractor.TYPE_LANGUAGE,
                        tech = language)} + libraryStats
    }

    fun extractImports(fileContent: List<String>): List<String> {
        return listOf()
    }

    fun tokenize(line: String): List<String> {
        val regex =
            Regex("""\s|,|;|\*|\n|\(|\)|\[|]|\{|}|\+|=|&|\$|!=|\.|>|<|#|@|:|\?""")
        val tokens = regex.split(line)
            .filter { it.isNotBlank() && !it.contains('"') && !it.contains('"')
                && it != "-"}
        return tokens
    }

    fun getLineLibraries(line: String, fileLibraries: List<String>): List<String> {
        return listOf()
    }
}
