/**
 * Copyright 2013 Nitor Creations Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nitorcreations.robotframework.eclipseide.internal.assistant.proposalgenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.graphics.Image;

import com.nitorcreations.robotframework.eclipseide.builder.parser.LineType;
import com.nitorcreations.robotframework.eclipseide.builder.parser.RobotLine;
import com.nitorcreations.robotframework.eclipseide.internal.util.FileWithType;
import com.nitorcreations.robotframework.eclipseide.internal.util.LineFinder;
import com.nitorcreations.robotframework.eclipseide.internal.util.LineMatchVisitor;
import com.nitorcreations.robotframework.eclipseide.internal.util.VisitorInterest;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString.ArgumentType;

public class KeywordDefinitionAttemptVisitor implements AttemptVisitor {
    final Map<String, List<KeywordDefinitionAttemptVisitor.KeywordNeed>> undefinedKeywords;

    public KeywordDefinitionAttemptVisitor(IFile file, ParsedString argument) {
        undefinedKeywords = collectUndefinedKeywords(file, argument);
    }

    private static final Set<LineType> KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES = new HashSet<LineType>();

    static {
        // all LineTypes that might have KEYWORD_CALL arguments or keyword definitions
        KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES.add(LineType.TESTCASE_TABLE_TESTCASE_BEGIN);
        KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES.add(LineType.TESTCASE_TABLE_TESTCASE_LINE);
        KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES.add(LineType.KEYWORD_TABLE_KEYWORD_BEGIN);
        KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES.add(LineType.KEYWORD_TABLE_KEYWORD_LINE);
        KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES.add(LineType.CONTINUATION_LINE);
        KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES.add(LineType.SETTING_TABLE_LINE);
    }

    @Override
    public RobotCompletionProposalSet visitAttempt(String attempt, IRegion replacementRegion) {
        RobotCompletionProposalSet ourProposalSet = new RobotCompletionProposalSet();
        for (Entry<String, List<KeywordDefinitionAttemptVisitor.KeywordNeed>> e : undefinedKeywords.entrySet()) {
            String key = e.getKey();
            if (key.toLowerCase().startsWith(attempt)) {
                ParsedString proposal = new ParsedString(key, 0);
                proposal.setType(ArgumentType.SETTING_KEY);

                Image image = null;
                String displayString = key;
                StringBuilder sb = new StringBuilder();
                sb.append("Called from the following testcases/keywords:<ul>");
                for (KeywordDefinitionAttemptVisitor.KeywordNeed need : e.getValue()) {
                    String callerType = need.callingTestcaseOrKeyword.getType() == ArgumentType.NEW_TESTCASE ? "TEST CASE" : "KEYWORD";
                    String callerName = need.callingTestcaseOrKeyword.getValue();
                    sb.append("<li><b>" + callerType + "</b> " + callerName + "</li>");
                }
                String additionalProposalInfo = sb.toString();
                String informationDisplayString = null;
                ourProposalSet.getProposals().add(new RobotCompletionProposal(proposal, null, replacementRegion, image, displayString, informationDisplayString, additionalProposalInfo));
            }
        }
        return ourProposalSet;
    }

    private static class KeywordNeed {
        public final ParsedString callingTestcaseOrKeyword;
        public final ParsedString calledKeyword;

        KeywordNeed(ParsedString callingTestcaseOrKeyword, ParsedString calledKeyword) {
            this.callingTestcaseOrKeyword = callingTestcaseOrKeyword;
            this.calledKeyword = calledKeyword;
        }
    }

    /**
     * Collect a list of keywords that are called in this file, but not defined in the file or in imported
     * resources/libraries. If assumeThisKeywordIsUndefined is not null, then it will not be considered as defined. This
     * is used to include the keyword already defined at the line where the cursor already is.
     */
    private static Map<String, List<KeywordDefinitionAttemptVisitor.KeywordNeed>> collectUndefinedKeywords(final IFile file, final ParsedString assumeThisKeywordIsUndefined) {
        final Map<String, List<KeywordDefinitionAttemptVisitor.KeywordNeed>> neededKeywords = new LinkedHashMap<String, List<KeywordDefinitionAttemptVisitor.KeywordNeed>>();
        final List<String> definedKeywords = new ArrayList<String>();
        LineFinder.acceptMatches(file, new LineMatchVisitor() {

            @Override
            public VisitorInterest visitMatch(RobotLine line, FileWithType lineLocation) {
                if (lineLocation.getFile() == file) {
                    visitKeywordCalls(line);
                }
                if (line.type == LineType.KEYWORD_TABLE_KEYWORD_BEGIN) {
                    visitKeywordDefinition(line, lineLocation);
                }
                return VisitorInterest.CONTINUE;
            }

            private ParsedString lastDefinedTestcaseOrKeyword;

            private void visitKeywordCalls(RobotLine line) {
                for (ParsedString argument : line.arguments) {
                    switch (argument.getType()) {
                        case NEW_TESTCASE:
                        case NEW_KEYWORD:
                            lastDefinedTestcaseOrKeyword = argument;
                            break;
                        case KEYWORD_CALL:
                        case KEYWORD_CALL_DYNAMIC:
                            String argumentStr = argument.getValue();
                            List<KeywordDefinitionAttemptVisitor.KeywordNeed> list = neededKeywords.get(argumentStr);
                            if (list == null) {
                                list = new ArrayList<KeywordDefinitionAttemptVisitor.KeywordNeed>();
                                neededKeywords.put(argumentStr, list);
                            }
                            list.add(new KeywordNeed(lastDefinedTestcaseOrKeyword, argument));
                            break;
                    }
                }
            }

            private void visitKeywordDefinition(RobotLine line, FileWithType lineLocation) {
                ParsedString definedKeyword = line.arguments.get(0);
                if (definedKeyword != assumeThisKeywordIsUndefined) {
                    definedKeywords.add(definedKeyword.getValue());
                }
            }

            @Override
            public boolean visitImport(IFile currentFile, RobotLine line) {
                return true;
            }

            @Override
            public Set<LineType> getWantedLineTypes() {
                return KEYWORD_CALLS_AND_DEFINITIONS_LINETYPES;
            }

            @Override
            public boolean wantsLibraryVariables() {
                return false;
            }

            @Override
            public boolean wantsLibraryKeywords() {
                return true;
            }
        });
        neededKeywords.keySet().removeAll(definedKeywords);
        return neededKeywords;
    }
}
