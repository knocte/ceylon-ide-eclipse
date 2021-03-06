package com.redhat.ceylon.eclipse.core.debug.presentation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.internal.ui.DefaultLabelProvider;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.internal.debug.core.logicalstructures.JDIAllInstancesValue;
import org.eclipse.jdt.internal.debug.core.model.JDIClassType;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDILocalVariable;
import org.eclipse.jdt.internal.debug.core.model.JDINullValue;
import org.eclipse.jdt.internal.debug.core.model.JDIObjectValue;
import org.eclipse.jdt.internal.debug.core.model.JDIReferenceListValue;
import org.eclipse.jdt.internal.debug.ui.DebugUIMessages;
import org.eclipse.jdt.internal.debug.ui.JDIModelPresentation;
import org.eclipse.osgi.util.NLS;

import com.redhat.ceylon.common.JVMModuleUtil;
import com.redhat.ceylon.compiler.java.runtime.metamodel.Metamodel;
import com.redhat.ceylon.eclipse.core.debug.model.CeylonJDIDebugTarget;
import com.redhat.ceylon.eclipse.core.debug.model.CeylonJDIDebugTarget.EvaluationListener;
import com.redhat.ceylon.eclipse.core.debug.model.CeylonJDIDebugTarget.EvaluationRunner;

public class CeylonJDIModelPresentation extends JDIModelPresentation {
    private static final String ceylonStringTypeName = ceylon.language.String.class.getName();
    private static final String ceylonStringValueFieldName = "value";

    @Override
    public String getValueText(IJavaValue value) throws DebugException {
        if (!CeylonPresentationContext.isCeylonContext(value)) {
            return super.getValueText(value);
        }

        String refTypeName= value.getReferenceTypeName();
        String valueString= value.getValueString();
        boolean isString= refTypeName.equals(fgStringName);
        if (isString) {
            return super.getValueText(value);
        }
        
        if (refTypeName.equals(ceylonStringTypeName)) {
            isString = true;
            IJavaFieldVariable javaStringValueField = ((IJavaObject)value).getField(ceylonStringValueFieldName, false);
            if (javaStringValueField != null) {
                IValue javaStringValue = javaStringValueField.getValue();
                if (javaStringValue != null) {
                    valueString = javaStringValue.getValueString();
                }
            }
        }
        
        IJavaType type= value.getJavaType();
        String signature= null;
        if (type != null) {
            signature= type.getSignature();
        }

        if (!isObjectValue(signature)) {
            return super.getValueText(value);
        }
        
        boolean isArray= value instanceof IJavaArray;
        StringBuffer buffer= new StringBuffer();
        if (!isString && (refTypeName.length() > 0)) {
            // Don't show type name for instances and references
            if (!(value instanceof JDIReferenceListValue || value instanceof JDIAllInstancesValue)){
                String qualTypeName= getCeylonReifiedTypeName(value);
                if (isArray) {
                    qualTypeName= adjustTypeNameForArrayIndex(qualTypeName, ((IJavaArray)value).getLength());
                }
                buffer.append(qualTypeName);
                buffer.append(' ');
            }
        }
        
        // Put double quotes around Strings
        if (valueString != null && (isString || valueString.length() > 0)) {
            if (isString) {
                buffer.append('"');
            }
            buffer.append(DefaultLabelProvider.escapeSpecialChars(valueString));
            if (isString) {
                buffer.append('"');
                if(value instanceof IJavaObject){
                    buffer.append(" "); //$NON-NLS-1$
                    buffer.append(NLS.bind(DebugUIMessages.JDIModelPresentation_118, new String[]{String.valueOf(((IJavaObject)value).getUniqueId())})); 
                }
            }
            
        }
        return buffer.toString().trim();
    }

    private interface ProducedTypeAction<ReturnType extends IJavaValue> {
        ReturnType doOnProducedType(IJavaObject producedType, 
                IJavaThread innerThread, 
                IProgressMonitor monitor) throws DebugException;
    }
    
    @SuppressWarnings("unchecked")
    public static <ReturnType extends IJavaValue> ReturnType getFromReifiedType(IValue value, final ProducedTypeAction<ReturnType> postAction) {
        if (value instanceof JDINullValue) {
            return null;
        }
        if (value instanceof JDIObjectValue) {
            try {
                if (((IJavaReferenceType)((JDIObjectValue)value).getJavaType()).getName().endsWith("$impl")) {
                    IJavaFieldVariable thisField = ((JDIObjectValue) value).getField("$this", 0);
                    value = null;
                    if (thisField != null) {
                        IValue fieldValue = thisField.getValue();
                        if (fieldValue instanceof IJavaObject && 
                                !(fieldValue instanceof JDINullValue)) {
                            value = fieldValue;
                        }
                    }
                }
            } catch (DebugException e) {
                e.printStackTrace();
            }
            if (value != null) {
                final IJavaValue javaValue = (IJavaValue) value;
                final JDIDebugTarget debugTarget = ((JDIObjectValue) value).getJavaDebugTarget();
                if (debugTarget instanceof CeylonJDIDebugTarget) {
                    IJavaValue reifiedTypeInfo = ((CeylonJDIDebugTarget) debugTarget).getEvaluationResult(new EvaluationRunner() {
                        @Override
                        public void run(IJavaThread innerThread, IProgressMonitor monitor,
                                EvaluationListener listener) throws DebugException {
                            IJavaType[] types = ((JDIObjectValue) javaValue).getJavaDebugTarget().getJavaTypes(Metamodel.class.getName());
                            if (types != null && types.length > 0) {
                                JDIClassType metamodelType = (JDIClassType) types[0];
                                IJavaValue typeDescriptor = metamodelType.sendMessage("getTypeDescriptor", "(Ljava/lang/Object;)Lcom/redhat/ceylon/compiler/java/runtime/model/TypeDescriptor;", new IJavaValue[] {javaValue}, innerThread);
                                if (typeDescriptor instanceof IJavaObject && ! (typeDescriptor instanceof JDINullValue)) {
                                    IJavaValue producedType = metamodelType.sendMessage("getProducedType", "(Lcom/redhat/ceylon/compiler/java/runtime/model/TypeDescriptor;)Lcom/redhat/ceylon/compiler/typechecker/model/ProducedType;", new IJavaValue[] {typeDescriptor}, innerThread);
                                    if (producedType instanceof IJavaObject) {
                                        listener.finished(postAction.doOnProducedType((IJavaObject)producedType, innerThread, monitor));
                                        return;
                                    }
                                }
                            }
                            listener.finished(null);
                        }
                    }, 5000);
                    return (ReturnType)reifiedTypeInfo;
                }
            }
        }
        return null;
    }
    
    public String getCeylonReifiedTypeName(IValue value) throws DebugException {
        if (value instanceof JDINullValue) {
            return "Null";
        }
        IJavaValue reifiedTypeNameValue = getFromReifiedType(value, new ProducedTypeAction<IJavaValue>() {
            @Override
            public IJavaValue doOnProducedType(IJavaObject producedType,
                    IJavaThread innerThread, IProgressMonitor monitor)
            throws DebugException {
                if (producedType instanceof IJavaObject && ! (producedType instanceof JDINullValue)) {
                    IJavaValue producedTypeName = ((IJavaObject) producedType).sendMessage("getProducedTypeName", "()Ljava/lang/String;", new IJavaValue[] {}, innerThread, "Lcom/redhat/ceylon/compiler/typechecker/model/ProducedType;");
                    return producedTypeName;
                }
                return null;
            }
        });
        
        if (reifiedTypeNameValue instanceof JDIObjectValue  && !(reifiedTypeNameValue instanceof JDINullValue)) {
            String reifiedTypeName;
            reifiedTypeName = reifiedTypeNameValue.getValueString();
            return reifiedTypeName;
        }
        return getQualifiedName(value.getReferenceTypeName());
    }
    

    public static IJavaObject getCeylonReifiedType(IValue value) throws DebugException {
        return getFromReifiedType(value, new ProducedTypeAction<IJavaObject>() {
            @Override
            public IJavaObject doOnProducedType(IJavaObject producedType,
                    IJavaThread innerThread, IProgressMonitor monitor)
            throws DebugException {
                return producedType;
            }
        });
    }
    
    public static IJavaObject getCeylonDeclaration(IValue value) throws DebugException {
        IJavaObject producedType = getCeylonReifiedType(value);
        if (producedType != null) {
            IJavaFieldVariable fieldVariable = producedType.getField("declaration", true);
            if (fieldVariable != null) {
                IValue declValue = fieldVariable.getValue();
                if (declValue instanceof IJavaObject) {
                    return (IJavaObject) declValue;
                }
            }
        }
        return null;
    }
    
    
    @Override
    public String getVariableText(IJavaVariable var) {
        boolean isCeylonContext = CeylonPresentationContext.isCeylonContext(var);
        String varLabel= DebugUIMessages.JDIModelPresentation_unknown_name__1; 
        try {
            varLabel= var.getName();
            if (isCeylonContext) {
                varLabel = CeylonJDIModelPresentation.fixVariableName(varLabel, 
                        var instanceof JDILocalVariable,
                        var.isSynthetic());
            }
        } catch (DebugException exception) {
        }

        
        IJavaValue javaValue= null;
        try {
            javaValue = (IJavaValue) var.getValue();
        } catch (DebugException e1) {
        }
        boolean showTypes= isShowVariableTypeNames();
        StringBuffer buff= new StringBuffer();
        String typeName= DebugUIMessages.JDIModelPresentation_unknown_type__2; 
        try {
            typeName= var.getReferenceTypeName();
            if (isCeylonContext) {
                typeName = CeylonJDIModelPresentation.fixObjectTypeName(typeName);
            }
            if (showTypes) {
                typeName= getQualifiedName(typeName);
            }
        } catch (DebugException exception) {
        }
        if (showTypes) {
            buff.append(typeName);
            buff.append(' ');
        }
        buff.append(varLabel);

        // add declaring type name if required
        if (var instanceof IJavaFieldVariable) {
            IJavaFieldVariable field = (IJavaFieldVariable)var;
            if (isDuplicateName(field)) {
                try {
                    String decl = field.getDeclaringType().getName();
                    if (isCeylonContext) {
                        decl = CeylonJDIModelPresentation.fixObjectTypeName(decl);
                    }
                    buff.append(NLS.bind(" ({0})", new String[]{getQualifiedName(decl)})); //$NON-NLS-1$
                } catch (DebugException e) {
                }
            }
        }
        
        String valueString= getFormattedValueText(javaValue);

        //do not put the equal sign for array partitions
        if (valueString.length() != 0) {
            buff.append("= "); //$NON-NLS-1$
            buff.append(valueString);
        }
        return buff.toString();
    }

    
    private final static Pattern localVariablePattern = Pattern.compile("([^$]+)\\$[0-9]+");
    public static String fixVariableName(String name, boolean isLocalVariable, boolean isSynthetic) {
        if (isSynthetic 
                && name.startsWith("val$")) {
            name = name.substring(4);
        }
        if (name.charAt(0) == '$') {
            if (JVMModuleUtil.isJavaKeyword(name, 1, name.length())) {
                name = name.substring(1);
            }
        }
        if (isLocalVariable || isSynthetic 
                && name.contains("$")) {
            Matcher matcher = localVariablePattern.matcher(name);
            if (matcher.matches()) {
                name = matcher.group(1);
            }
        }
        
        return name;
    }

    static String fixObjectTypeName(String typeName)
            throws DebugException {
        int index = typeName.lastIndexOf('.');
        if (index > 0) {
            typeName = typeName.substring(index+1);
        }
        
        if (! Character.isUpperCase(typeName.charAt(0)) &&
                typeName.endsWith("_")) {
            typeName = typeName.substring(0, typeName.length() - 1);
        }
        return typeName;
    }
}
