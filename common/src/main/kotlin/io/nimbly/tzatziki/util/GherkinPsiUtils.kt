/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.util

import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.startOffset
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.Tzatziki
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

fun loadStepParams(step: GherkinStep): List<TextRange> {
    val references = step.references
    if (references.size != 1 || references[0] !is CucumberStepReference) {
        return emptyList()
    }
    val reference = references[0] as CucumberStepReference
    val definition = reference.resolveToDefinition()
    if (definition != null) {
        return GherkinPsiUtil.buildParameterRanges(step, definition, reference.rangeInElement.startOffset)
            ?.map { it.shiftRight(step.startOffset) }
            ?: emptyList()
    }
    return emptyList()
}

val PsiElement.description: String
    get() = descriptionRange.substring(this.text)

val PsiElement.descriptionRange: TextRange
    get() {
        val indexOfFirst = this.text.indexOfFirst { it == ' ' }
        if (indexOfFirst <0)
            return TextRange.EMPTY_RANGE
        var start = indexOfFirst + 1
        start += this.text.substring(start).indexOfFirst { it != ' ' }
        val eol = this.text.indexOfFirst { it == '\n' }
        return TextRange(
            start,
            if (eol > 0) eol else this.textLength
        )
    }

val GherkinStepsHolder.feature: GherkinFeature
    get() = PsiTreeUtil.getParentOfType(this, GherkinFeature::class.java)!!

val GherkinStepsHolder.isBackground: Boolean
    get() = this is GherkinScenario && this.isBackground

fun GherkinStepsHolder.checkExpression(tagExpression: Expression?): Boolean {
    if (tagExpression == null)
        return true
    if (isBackground)
        return true
    if (tagExpression.evaluate(this.allTags.map { it.name }))
        return true
    return false
}

fun GherkinFeature.checkExpression(tagExpression: Expression?): Boolean {
    if (tagExpression == null)
        return true
    if (tagExpression.evaluate(this.tags.map { it.name }))
        return true
    return this.scenarios.find {
        !it.isBackground && it.checkExpression(tagExpression) } != null
}

fun GherkinFile.checkExpression(tagExpression: Expression?): Boolean {
    if (tagExpression == null)
        return true
    return this.features.find { it.checkExpression(tagExpression) } != null
}

val GherkinFeature.tags: List<GherkinTag>
    get() {
        val list = mutableListOf<GherkinTag>()
        var ref: PsiElement? = this
        while (ref != null) {
            ref = PsiTreeUtil.getPrevSiblingOfType(ref, GherkinTag::class.java)
            if (ref != null)
                list.add(ref)
        }
        return list
    }

val GherkinStepsHolder.allTags: Set<GherkinTag>
    get() = this.feature.tags.toSet()
        .union(this.tags.toSet())

val GherkinStep.allTags: Set<GherkinTag>
    get() = this.stepHolder.feature.tags.toSet()
                .union(this.stepHolder.tags.toSet())

fun Module.getGherkinScope(recursive: Boolean = false): GlobalSearchScope {
    if (recursive) {
        var scope = this.getGherkinScope(false)
        this.subModules.forEach { m ->
            scope = scope.union(m.getGherkinScope(true))
        }
        return scope
    }
    return GlobalSearchScope.getScopeRestrictedByFileTypes(
        GlobalSearchScope.moduleScope(this), GherkinFileType.INSTANCE
    )
}


fun Project.getGherkinScope(): GlobalSearchScope {

    val vFiles = ProjectRootManager.getInstance(this).contentRootsFromAllModules

    var scope = GlobalSearchScope.EMPTY_SCOPE
    vFiles.forEach { vdir ->
        val module = vdir.getDirectory(this)?.getModule()
        if (module != null)
            scope = scope.union(module.getGherkinScope())
    }

    return scope
}

fun AbstractStepDefinition.isDeprecated(): Boolean {
    val element = element
        ?: return false

    Tzatziki().extensionList.forEach {
        if (it.isDeprecated(element))
            return true
    }
    return false;
}

fun PsiElement.isDeprecated(): Boolean {
    Tzatziki().extensionList.forEach {
        if (it.isDeprecated(this))
            return true
    }
    return false;
}

fun GherkinStep.findCucumberStepReference(): CucumberStepReference?
    = findCucumberStepReferences().firstOrNull()

fun GherkinStep.findCucumberStepReferences(): List<CucumberStepReference>
    = references.filterIsInstance<CucumberStepReference>()

fun GherkinStep.findCucumberStepDefinitions(): List<AbstractStepDefinition>
    = this.findCucumberStepReferences().flatMap { it.resolveToDefinitions() }

fun GherkinStepsHolder.findCucumberStepDefinitions(): List<AbstractStepDefinition>
    = steps.flatMap { step -> step.findCucumberStepDefinitions() }

/**
 * Please take care of @IndexNotReadyException
 */
fun findUsages(function: PsiElement): List<PsiReference> {

    val usagesManager = (FindManager.getInstance(function.project) as FindManagerImpl).findUsagesManager
    val handler = usagesManager.getFindUsagesHandler(function, false)
        ?: return emptyList()

    val usages = mutableListOf<PsiReference>()
    handler.processElementUsages(function, {
        val ref = it.reference
        if (ref != null)
            usages.add(ref)
        true
    }, handler.findUsagesOptions)
    return usages
}
