package net.robinfriedli.botify.scripting;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import net.robinfriedli.botify.Botify;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.transform.sc.transformers.CompareToNullExpression;
import org.codehaus.groovy.transform.stc.StaticTypesMarker;

/**
 * Transforms method calls for which an invocation limit is set (either using the "maxMethodInvocations" attribute
 * on {@code <whitelistMethods/>} or {@code <whitelistClass/>} elements or the "maxInvocationCount" attribute on
 * {@code <method/>} elements) wrapping the actual method calls into a Closure and passing them to
 * {@link RuntimeInvocationCountChecker#countedInvocation(Closure, Class, String)} which will count the invocations and
 * throw an exception at runtime if the limit has been reached.
 * <p>
 * Also manages a global limit of total method invocation counts and loop iterations using a ConditionalInterrupt combined
 * with the {@link #GLOBAL_COUNT_INCREMENTATION_CLOSURE}.
 * <p>
 * <p>
 * Transforms method pointers that cannot be checked at compile time to check them at runtime. Includes any method pointer
 * that does not consist of a ClassExpression / VariableExpression / PropertyExpression and ConstantExpression
 */
public class GroovyCompilationCustomizer extends CompilationCustomizer {

    private static final ClassNode CHECKER_CLASS = new ClassNode(RuntimeInvocationCountChecker.class);
    public static final ClosureExpression GLOBAL_COUNT_INCREMENTATION_CLOSURE = new ClosureExpression(
        new Parameter[0],
        new ExpressionStatement(
            new StaticMethodCallExpression(
                CHECKER_CLASS,
                "incrementGlobalCounter",
                ArgumentListExpression.EMPTY_ARGUMENTS
            )
        )
    );
    private static final int GLOBAL_INVOCATION_LIMIT = 10000;

    private final GroovyWhitelistManager groovyWhitelistManager;

    public GroovyCompilationCustomizer(GroovyWhitelistManager groovyWhitelistManager) {
        super(CompilePhase.INSTRUCTION_SELECTION);
        this.groovyWhitelistManager = groovyWhitelistManager;
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        ExpressionTransformer expressionTransformer = new ExpressionTransformer(groovyWhitelistManager, source);
        expressionTransformer.visitClass(classNode);

        // visit the new scopes for the closures
        VariableScopeVisitor variableScopeVisitor = new VariableScopeVisitor(source);
        variableScopeVisitor.visitClass(classNode);
        expressionTransformer.clearState();
    }

    private static class ExpressionTransformer extends ClassCodeExpressionTransformer {

        private final GroovyWhitelistManager groovyWhitelistManager;
        private final SourceUnit sourceUnit;

        private final LinkedList<VariableScope> scopeStack = new LinkedList<>();
        private final Set<ClosureExpression> createdClosures = new HashSet<>();

        private ExpressionTransformer(GroovyWhitelistManager groovyWhitelistManager, SourceUnit sourceUnit) {
            this.groovyWhitelistManager = groovyWhitelistManager;
            this.sourceUnit = sourceUnit;
        }

        void clearState() {
            scopeStack.clear();
            createdClosures.clear();
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit;
        }

        @Override
        public void visitMethod(MethodNode node) {
            scopeStack.push(node.getVariableScope());
            super.visitMethod(node);
            scopeStack.pop();
        }

        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            // don't visit our own closures, that would lead to wrapping target method calls multiple times leading to
            // multiple countedInvocation() invocations for the same method call
            if (!createdClosures.contains(expression)) {
                scopeStack.push(expression.getVariableScope());
                super.visitClosureExpression(expression);
                scopeStack.pop();
            }
        }

        @Override
        public void visitClosureListExpression(ClosureListExpression cle) {
            scopeStack.push(cle.getVariableScope());
            super.visitClosureListExpression(cle);
            scopeStack.pop();
        }

        @Override
        public void visitThrowStatement(ThrowStatement ts) {
            super.visitThrowStatement(ts);
        }

        @Override
        public Expression transform(Expression exp) {
            if (exp instanceof MethodPointerExpression) {
                return transformMethodPointerExpression((MethodPointerExpression) exp);
            } else {
                Expression expression = doTransform(exp);

                if (exp != expression) {
                    expression.setSourcePosition(exp);
                }

                return expression;
            }
        }

        private Expression doTransform(Expression exp) {
            if (exp instanceof MethodCallExpression) {
                MethodCallExpression methodCallExpression = (MethodCallExpression) exp;
                MethodNode methodTarget = methodCallExpression.getMethodTarget();

                if (methodTarget == null) {
                    throw new IllegalStateException("methodTarget is null for expression " + methodCallExpression);
                }

                return transformMethodCall(methodCallExpression, methodTarget.getDeclaringClass());
            } else if (exp instanceof ClosureExpression) {
                visitClosureExpression((ClosureExpression) exp);
            } else if (exp instanceof ConstructorCallExpression) {
                ConstructorCallExpression constructorCallExpression = (ConstructorCallExpression) exp;
                if (!constructorCallExpression.isSpecialCall()) {
                    return transformMethodCall(constructorCallExpression, constructorCallExpression.getType());
                }
            } else if (exp instanceof BinaryExpression) {
                BinaryExpression binaryExpression = (BinaryExpression) exp;
                Expression prevLeftExpression = binaryExpression.getLeftExpression();
                Expression newLeftExpression = transform(prevLeftExpression);
                binaryExpression.setLeftExpression(newLeftExpression);
                Expression prevRightExpression = binaryExpression.getRightExpression();
                Expression newRightExpression = transform(prevRightExpression);
                binaryExpression.setRightExpression(newRightExpression);

                if (exp instanceof CompareToNullExpression) {
                    CompareToNullExpression compareToNullExpression = (CompareToNullExpression) exp;
                    Expression objectExpression = compareToNullExpression.getObjectExpression();
                    if (objectExpression == prevLeftExpression) {
                        return new CompareToNullExpression(newLeftExpression, "==".equals(compareToNullExpression.getOperation().getText()));
                    } else if (objectExpression == prevRightExpression) {
                        return new CompareToNullExpression(newRightExpression, "==".equals(compareToNullExpression.getOperation().getText()));
                    }
                }
            }
            return super.transform(exp);
        }

        private Expression transformMethodCall(MethodCall methodCall, ClassNode declaringClass) {
            if (declaringClass == null) {
                throw new IllegalStateException("declaringClass node empty for methodCall " + methodCall);
            }

            VariableScope currentCope = scopeStack.peek();

            // declaring class is the Script itself or a class declared within the script, always allow those methods
            if (!declaringClass.isScript() && !declaringClass.isPrimaryClassNode()) {
                Class<?> type = declaringClass.getTypeClass();
                String methodName = methodCall.getMethodAsString();

                Collection<GroovyWhitelistManager.WhitelistedClassContribution> whitelistContributions = groovyWhitelistManager.findWhitelistContributions(type);
                for (GroovyWhitelistManager.WhitelistedClassContribution whitelistContribution : whitelistContributions) {
                    if (whitelistContribution.getMaxMethodInvocations() > 0) {
                        return createCountedInvocationCall(methodCall, currentCope, declaringClass, methodName);
                    } else {
                        GroovyWhitelistManager.WhitelistedMethodContribution methodContribution = whitelistContribution
                            .getWhitelistedMethodContributions()
                            .get(methodName);

                        if (methodContribution != null && methodContribution.getMaxInvocationCount() > 0) {
                            return createCountedInvocationCall(methodCall, currentCope, declaringClass, methodName);
                        }
                    }
                }
            }

            return super.transform((Expression) methodCall);
        }

        private StaticMethodCallExpression createCountedInvocationCall(
            MethodCall methodCall,
            VariableScope variableScope,
            ClassNode declaringClass,
            String methodName
        ) {
            if (methodCall instanceof MethodCallExpression) {
                MethodCallExpression methodCallExpression = (MethodCallExpression) methodCall;
                methodCallExpression.setObjectExpression(transform(methodCallExpression.getObjectExpression()));
            }

            VariableScope closureScope = new VariableScope(variableScope);
            ClosureExpression methodInvocationClosure = new ClosureExpression(
                new Parameter[0],
                new BlockStatement(
                    new Statement[]{
                        new ExpressionStatement((Expression) methodCall)
                    },
                    new VariableScope(closureScope)
                )
            );
            methodInvocationClosure.setVariableScope(closureScope);
            createdClosures.add(methodInvocationClosure);

            return new StaticMethodCallExpression(
                CHECKER_CLASS,
                "countedInvocation",
                new ArgumentListExpression(
                    methodInvocationClosure,
                    new ClassExpression(declaringClass),
                    new ConstantExpression(methodName)
                )
            );
        }

        private Expression transformMethodPointerExpression(MethodPointerExpression expression) {
            Expression expr = expression.getExpression();
            ClassNode classNode = TypeCheckingExtension.getClassNodeForExpression(expr);

            if (classNode != null) {
                if (classNode.isScript() || classNode.isPrimaryClassNode()) {
                    return expression;
                }

                String constantMethodName = TypeCheckingExtension.getMethodPointerConstantMethodName(expression);
                if (constantMethodName != null) {
                    // method name can be determined statically, thus already handled by TypeCheckingExtension
                    return expression;
                }

                VariableScope variableScope = scopeStack.peek();
                VariableScope closureScope = new VariableScope(variableScope);

                Expression methodNameExpression = expression.getMethodName();
                VariableExpression methodNameVariableExpression = new VariableExpression("__botify_methodName__");
                DeclarationExpression methodNameDeclarationExpression = new DeclarationExpression(methodNameVariableExpression, new Token(100, "=", -1, -1), methodNameExpression);

                // create the following statement
                // if (!(methodName instanceof String)) {
                //     throw new SecurityException('Method name expression of method pointer does not evaluate to boolean')
                // }
                BooleanExpression booleanExpression = new BooleanExpression(
                    new NotExpression(
                        new BinaryExpression(
                            methodNameVariableExpression,
                            new Token(544, "instanceof", -1, -1),
                            new ClassExpression(ClassHelper.STRING_TYPE)
                        )
                    )
                );
                ClassNode securityExceptionClass = ClassHelper.make(SecurityException.class);
                ConstructorNode securityExceptionConstructor = securityExceptionClass.getDeclaredConstructor(new Parameter[]{new Parameter(ClassHelper.STRING_TYPE, null)});
                ConstructorCallExpression securityExceptionConstructorCall = new ConstructorCallExpression(
                    securityExceptionClass,
                    new ArgumentListExpression(
                        new ConstantExpression("Method name expression of method pointer does not evaluate to string")
                    )
                );
                securityExceptionConstructorCall.setNodeMetaData(StaticTypesMarker.DIRECT_METHOD_CALL_TARGET, securityExceptionConstructor);
                ThrowStatement throwStatement = new ThrowStatement(securityExceptionConstructorCall);
                VariableScope ifStatementScope = new VariableScope(closureScope);
                IfStatement ifStatement = new IfStatement(booleanExpression, new BlockStatement(Lists.newArrayList(throwStatement), ifStatementScope), new BlockStatement());

                StaticMethodCallExpression checkWhitelistCallExpression = new StaticMethodCallExpression(
                    CHECKER_CLASS,
                    "checkMethodCall",
                    new ArgumentListExpression(
                        new ClassExpression(classNode),
                        methodNameVariableExpression
                    )
                );

                VariableExpression methodPointerVariable = new VariableExpression("__botify_methodPointer__");
                MethodPointerExpression methodPointerExpression = new MethodPointerExpression(new ClassExpression(classNode), methodNameVariableExpression);
                DeclarationExpression methodPointerDeclarationExpression = new DeclarationExpression(
                    methodPointerVariable,
                    new Token(100, "=", -1, -1),
                    methodPointerExpression
                );

                MethodCallExpression methodCallExpression = new MethodCallExpression(methodPointerVariable, "call", new ArgumentListExpression(new VariableExpression("it")));

                ClosureExpression closureExpression = new ClosureExpression(
                    new Parameter[0],
                    new BlockStatement(
                        new Statement[]{
                            new ExpressionStatement(methodNameDeclarationExpression),
                            ifStatement,
                            new ExpressionStatement(checkWhitelistCallExpression),
                            new ExpressionStatement(methodPointerDeclarationExpression),
                            new ExpressionStatement(methodCallExpression)
                        },
                        new VariableScope(closureScope)
                    )
                );
                createdClosures.add(closureExpression);
                closureExpression.setVariableScope(closureScope);
                return closureExpression;
            }

            return expression;
        }

    }

    public static class RuntimeInvocationCountChecker {

        // invoked by groovy
        @SuppressWarnings("unused")
        public static Object countedInvocation(
            Closure<?> methodInvocationClosure,
            Class<?> type,
            String methodName
        ) {
            GroovyWhitelistManager groovyWhitelistManager = Botify.get().getGroovySandboxComponent().getGroovyWhitelistManager();
            Collection<GroovyWhitelistManager.WhitelistedClassContribution> whitelistContributions = groovyWhitelistManager
                .findWhitelistContributions(type);

            for (GroovyWhitelistManager.WhitelistedClassContribution whitelistContribution : whitelistContributions) {
                whitelistContribution.incrementMethodInvocationCount();
                int maxMethodInvocations = whitelistContribution.getMaxMethodInvocations();

                if (maxMethodInvocations > 0 && whitelistContribution.getCurrentMethodInvocationCount() > maxMethodInvocations) {
                    throw new SecurityException(
                        String.format(
                            "Reached the maximum invocation count of %d for methods on %s",
                            maxMethodInvocations,
                            whitelistContribution.getType()
                        )
                    );
                }

                GroovyWhitelistManager.WhitelistedMethodContribution methodContribution = whitelistContribution
                    .getWhitelistedMethodContributions()
                    .get(methodName);

                if (methodContribution != null) {
                    methodContribution.incrementInvocationCount();
                    int maxInvocationCount = methodContribution.getMaxInvocationCount();

                    if (maxInvocationCount > 0 && methodContribution.getCurrentInvocationCount() > maxInvocationCount) {
                        throw new SecurityException(
                            String.format(
                                "Reached the maximum invocation count of %d for method %s",
                                maxInvocationCount,
                                methodContribution.getType().getName() + "#" + methodContribution.getMethod()
                            )
                        );
                    }
                }
            }

            return methodInvocationClosure.call();
        }

        // invoked by groovy
        @SuppressWarnings("unused")
        public static void checkMethodCall(Class<?> type, String methodName) {
            GroovyWhitelistManager groovyWhitelistManager = Botify.get().getGroovySandboxComponent().getGroovyWhitelistManager();

            if (!groovyWhitelistManager.checkMethodCall(type, methodName, false)) {
                throw new SecurityException(String.format("Method invocation not allowed: '%s#%s'", type.getSimpleName(), methodName));
            }
        }

        // invoked by groovy
        @SuppressWarnings("unused")
        public static void incrementGlobalCounter() {
            Integer prevVal = GroovyWhitelistManager.GLOBAL_INVOCATION_COUNT.get();

            if (prevVal >= GLOBAL_INVOCATION_LIMIT) {
                throw new SecurityException("Reached the global limit of method invocations and loop iterations of " + GLOBAL_INVOCATION_LIMIT);
            }

            GroovyWhitelistManager.GLOBAL_INVOCATION_COUNT.set(prevVal + 1);
        }

    }

}
