package net.robinfriedli.botify.scripting;

import net.robinfriedli.botify.Botify;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.sc.StaticCompilationMetadataKeys;
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

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
        // declaring class is the Script itself or a class declared within the script, always allow those methods
        if (!declaringClass.isScript() && !declaringClass.isPrimaryClassNode()) {
            Class<?> type = declaringClass.getTypeClass();
            String methodName = target.getName();

            if (!groovyWhitelistManager.checkMethodCall(type, methodName)) {
                addStaticTypeError(String.format("Method invocation not allowed %s#%s", type.getSimpleName(), methodName), expression);
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

    }

}
