/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import io.nimbly.tzatziki.psi.findCucumberStepDefinitions
import io.nimbly.tzatziki.psi.row
import io.nimbly.tzatziki.psi.rowNumber
import io.nimbly.tzatziki.psi.table
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaRunConfiguration
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaScenarioRunConfigurationProducer
import org.jetbrains.plugins.cucumber.java.steps.AbstractJavaStepDefinition
import org.jetbrains.plugins.cucumber.psi.*

class TzCucumberJavaRunConfigurationProducer : CucumberJavaScenarioRunConfigurationProducer() {

    override fun setupConfigurationFromContext(
        configuration: CucumberJavaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {

        val element = sourceElement.get()
        val row = findRow(element.parent)
            ?: return false

        row.parentOfType<GherkinStepsHolder>()
            ?: return false

        val line = findLineNumber(element)
        val example = row.rowNumber

        super.setupConfigurationFromContext(configuration, context, sourceElement)

        if (configuration.filePath.matches(".*:[0-9]+$".toRegex()))
            return true

        configuration.filePath = configuration.filePath + ":" + line;

        return true
    }

    override fun isConfigurationFromContext(
        configuration: CucumberJavaRunConfiguration,
        context: ConfigurationContext): Boolean {

        val element = context.psiLocation ?:
            return false

        val configLine = configuration.filePath.substringAfterLast(":").toIntOrNull()
        val line = findLineNumber(element)
        if (line != configLine)
            return false

        return configuration.filePath.endsWith(":$line")
    }

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return shouldReplace(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        val row = findRow(self.sourceElement.parent)
            ?: return false
        val scenario = row.parentOfType<GherkinStepsHolder>()
            ?: return false
        val definition = findCucumberStepDefinitions(scenario).firstOrNull()
            ?: return false
        if (definition !is AbstractJavaStepDefinition)
            return false
        return true
    }

    override fun getConfigurationName(context: ConfigurationContext): String {
        val name = super.getConfigurationName(context)
        if (name.matches(".*(- Example n°)[0-9]+\$".toRegex()))
            return name

        val element = context.psiLocation
            ?: return name
        val row = findRow(element.parent)
            ?: return name

        row.parentOfType<GherkinStepsHolder>()
            ?: return name

        var block = ""
        if (row.table.parent is GherkinExamplesBlock) {
            val title = row.table.parent.node.findChildByType(GherkinTokenTypes.TEXT)
            if (title != null) {
                var t = title.text.substringBefore("\n").take(12)
                if (t.length < title.text.length)
                    t += "..."
                block = " - " + t.take(12)
            }
        }
        val example = row.rowNumber
        return "$name$block - Example n°$example"
    }

    private fun findLineNumber(element: PsiElement): Int? {
        val document = PsiDocumentManager.getInstance(element.containingFile.project).getDocument(element.containingFile)
            ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    private fun findRow(element: PsiElement): GherkinTableRow? {
        return element as? GherkinTableRow
            ?: element.parent as? GherkinTableRow
            ?: (element.parent as? GherkinTableCell)?.row
    }
}