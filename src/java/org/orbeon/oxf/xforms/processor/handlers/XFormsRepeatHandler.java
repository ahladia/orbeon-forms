/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.controls.RepeatIterationControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.processor.XFormsElementFilterContentHandler;
import org.orbeon.oxf.xml.DeferredContentHandler;
import org.orbeon.oxf.xml.DeferredContentHandlerImpl;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Map;

/**
 * Handle xforms:repeat.
 */
public class XFormsRepeatHandler extends HandlerBase {

    public XFormsRepeatHandler() {
        // This is a repeating element
        super(true, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String repeatId = handlerContext.getId(attributes);
        final String effectiveId = handlerContext.getEffectiveId(attributes);

        final boolean isTopLevelRepeat = handlerContext.countParentRepeats() == 0;
        final boolean isRepeatSelected = handlerContext.isRepeatSelected() || isTopLevelRepeat;
        final boolean isMustGenerateTemplate = handlerContext.isTemplate() || isTopLevelRepeat;
        final int currentIteration = handlerContext.getCurrentIteration();

        final XFormsControls.ControlsState currentControlState = containingDocument.getXFormsControls().getCurrentControlsState();
        final Map effectiveRepeatIdToIterations = currentControlState.getEffectiveRepeatIdToIterations();
        final Map repeatIdToIndex = currentControlState.getRepeatIdToIndex();

        final XFormsRepeatControl repeatControl = handlerContext.isTemplate() ? null : (XFormsRepeatControl) containingDocument.getObjectById(effectiveId);
        final boolean isConcreteControl = repeatControl != null;

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        // Place interceptor on output
        final DeferredContentHandler savedOutput = handlerContext.getController().getOutput();
        final OutputInterceptor outputInterceptor = new OutputInterceptor(savedOutput, spanQName, new OutputInterceptor.Listener() {
            public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                // Delimiter: begin repeat
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end", "repeat-begin-" + effectiveId);
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
            }
        });
        handlerContext.getController().setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(outputInterceptor)));
        setContentHandler(handlerContext.getController().getOutput());

        if (isConcreteControl && (isTopLevelRepeat || !isMustGenerateTemplate)) {

            final int currentRepeatIndex = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : ((Integer) repeatIdToIndex.get(repeatId)).intValue();
            final int currentRepeatIterations = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : ((Integer) effectiveRepeatIdToIterations.get(effectiveId)).intValue();

            // Unroll repeat
            for (int i = 1; i <= currentRepeatIterations; i++) {
                if (i > 1) {
                    // Delimiter: between repeat entries
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
                }

                // Is the current iteration selected?
                final boolean isCurrentIterationSelected = isRepeatSelected && i == currentRepeatIndex;
                final boolean isCurrentIterationRelevant = ((RepeatIterationControl) repeatControl.getChildren().get(i - 1)).isRelevant();
                final int numberOfParentRepeats = handlerContext.countParentRepeats();

                // Determine classes to add on root elements and around root characters
                final FastStringBuffer addedClasses;
                {
                    addedClasses = new FastStringBuffer(200);
                    if (isCurrentIterationSelected && !isStaticReadonly(repeatControl)) {
                        addedClasses.append("xforms-repeat-selected-item-");
                        addedClasses.append(Integer.toString((numberOfParentRepeats % 4) + 1));
                    }
                    if (!isCurrentIterationRelevant)
                        addedClasses.append(" xforms-disabled");
                    // Add classes such as DnD classes, etc.
                    addRepeatClasses(addedClasses, attributes);
                }
                outputInterceptor.setAddedClasses(addedClasses);

                // Apply the content of the body for this iteration
                handlerContext.pushRepeatContext(false, i, false, isCurrentIterationSelected);
                try {
                    handlerContext.getController().repeatBody();
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, new ExtendedLocationData(repeatControl.getLocationData(), "unrolling xforms:repeat control", repeatControl.getControlElement()));
                }
                outputInterceptor.flushCharacters(true, true);
                handlerContext.popRepeatContext();
            }
        }

        // Generate template
        final boolean isNoscript = XFormsProperties.isNoscript(containingDocument);
        if (isMustGenerateTemplate && !isNoscript) {// don't generate templates in noscript mode as they won't be used

            if (!outputInterceptor.isMustGenerateFirstDelimiters()) {
                // Delimiter: between repeat entries
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
            }

            // Determine classes to add on root elements and around root characters
            final FastStringBuffer addedClasses = new FastStringBuffer(isTopLevelRepeat ? "xforms-repeat-template" : "");

            // Add classes such as DnD classes, etc.
            addRepeatClasses(addedClasses, attributes);

            outputInterceptor.setAddedClasses(addedClasses);

            // Apply the content of the body for this iteration
            handlerContext.pushRepeatContext(true, 0, false, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true, true);
            handlerContext.popRepeatContext();
        }

        // If no delimiter has been generated, try to find one!
        if (outputInterceptor.getDelimiterNamespaceURI() == null) {

            outputInterceptor.setForward(false); // prevent interceptor to output anything

            handlerContext.pushRepeatContext(true, 0, false, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true, true);
            handlerContext.popRepeatContext();
        }

        // Restore output
        handlerContext.getController().setOutput(savedOutput);
        setContentHandler(savedOutput);

        // Delimiter: end repeat
        outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end", "repeat-end-" + effectiveId);
    }

    private static void addRepeatClasses(FastStringBuffer sb, Attributes attributes) {

        final String dndAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd");
        if (dndAttribute != null && !dndAttribute.equals("none")) {

            sb.append(" xforms-dnd");

            if (dndAttribute.equals("vertical"))
                sb.append(" xforms-dnd-vertical");
            if (dndAttribute.equals("horizontal"))
                sb.append(" xforms-dnd-horizontal");

            if (attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd-over") != null)
                sb.append(" xforms-dnd-over");
        }
    }
}
