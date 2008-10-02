/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XFormsFocusEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.7 The setfocus Element
 */
public class XFormsSetfocusAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();


        final String controlIdAttributeValue = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("control"));
        if (controlIdAttributeValue == null)
            throw new OXFException("Missing mandatory 'control' attribute on xforms:control element.");

        final String resolvedControlId;
        {
            // Resolve AVT
            resolvedControlId = resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, controlIdAttributeValue, true);
            if (resolvedControlId == null)
                return;
        }

        final Object controlObject = resolveEffectiveControl(actionInterpreter, pipelineContext, eventObserver.getEffectiveId(), resolvedControlId, actionElement);
        if (controlObject instanceof XFormsControl) {
            // Dispatch event to control object
            containingDocument.dispatchEvent(pipelineContext, new XFormsFocusEvent((XFormsEventTarget) controlObject));
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:setfocus", "control does not refer to an existing control element, ignoring action",
                        new String[] { "control id", resolvedControlId } );
        }
    }
}
