/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.completion.test

import com.google.common.collect.ImmutableList
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.ui.JBColor
import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.idea.test.AstAccessControl
import org.jetbrains.kotlin.js.resolve.JsPlatform
import org.jetbrains.kotlin.resolve.TargetPlatform
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.junit.Assert
import java.util.*

/**
 * Extract a number of statements about completion from the given text. Those statements
 * should be asserted during test execution.
 */
public object ExpectedCompletionUtils {

    public class CompletionProposal {
        private val map: Map<String, String?>

        public constructor(lookupString: String) {
            map = HashMap<String, String?>()
            map.put(LOOKUP_STRING, lookupString)
        }

        public constructor(map: MutableMap<String, String?>) {
            this.map = map
            for (key in map.keySet()) {
                if (key !in validKeys) {
                    throw RuntimeException("Invalid key '$key'")
                }
            }
        }

        public constructor(json: JsonObject) {
            map = HashMap<String, String?>()
            for (entry in json.entrySet()) {
                val key = entry.getKey()
                if (key !in validKeys) {
                    throw RuntimeException("Invalid json property '$key'")
                }
                val value = entry.value
                if (value !is JsonNull) {
                    map.put(key, value.asString)
                }
            }
        }

        public fun matches(expectedProposal: CompletionProposal): Boolean
                = expectedProposal.map.entrySet().none { it.value != map[it.key] }

        override fun toString(): String {
            val jsonObject = JsonObject()
            for ((key, value) in map) {
                jsonObject.addProperty(key, value)
            }
            return jsonObject.toString()
        }

        companion object {
            public val LOOKUP_STRING: String = "lookupString"
            public val ALL_LOOKUP_STRINGS: String = "allLookupStrings"
            public val PRESENTATION_ITEM_TEXT: String = "itemText"
            public val PRESENTATION_TYPE_TEXT: String = "typeText"
            public val PRESENTATION_TAIL_TEXT: String = "tailText"
            public val PRESENTATION_TEXT_ATTRIBUTES: String = "attributes"
            public val validKeys: Set<String> = setOf(LOOKUP_STRING, ALL_LOOKUP_STRINGS, PRESENTATION_ITEM_TEXT, PRESENTATION_TYPE_TEXT, PRESENTATION_TAIL_TEXT, PRESENTATION_TEXT_ATTRIBUTES)
        }
    }

    private val UNSUPPORTED_PLATFORM_MESSAGE = "Only $JvmPlatform and $JsPlatform platforms are supported"

    private val EXIST_LINE_PREFIX = "EXIST:"

    private val ABSENT_LINE_PREFIX = "ABSENT:"
    private val ABSENT_JS_LINE_PREFIX = "ABSENT_JS:"
    private val ABSENT_JAVA_LINE_PREFIX = "ABSENT_JAVA:"

    private val EXIST_JAVA_ONLY_LINE_PREFIX = "EXIST_JAVA_ONLY:"
    private val EXIST_JS_ONLY_LINE_PREFIX = "EXIST_JS_ONLY:"

    private val NUMBER_LINE_PREFIX = "NUMBER:"
    private val NUMBER_JS_LINE_PREFIX = "NUMBER_JS:"
    private val NUMBER_JAVA_LINE_PREFIX = "NUMBER_JAVA:"

    private val NOTHING_ELSE_PREFIX = "NOTHING_ELSE"

    private val INVOCATION_COUNT_PREFIX = "INVOCATION_COUNT:"
    private val WITH_ORDER_PREFIX = "WITH_ORDER"
    private val AUTOCOMPLETE_SETTING_PREFIX = "AUTOCOMPLETE_SETTING:"

    public val RUNTIME_TYPE: String = "RUNTIME_TYPE:"

    public val KNOWN_PREFIXES: List<String> = ImmutableList.of(
            EXIST_LINE_PREFIX,
            ABSENT_LINE_PREFIX,
            ABSENT_JS_LINE_PREFIX,
            ABSENT_JAVA_LINE_PREFIX,
            EXIST_JAVA_ONLY_LINE_PREFIX,
            EXIST_JS_ONLY_LINE_PREFIX,
            NUMBER_LINE_PREFIX,
            NUMBER_JS_LINE_PREFIX,
            NUMBER_JAVA_LINE_PREFIX,
            INVOCATION_COUNT_PREFIX,
            WITH_ORDER_PREFIX,
            AUTOCOMPLETE_SETTING_PREFIX,
            NOTHING_ELSE_PREFIX,
            RUNTIME_TYPE,
            AstAccessControl.ALLOW_AST_ACCESS_DIRECTIVE)

    public fun itemsShouldExist(fileText: String, platform: TargetPlatform?): Array<CompletionProposal> {
        return when (platform) {
            JvmPlatform -> processProposalAssertions(fileText, EXIST_LINE_PREFIX, EXIST_JAVA_ONLY_LINE_PREFIX)
            JsPlatform -> processProposalAssertions(fileText, EXIST_LINE_PREFIX, EXIST_JS_ONLY_LINE_PREFIX)
            null -> processProposalAssertions(fileText, EXIST_LINE_PREFIX)
            else -> throw IllegalArgumentException(UNSUPPORTED_PLATFORM_MESSAGE)
        }
    }

    public fun itemsShouldAbsent(fileText: String, platform: TargetPlatform?): Array<CompletionProposal> {
        return when (platform) {
            JvmPlatform -> processProposalAssertions(fileText, ABSENT_LINE_PREFIX, ABSENT_JAVA_LINE_PREFIX, EXIST_JS_ONLY_LINE_PREFIX)
            JsPlatform -> processProposalAssertions(fileText, ABSENT_LINE_PREFIX, ABSENT_JS_LINE_PREFIX, EXIST_JAVA_ONLY_LINE_PREFIX)
            null -> processProposalAssertions(fileText, ABSENT_LINE_PREFIX)
            else -> throw IllegalArgumentException(UNSUPPORTED_PLATFORM_MESSAGE)
        }
    }

    public fun processProposalAssertions(fileText: String, vararg prefixes: String): Array<CompletionProposal> {
        val proposals = ArrayList<CompletionProposal>()
        for (proposalStr in InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, *prefixes)) {
            if (proposalStr.startsWith("{")) {
                val parser = JsonParser()
                val json = parser.parse(proposalStr)
                proposals.add(CompletionProposal(json as JsonObject))
            }
            else if (proposalStr.startsWith("\"") && proposalStr.endsWith("\"")) {
                proposals.add(CompletionProposal(proposalStr.substring(1, proposalStr.length() - 1)))
            }
            else {
                for (item in proposalStr.split(",")) {
                    proposals.add(CompletionProposal(item.trim()))
                }
            }
        }

        return ArrayUtil.toObjectArray(proposals, javaClass<CompletionProposal>())
    }

    public fun getExpectedNumber(fileText: String, platform: TargetPlatform?): Int? {
        return when (platform) {
            null -> InTextDirectivesUtils.getPrefixedInt(fileText, NUMBER_LINE_PREFIX)
            JvmPlatform -> getPlatformExpectedNumber(fileText, NUMBER_JAVA_LINE_PREFIX)
            JsPlatform -> getPlatformExpectedNumber(fileText, NUMBER_JS_LINE_PREFIX)
            else -> throw IllegalArgumentException(UNSUPPORTED_PLATFORM_MESSAGE)
        }
    }

    public fun isNothingElseExpected(fileText: String): Boolean {
        return !InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, NOTHING_ELSE_PREFIX).isEmpty()
    }

    public fun getInvocationCount(fileText: String): Int? {
        return InTextDirectivesUtils.getPrefixedInt(fileText, INVOCATION_COUNT_PREFIX)
    }

    public fun getAutocompleteSetting(fileText: String): Boolean? {
        return InTextDirectivesUtils.getPrefixedBoolean(fileText, AUTOCOMPLETE_SETTING_PREFIX)
    }

    public fun isWithOrder(fileText: String): Boolean {
        return !InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, WITH_ORDER_PREFIX).isEmpty()
    }

    public fun assertDirectivesValid(fileText: String) {
        InTextDirectivesUtils.assertHasUnknownPrefixes(fileText, KNOWN_PREFIXES)
    }

    public fun assertContainsRenderedItems(expected: Array<CompletionProposal>, items: Array<LookupElement>, checkOrder: Boolean, nothingElse: Boolean) {
        val itemsInformation = getItemsInformation(items)
        val allItemsString = listToString(itemsInformation)

        val leftItems = if (nothingElse) LinkedHashSet(itemsInformation) else null

        var indexOfPrevious = Integer.MIN_VALUE

        for (expectedProposal in expected) {
            var isFound = false

            for (index in itemsInformation.indices) {
                val proposal = itemsInformation.get(index)

                if (proposal.matches(expectedProposal)) {
                    isFound = true

                    Assert.assertTrue("Invalid order of existent elements in $allItemsString",
                                      !checkOrder || index > indexOfPrevious)
                    indexOfPrevious = index

                    leftItems?.remove(proposal)

                    break
                }
            }

            if (!isFound) {
                if (allItemsString.isEmpty()) {
                    Assert.fail("Completion is empty but $expectedProposal is expected")
                }
                else {
                    Assert.fail("Expected $expectedProposal not found in:\n$allItemsString")
                }
            }
        }

        if (leftItems != null && !leftItems.isEmpty()) {
            Assert.fail("No items not mentioned in EXIST directives expected but some found:\n" + listToString(leftItems))
        }
    }

    private fun getPlatformExpectedNumber(fileText: String, platformNumberPrefix: String): Int? {
        val prefixedInt = InTextDirectivesUtils.getPrefixedInt(fileText, platformNumberPrefix)
        if (prefixedInt != null) {
            Assert.assertNull("There shouldn't be $NUMBER_LINE_PREFIX and $platformNumberPrefix prefixes set in same time",
                              InTextDirectivesUtils.getPrefixedInt(fileText, NUMBER_LINE_PREFIX))
            return prefixedInt
        }

        return InTextDirectivesUtils.getPrefixedInt(fileText, NUMBER_LINE_PREFIX)
    }

    public fun assertNotContainsRenderedItems(unexpected: Array<CompletionProposal>, items: Array<LookupElement>) {
        val itemsInformation = getItemsInformation(items)
        val allItemsString = listToString(itemsInformation)

        for (unexpectedProposal in unexpected) {
            for (proposal in itemsInformation) {
                Assert.assertFalse("Unexpected '$unexpectedProposal' presented in $allItemsString",
                                   proposal.matches(unexpectedProposal))
            }
        }
    }

    public fun getItemsInformation(items: Array<LookupElement>): List<CompletionProposal> {
        val presentation = LookupElementPresentation()

        val result = ArrayList<CompletionProposal>(items.size())
        for (item in items) {
            item.renderElement(presentation)

            val map = HashMap<String, String?>()
            map.put(CompletionProposal.LOOKUP_STRING, item.lookupString)

            map.put(CompletionProposal.ALL_LOOKUP_STRINGS, item.allLookupStrings.sort().joinToString())

            if (presentation.itemText != null) {
                map.put(CompletionProposal.PRESENTATION_ITEM_TEXT, presentation.itemText)
                map.put(CompletionProposal.PRESENTATION_TEXT_ATTRIBUTES, textAttributes(presentation))
            }

            if (presentation.typeText != null) {
                map.put(CompletionProposal.PRESENTATION_TYPE_TEXT, presentation.typeText)
            }

            if (presentation.tailText != null) {
                map.put(CompletionProposal.PRESENTATION_TAIL_TEXT, presentation.tailText)
            }

            result.add(ExpectedCompletionUtils.CompletionProposal(map))
        }
        return result
    }

    private fun textAttributes(presentation: LookupElementPresentation): String {
        return StringBuilder {
            if (presentation.isItemTextBold) {
                append("bold")
            }
            if (presentation.isItemTextUnderlined) {
                if (length() > 0) append(" ")
                append("underlined")
            }
            val foreground = presentation.itemTextForeground
            if (foreground != JBColor.foreground()) {
                assert(foreground == LookupCellRenderer.getGrayedForeground(false))
                if (length() > 0) append(" ")
                append("grayed")
            }
            if (presentation.isStrikeout) {
                if (length() > 0) append(" ")
                append("strikeout")
            }
        }.toString()
    }

    public fun listToString(items: Collection<CompletionProposal>): String = items.joinToString("\n")
}
