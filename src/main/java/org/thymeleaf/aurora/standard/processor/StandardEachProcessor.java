/*
 * =============================================================================
 * 
 *   Copyright (c) 2011-2014, The THYMELEAF team (http://www.thymeleaf.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.thymeleaf.aurora.standard.processor;

import java.util.Iterator;

import org.thymeleaf.aurora.context.ITemplateProcessingContext;
import org.thymeleaf.aurora.engine.AttributeName;
import org.thymeleaf.aurora.engine.IElementTagActionHandler;
import org.thymeleaf.aurora.model.IProcessableElementTag;
import org.thymeleaf.aurora.processor.element.AbstractAttributeMatchingHTMLElementProcessor;

/**
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.0
 *
 */
public class StandardEachProcessor extends AbstractAttributeMatchingHTMLElementProcessor {


    public StandardEachProcessor() {
        super("each", 200);
    }



    public void process(
            final ITemplateProcessingContext processingContext,
            final IProcessableElementTag tag,
            final IElementTagActionHandler actionHandler) {

        // We know this will not be null, because we linked the processor to a specific attribute
        final AttributeName attributeName = getMatchingAttributeName().getMatchingAttributeName();

        final String[] values = new String[] { "Iteration One", "Iteration Two", "Iteration Three" };
//        actionHandler.iterateElement("iter", "iterStat", values);
//        actionHandler.iterateElement("iter", "iterStat", new int[] { 12, 3, 123, 512311, 23, 3, 3, 123, 231, 2311});
        actionHandler.iterateElement("iter", "iterStat", new Iterator<String>() {

            private int i = 0;

            public boolean hasNext() {
                return i < 10;
            }

            public String next() {
                return "Iteration " + i++;
            }

            public void remove() {

            }

        });

        tag.getAttributes().removeAttribute(attributeName);

    }


}
