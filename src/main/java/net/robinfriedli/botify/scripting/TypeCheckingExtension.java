package net.robinfriedli.botify.scripting;

import javax.annotation.Nullable;

import net.robinfriedli.botify.Botify;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.sc.StaticCompilationMetadataKeys;
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;
import org.codehaus.groovy.transform.stc.StaticTypesMarker;

// invoked by groovy
@SuppressWarnings("unused")
public class TypeCheckingExtension extends AbstractTypeCheckingExtension {

    private GroovyVariableManager groovyVariableManager;
    private GroovyWhitelistManager groovyWhitelistManager;

    public TypeCheckingExtension(StaticTypeCheckingVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor);
    }

    @Override
    public void onMethodSelection(Expression expression, MethodNode target) {
        initGroovyWhitelistManager();
        ClassNode declaringClass = target.getDeclaringClass();
        boolean isGenerated = !(expression.getColumnNumber() > 0 && expression.getLineNumber() > 0);
        // declaring class is the Script itself or a class declared within the script, always allow those methods
        if (!declaringClass.isScript() && !declaringClass.isPrimaryClassNode()) {
            Class<?> type = declaringClass.getTypeClass();
            String methodName = target.getName();

            if (!groovyWhitelistManager.checkMethodCall(type, methodName, isGenerated)) {
                if (isGenerated) {
                    // groovy ignores static type errors for certain generated method calls, even if 'groovy.stc.debug'
                    // is enabled, throw exception immediately
                    throw new SecurityException(String.format("Forbidden usage of generated method: %s#%s", type.getSimpleName(), methodName));
                } else {
                    addStaticTypeError(String.format("Method invocation not allowed %s#%s", type.getSimpleName(), methodName), expression);
                }
            }
        }
        super.onMethodSelection(expression, target);
    }

    @Override
    public boolean handleUnresolvedVariableExpression(VariableExpression vexp) {
        initGroovyVariableManager();
        Object variable = groovyVariableManager.getVariable(vexp.getName());
        if (variable != null) {
            storeType(vexp, ClassHelper.make(variable.getClass()));
            setHandled(true);
            return true;
        }
        return super.handleUnresolvedVariableExpression(vexp);
    }

    @Override
    public boolean beforeVisitMethod(MethodNode node) {
        forbidFinalizeOverride(node);
        return super.beforeVisitMethod(node);
    }

    @Override
    public void afterVisitMethod(MethodNode node) {
        ExpressionChecker expressionChecker = new ExpressionChecker(context.getSource());
        expressionChecker.visitMethod(node);
        super.afterVisitMethod(node);
    }

    @Override
    public boolean beforeVisitClass(ClassNode node) {
        for (MethodNode method : node.getMethods()) {
            forbidFinalizeOverride(method);
        }
        return super.beforeVisitClass(node);
    }

    @Override
    public void afterVisitClass(ClassNode node) {
        ExpressionChecker expressionChecker = new ExpressionChecker(context.getSource());
        expressionChecker.visitClass(node);
        super.afterVisitClass(node);
    }

    private void initGroovyVariableManager() {
        if (groovyVariableManager == null) {
            groovyVariableManager = Botify.get().getGroovyVariableManager();
        }
    }

    private void initGroovyWhitelistManager() {
        if (groovyWhitelistManager == null) {
            groovyWhitelistManager = Botify.get().getGroovySandboxComponent().getGroovyWhitelistManager();
        }
    }

    private void forbidFinalizeOverride(MethodNode methodNode) {
        if (methodNode.getName().equals("finalize") && methodNode.getParameters().length == 0) {
            addStaticTypeError("Overriding finalize() method is not allowed", methodNode);
        }
    }

    private class ExpressionChecker extends ClassCodeVisitorSupport {

        private final SourceUnit sourceUnit;

        private ExpressionChecker(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit;
        }

        @Override
        public void visitBinaryExpression(BinaryExpression expression) {
            Expression leftExpression = expression.getLeftExpression();
            if (leftExpression instanceof PropertyExpression && Types.ofType(expression.getOperation().getType(), Types.ASSIGNMENT_OPERATOR)) {
                PropertyExpression propertyExpression = (PropertyExpression) leftExpression;
                Expression objectExpression = propertyExpression.getObjectExpression();
                Object propertyOwner = objectExpression.getNodeMetaData(StaticCompilationMetadataKeys.PROPERTY_OWNER);

                if (propertyOwner instanceof ClassNode) {
                    ClassNode ownerType = (ClassNode) propertyOwner;

                    if (!ownerType.isScript() && !ownerType.isPrimaryClassNode()) {
                        Class<?> typeClass = ownerType.getTypeClass();
                        String property = propertyExpression.getPropertyAsString();

                        if (!groovyWhitelistManager.checkPropertyWriteAccess(typeClass, property)) {
                            addStaticTypeError(String.format("Property write access is not allowed: %s.%s", typeClass.getSimpleName(), property), expression);
                        }
                    }
                } else {
                    addStaticTypeError("Could not identify owner of property", expression);
                }
            }
            super.visitBinaryExpression(expression);
        }

        @Override
        public void visitMethodPointerExpression(MethodPointerExpression expression) {
            Expression expr = expression.getExpression();

            ClassNode classNode = getClassNodeForExpression(expr);

            if (classNode != null && !classNode.isScript() && !classNode.isPrimaryClassNode()) {
                Class<?> typeClass = classNode.getTypeClass();

                String methodName = getMethodPointerConstantMethodName(expression);
                if (methodName != null) {
                    boolean isGenerated = !(expression.getColumnNumber() > 0 && expression.getLineNumber() > 0);

                    if (!groovyWhitelistManager.checkMethodCall(typeClass, methodName, isGenerated)) {
                        addStaticTypeError(String.format("Method pointer not allowed %s#%s", typeClass.getSimpleName(), methodName), expression);
                    }
                }
            } else if (classNode == null) {
                addStaticTypeError("Could not determine target class for MethodPointerExpression", expression);
            }

            super.visitMethodPointerExpression(expression);
        }
    }

    @Nullable
    static ClassNode getClassNodeForExpression(Expression expr) {
        if (expr instanceof ClassExpression) {
            ClassExpression classExpression = (ClassExpression) expr;
            return classExpression.getType();
        } else if (expr instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) expr;
            Object inferredReturnType = variableExpression.getMetaDataMap().get(StaticTypesMarker.INFERRED_RETURN_TYPE);
            if (inferredReturnType instanceof ClassNode) {
                return (ClassNode) inferredReturnType;
            }
        } else if (expr instanceof PropertyExpression) {
            PropertyExpression propertyExpression = (PropertyExpression) expr;
            Object inferredType = propertyExpression.getMetaDataMap().get(StaticTypesMarker.INFERRED_TYPE);

            if (inferredType instanceof ClassNode) {
                return (ClassNode) inferredType;
            }
        }

        return null;
    }

    @Nullable
    static String getMethodPointerConstantMethodName(MethodPointerExpression expression) {
        Expression methodNameExpression = expression.getMethodName();

        if (methodNameExpression instanceof ConstantExpression) {
            Object value = ((ConstantExpression) methodNameExpression).getValue();
            if (value instanceof String) {
                boolean isGenerated = !(expression.getColumnNumber() > 0 && expression.getLineNumber() > 0);

                return (String) value;
            }
        }

        return null;
    }

}
