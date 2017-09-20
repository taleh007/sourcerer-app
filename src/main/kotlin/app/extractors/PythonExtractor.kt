// Copyright 2017 Sourcerer Inc. All Rights Reserved.
// Author: Liubov Yaronskaya (lyaronskaya@sourcerer.io)
// Author: Anatoly Kislov (anatoly@sourcerer.io)

package app.extractors

import app.model.CommitStats
import app.model.DiffFile
import org.dmg.pmml.FieldName
import org.jpmml.evaluator.FieldValue
import org.jpmml.evaluator.ModelEvaluatorFactory
import org.jpmml.evaluator.ProbabilityDistribution
import org.jpmml.model.PMMLUtil
import java.io.File

class PythonExtractor : ExtractorInterface {
    companion object {
        val LANGUAGE_NAME = "python"
        val FILE_EXTS = listOf("py", "py3")
        val LIBRARIES_CLASSIFIER_PATH = "data/models/python.pmml"
        val LIBRARIES = listOf<String>() // TODO: add loading labels
        val LANGUAGE_LABEL = LIBRARIES.indexOf(LANGUAGE_NAME).toString()
    }

    val pmml = PMMLUtil.unmarshal(File(LIBRARIES_CLASSIFIER_PATH).inputStream())
    val evaluator = ModelEvaluatorFactory.newInstance()
                                         .newModelEvaluator(pmml)

    override fun extract(files: List<DiffFile>): List<CommitStats> {
        files.map { file -> file.language = LANGUAGE_NAME }
        return super.extract(files)
    }

    override fun extractImports(fileContent: List<String>): List<String> {
        val imports = mutableSetOf<String>()

        val regex =
            Regex("""(from\s+(\w+)[.\w+]*\s+import|import\s+(\w+[,\s*\w+]*))""")
        fileContent.forEach {
            val res = regex.find(it)
            if (res != null) {
                val lineLibs = res.groupValues.last { it != "" }
                    .split(Regex(""",\s*"""))
                imports.addAll(lineLibs)
            }
        }

        return imports.toList()

    }

    override fun tokenize(line: String): List<String> {
        val regex = Regex("""^([^\n]*#|\s*\"\"\"|\s*import|\s*from)[^\n]*""")
        return super.tokenize(regex.replace(line, ""))
    }

    override fun getLineLibraries(line: String, fileLibraries: List<String>): List<String> {
        val arguments = LinkedHashMap<FieldName, FieldValue>()

        for (inputField in evaluator.inputFields) {
            val inputFieldName = inputField.name
            val inputFieldValue = inputField.prepare(tokenize(line))
            arguments.put(inputFieldName, inputFieldValue)
        }

        val result = evaluator.evaluate(arguments)

        val targetFieldName = evaluator.targetFields[0].name
        val targetFieldValue = result[targetFieldName] as ProbabilityDistribution

        val categoryValues = targetFieldValue.categoryValues.toList()
        val probabilities = categoryValues.map { targetFieldValue.getProbability(it) }
        val maxProbability = probabilities.max() as Double
        val maxProbabilityCategory = categoryValues[probabilities.indexOf(maxProbability)]
        val selectedCategories = categoryValues.filter {
            targetFieldValue.getProbability(it) > 0.3 * maxProbability
        }.map { LIBRARIES[it.toInt()] }

        if (maxProbabilityCategory == LANGUAGE_LABEL) {
            return emptyList()
        }

        val lineLibraries = fileLibraries.filter { it in selectedCategories }
        return lineLibraries
    }
}
