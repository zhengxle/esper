/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.epl.enummethod.eval;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionField;
import com.espertech.esper.common.internal.context.module.EPStatementInitServices;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenParams;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluator;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventBean;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventType;
import com.espertech.esper.common.internal.event.core.EventTypeUtility;

import java.util.Collection;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class EnumSumScalarLambdaForgeEval implements EnumEval {

    private final EnumSumScalarLambdaForge forge;
    private final ExprEvaluator innerExpression;

    public EnumSumScalarLambdaForgeEval(EnumSumScalarLambdaForge forge, ExprEvaluator innerExpression) {
        this.forge = forge;
        this.innerExpression = innerExpression;
    }

    public Object evaluateEnumMethod(EventBean[] eventsLambda, Collection enumcoll, boolean isNewData, ExprEvaluatorContext context) {

        ExprDotEvalSumMethod method = forge.sumMethodFactory.getSumAggregator();

        ObjectArrayEventBean resultEvent = new ObjectArrayEventBean(new Object[1], forge.resultEventType);
        eventsLambda[forge.streamNumLambda] = resultEvent;
        Object[] props = resultEvent.getProperties();

        Collection<Object> values = (Collection<Object>) enumcoll;
        for (Object next : values) {
            props[0] = next;

            Object value = innerExpression.evaluate(eventsLambda, isNewData, context);
            method.enter(value);
        }

        return method.getValue();
    }

    public static CodegenExpression codegen(EnumSumScalarLambdaForge forge, EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {
        Class innerType = forge.innerExpression.getEvaluationType();
        CodegenExpressionField resultTypeMember = codegenClassScope.addFieldUnshared(true, ObjectArrayEventType.class, cast(ObjectArrayEventType.class, EventTypeUtility.resolveTypeCodegen(forge.resultEventType, EPStatementInitServices.REF)));

        ExprForgeCodegenSymbol scope = new ExprForgeCodegenSymbol(false, null);
        CodegenMethod methodNode = codegenMethodScope.makeChildWithScope(forge.sumMethodFactory.getValueType(), EnumSumEventsForgeEval.class, scope, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMS);

        CodegenBlock block = methodNode.getBlock();
        forge.sumMethodFactory.codegenDeclare(block);
        block.declareVar(ObjectArrayEventBean.class, "resultEvent", newInstance(ObjectArrayEventBean.class, newArrayByLength(Object.class, constant(1)), resultTypeMember))
                .assignArrayElement(EnumForgeCodegenNames.REF_EPS, constant(forge.streamNumLambda), ref("resultEvent"))
                .declareVar(Object[].class, "props", exprDotMethod(ref("resultEvent"), "getProperties"));

        CodegenBlock forEach = block.forEach(Object.class, "next", EnumForgeCodegenNames.REF_ENUMCOLL)
                .assignArrayElement("props", constant(0), ref("next"))
                .declareVar(innerType, "value", forge.innerExpression.evaluateCodegen(innerType, methodNode, scope, codegenClassScope));
        if (!innerType.isPrimitive()) {
            forEach.ifRefNull("value").blockContinue();
        }
        forge.sumMethodFactory.codegenEnterNumberTypedNonNull(forEach, ref("value"));

        forge.sumMethodFactory.codegenReturn(block);
        return localMethod(methodNode, args.getEps(), args.getEnumcoll(), args.getIsNewData(), args.getExprCtx());
    }
}
