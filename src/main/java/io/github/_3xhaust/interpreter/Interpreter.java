package io.github._3xhaust.interpreter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github._3xhaust.ezylang.ast.Ast.*;
import io.github._3xhaust.ezylang.exception.ParseException;
import io.github._3xhaust.ezylang.lexer.Lexer;
import io.github._3xhaust.ezylang.lexer.Token;
import io.github._3xhaust.ezylang.parser.Parser;
import io.github._3xhaust.interpreter.module.ArrModule;
import io.github._3xhaust.interpreter.module.MathModule;
import io.github._3xhaust.interpreter.module.NativeFunction;
import io.github._3xhaust.interpreter.module.StrModule;

public class Interpreter implements Visitor<Object> {
    private final Map<String, FunctionDecl> functions = new HashMap<>();
    private final Map<String, NativeFunction> nativeFunctions = new HashMap<>();
    private final Map<String, Interpreter> loadedModules = new HashMap<>();
    private static final java.util.Set<String> BUILTIN_MODULES = java.util.Set.of("math", "str", "arr");
    private final String fileName;
    private final List<String> lines;
    private final Environment env = new Environment();
    private final Map<String, Map<List<Object>, Object>> memoCache = new HashMap<>();
    private final java.util.Set<String> importedModules = new java.util.HashSet<>();
    private static final Double[] DOUBLE_CACHE = new Double[256];
    static { for (int i = 0; i < 256; i++) DOUBLE_CACHE[i] = (double) i; }
    private boolean testMode = false;
    private int testsPassed = 0;
    private int testsFailed = 0;

    public Interpreter(String fileName, String sourceCode) {
        this.fileName = fileName;
        this.lines = List.of(sourceCode.split("\n"));
        registerNativeFunctions();
    }

    public void setTestMode(boolean testMode) { this.testMode = testMode; }
    public int getTestsPassed() { return testsPassed; }
    public int getTestsFailed() { return testsFailed; }

    public void interpret(Program program) throws ParseException {
        for (Node statement : program.getStatements()) {
            if (statement instanceof FunctionDecl) {
                statement.accept(this);
            }
        }

        for (Node statement : program.getStatements()) {
            if (!(statement instanceof FunctionDecl)) {
                statement.accept(this);
            }
        }
    }

    private Interpreter loadModule(String moduleName) throws ParseException {
        if (loadedModules.containsKey(moduleName)) {
            return loadedModules.get(moduleName);
        }

        try {
            String moduleFileName = moduleName + ".ezy";
            String sourceCode;
            String modulePath;

            Path currentDir = Paths.get(fileName).getParent();
            Path localPath = currentDir != null ? currentDir.resolve(moduleFileName) : Paths.get(moduleFileName);

            if (Files.exists(localPath)) {
                sourceCode = new String(Files.readAllBytes(localPath));
                modulePath = localPath.toString();
            } else {
                java.io.InputStream is = getClass().getResourceAsStream("/" + moduleFileName);
                if (is == null) throw new Exception("Module '" + moduleName + "' not found");
                sourceCode = new String(is.readAllBytes());
                is.close();
                modulePath = moduleFileName;
            }

            Lexer lexer = new Lexer(sourceCode);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(modulePath, sourceCode, tokens);
            Program program = parser.parse();

            Interpreter moduleInterpreter = new Interpreter(modulePath, sourceCode);
            registerModuleNatives(moduleName, moduleInterpreter);
            moduleInterpreter.interpret(program);
            loadedModules.put(moduleName, moduleInterpreter);
            return moduleInterpreter;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException(fileName, "Failed to load module '" + moduleName + "': " + e.getMessage(), 1, 1, "");
        }
    }

    private void registerNativeFunctions() {
    }

    private void registerModuleNatives(String moduleName, Interpreter moduleInterpreter) {
        switch (moduleName) {
            case "math" -> {
                MathModule.register(moduleInterpreter.nativeFunctions);
                MathModule.registerConstants(moduleInterpreter.env.getGlobalConstantScope());
            }
            case "str" -> StrModule.register(moduleInterpreter.nativeFunctions);
            case "arr" -> ArrModule.register(moduleInterpreter.nativeFunctions);
        }
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf(d.longValue());
            }
        }
        return String.valueOf(value);
    }

    private static Double cachedDouble(double v) {
        int i = (int) v;
        if (i == v && i >= 0 && i < 256) return DOUBLE_CACHE[i];
        return v;
    }

    private void enterScope() { env.enterScope(); }
    private void exitScope() { env.exitScope(); }
    private Object findVariable(String name) { return env.findVariable(name); }
    private Object findConstant(String name) { return env.findConstant(name); }
    private void setVariable(String name, Object value) { env.setVariable(name, value); }
    private void setConstant(String name, Object value) { env.setConstant(name, value); }

    @Override
    public Object visitProgram(Program program) throws ParseException {
        Object result = null;
        for (Node statement : program.getStatements()) {
            result = statement.accept(this);
        }
        return result;
    }

    @Override
    public Object visitVariableDecl(VariableDecl variableDecl) throws ParseException {
        String identifier = variableDecl.getIdentifier();
        Object value = null;
        if (env.hasVariableInCurrentScope(identifier)) {
            throw error(variableDecl, "Variable '" + identifier + "' is already defined in this scope");
        }
        if (variableDecl.getInitializer() != null) {
            value = variableDecl.getInitializer().accept(this);
        }

        if (variableDecl.getConstraint() != null) {
            Constraint c = variableDecl.getConstraint();
            if (c.isRange()) {
                double min = (Double) c.getMin().accept(this);
                double max = (Double) c.getMax().accept(this);
                env.setConstraint(identifier, new double[]{min, max});
                if (value != null) validateConstraint(identifier, value, variableDecl);
            } else if (c.isEnum()) {
                List<Object> allowed = new ArrayList<>();
                for (Node node : c.getAllowedValues()) {
                    allowed.add(node.accept(this));
                }
                env.setConstraint(identifier, allowed);
                if (value != null) validateConstraint(identifier, value, variableDecl);
            }
        }

        setVariable(identifier, value);
        return null;
    }

    private void validateConstraint(String varName, Object value, Node errorNode) throws ParseException {
        Object constraint = env.findConstraint(varName);
        if (constraint == null) return;

        if (constraint instanceof double[] range) {
            if (!(value instanceof Double)) throw error(errorNode, "Expected number for constrained variable '" + varName + "'");
            double v = (Double) value;
            if (v < range[0] || v > range[1]) {
                throw error(errorNode, "Value " + v + " is out of range " + range[0] + ".." + range[1] + " for variable '" + varName + "'");
            }
        } else if (constraint instanceof List<?> allowedValues) {
            if (!allowedValues.contains(value)) {
                throw error(errorNode, "Value '" + value + "' is not allowed for variable '" + varName + "'. Allowed: " + allowedValues);
            }
        }
    }

    @Override
    public Object visitConstantDecl(ConstantDecl constantDecl) throws ParseException {
        String identifier = constantDecl.getIdentifier();
        if (env.hasConstantInCurrentScope(identifier)) {
            throw error(constantDecl, "Constant '" + identifier + "' is already defined in this scope");
        }
        Object value = constantDecl.getValue().accept(this);
        setConstant(identifier, value);
        return null;
    }

    @Override
    public Object visitPrintStatement(PrintStatement printStatement) throws ParseException {
        Object result = printStatement.getExpression().accept(this);
        String output = formatValue(result);
        if (printStatement.isPrintln()) {
            System.out.println(output);
        } else {
            System.out.print(output);
        }
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr binaryExpr) throws ParseException {
        Object left = binaryExpr.getLeft().accept(this);
        Object right = binaryExpr.getRight().accept(this);

        return switch (binaryExpr.getOperator().getToken()) {
            case PLUS -> {
                if (left instanceof String || right instanceof String) {
                    yield formatValue(left) + formatValue(right);
                }
                if (left instanceof Double && right instanceof Double) {
                    yield (Double) left + (Double) right;
                }
                throw error(binaryExpr, "Operands must be two numbers or two strings");
            }
            case MINUS -> (Double) left - (Double) right;
            case ASTERISK -> (Double) left * (Double) right;
            case SLASH -> (Double) left / (Double) right;
            case PERCENT -> (Double) left % (Double) right;
            case EQUAL_EQUAL -> {
                if (left == null && right == null) yield true;
                if (left == null || right == null) yield false;
                yield left.equals(right);
            }
            case NOT_EQUAL -> {
                if (left == null && right == null) yield false;
                if (left == null || right == null) yield true;
                yield !left.equals(right);
            }
            case LESS_THAN -> (Double) left < (Double) right;
            case GREATER_THAN -> (Double) left > (Double) right;
            case LESS_THAN_OR_EQUAL -> (Double) left <= (Double) right;
            case GREATER_THAN_OR_EQUAL -> (Double) left >= (Double) right;
            case AND -> (Boolean) left && (Boolean) right;
            case OR -> (Boolean) left || (Boolean) right;
            default -> throw error(binaryExpr, "Unknown binary operator: " + binaryExpr.getOperator());
        };
    }

    @Override
    public Object visitLiteral(Literal literal) {
        return literal.getValue();
    }

    @Override
    public Object visitIdentifier(Identifier identifier) throws ParseException {
        String name = identifier.getName();
        Object value = findVariable(name);
        if (value != null) return value;
        value = findConstant(name);
        if (value != null) return value;

        try {
            return Double.parseDouble(name);
        } catch (NumberFormatException e) {
            throw error(identifier, "Undefined variable '" + name + "'");
        }
    }

    @Override
    public Object visitArrayAccess(ArrayAccess arrayAccess) throws ParseException {
        Object index = arrayAccess.getIndex().accept(this);
        String identifier = arrayAccess.getIdentifier();
        Object array = findVariable(identifier);
        if (array == null) {
            array = findConstant(identifier);
        }
        if (array == null) {
            throw error(arrayAccess, "Undefined variable '" + identifier + "'");
        }
        if (!(array instanceof List<?> list)) {
            throw error(arrayAccess, "Variable '" + identifier + "' is not an array");
        }
        if (!(index instanceof Double)) {
            throw error(arrayAccess, "Array index must be a number");
        }
        int idx = ((Double) index).intValue();
        if (idx < 0 || idx >= list.size()) {
            throw error(arrayAccess, "Array index out of bounds");
        }
        return list.get(idx);
    }

    @Override
    public Object visitIfStatement(IfStatement ifStatement) throws ParseException {
        Object condition = ifStatement.getCondition().accept(this);

        if (!(condition instanceof Boolean)) {
            throw error(ifStatement.getCondition(), "Condition must be a boolean expression");
        }

        if ((Boolean) condition) {
            return ifStatement.getThenBranch().accept(this);
        } else if (ifStatement.getElseBranch() != null) {
            return ifStatement.getElseBranch().accept(this);
        }

        return null;
    }

    @Override
    public Object visitSwitchStatement(SwitchStatement switchStatement) throws ParseException {
        Object switchExpr = switchStatement.getExpression().accept(this);
        boolean shouldExecute = false;
        boolean hasMatched = false;

        try {
            for (SwitchCase caseStmt : switchStatement.getCases()) {
                Object caseValue = caseStmt.getValue().accept(this);

                if (shouldExecute || switchExpr.equals(caseValue)) {
                    hasMatched = true;
                    shouldExecute = true;

                    try {
                        caseStmt.getBody().accept(this);
                    } catch (BreakException e) {
                        shouldExecute = false;
                        break;
                    }

                    if (caseStmt.isArrowStyle()) {
                        shouldExecute = false;
                        break;
                    }
                }
            }

            if ((!hasMatched || shouldExecute) && switchStatement.getDefaultCase() != null) {
                switchStatement.getDefaultCase().accept(this);
            }

        } catch (BreakException ignored) {
        }

        return null;
    }

    @Override
    public Object visitFunctionDecl(FunctionDecl functionDecl) throws ParseException {
        String functionName = functionDecl.getName();
        if (functions.containsKey(functionName)) {
            throw error(functionDecl, "Function '" + functionName + "' is already defined");
        }
        functions.put(functionDecl.getName(), functionDecl);
        return null;
    }

    @Override
    public Object visitFunctionCall(FunctionCall functionCall) throws ParseException {
        String name = functionCall.getName();
        FunctionDecl function = functions.get(name);
        if (function == null) {
            NativeFunction nativeFunction = nativeFunctions.get(name);
            if (nativeFunction != null) {
                List<Object> args = new ArrayList<>();
                for (Node arg : functionCall.getArguments()) {
                    args.add(arg.accept(this));
                }
                return nativeFunction.execute(args);
            }
            throw error(functionCall, "Undefined function '" + name + "'");
        }

        List<Node> arguments = functionCall.getArguments();
        List<String> paramNames = function.getParamNames();
        List<Token> paramTypes = function.getParamTypes();
        List<Boolean> isArrayTypes = function.getIsArrayTypes();

        if (arguments.size() != paramNames.size()) {
            throw error(functionCall, "Expected " + paramNames.size() + " arguments but got " + arguments.size());
        }

        List<Object> evaluatedArgs = new ArrayList<>();
        for (Node arg : arguments) {
            evaluatedArgs.add(arg.accept(this));
        }

        if (function.isMemo()) {
            Map<List<Object>, Object> cache = memoCache.computeIfAbsent(name, k -> new HashMap<>());
            if (cache.containsKey(evaluatedArgs)) {
                return cache.get(evaluatedArgs);
            }
        }

        enterScope();
        try {
            for (int i = 0; i < paramNames.size(); i++) {
                Object value = evaluatedArgs.get(i);
                String expectedType = paramTypes.get(i).getValue();
                boolean isArray = isArrayTypes.get(i);

                if (isArray && !(value instanceof List<?>)) {
                    throw error(functionCall, "Expected '" + expectedType + "[]' but got '" + getTypeName(value) + "'");
                } else if (!isArray && value instanceof List<?>) {
                    throw error(functionCall, "Expected '" + expectedType + "' but got array");
                } else if (!isArray && !expectedType.equals(getTypeName(value))) {
                    throw error(functionCall, "Expected '" + expectedType + "' but got '" + getTypeName(value) + "'");
                }
                setVariable(paramNames.get(i), value);
            }

            try {
                Object result = function.getBody().accept(this);
                if (function.isMemo()) memoCache.get(name).put(evaluatedArgs, result);
                return result;
            } catch (ReturnException returnEx) {
                Object value = returnEx.getValue();
                if (function.isMemo()) memoCache.get(name).put(evaluatedArgs, value);
                return value;
            }
        } finally {
            exitScope();
        }
    }

    private static class ReturnException extends RuntimeException {
        private final Object value;

        ReturnException(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    @Override
    public Object visitReturnStatement(ReturnStatement returnStatement) throws ParseException {
        Object value = returnStatement.getValue().accept(this);
        throw new ReturnException(value);
    }

    @Override
    public Object visitIncrementDecrementExpr(IncrementDecrementExpr expr) throws ParseException {
        String varName;
        boolean isArrayAccess = false;
        int index = -1;

        if (expr.getOperand() instanceof Identifier) {
            varName = ((Identifier) expr.getOperand()).getName();
        } else if (expr.getOperand() instanceof ArrayAccess) {
            ArrayAccess arrayAccess = (ArrayAccess) expr.getOperand();
            varName = arrayAccess.getIdentifier();
            isArrayAccess = true;

            Object indexValue = arrayAccess.getIndex().accept(this);
            if (!(indexValue instanceof Double)) {
                throw error(expr, "Array index must be a number");
            }
            index = ((Double) indexValue).intValue();
        } else {
            throw error(expr, "Invalid operand for increment/decrement operator");
        }

        if (!isArrayAccess && findConstant(varName) != null) {
            throw error(expr, "Cannot increment/decrement constant '" + varName + "'");
        }

        Object currentValue;
        if (isArrayAccess) {
            Object arrayObj = findVariable(varName);
            if (arrayObj == null) {
                arrayObj = findConstant(varName);
                if (arrayObj != null) {
                    throw error(expr, "Cannot modify constant array '" + varName + "'");
                } else {
                    throw error(expr, "Undefined array '" + varName + "'");
                }
            }

            if (!(arrayObj instanceof List)) {
                throw error(expr, "Variable '" + varName + "' is not an array");
            }

            List<Object> array = (List<Object>) arrayObj;

            if (index < 0 || index >= array.size()) {
                throw error(expr, "Array index out of bounds: " + index);
            }

            currentValue = array.get(index);
        } else {
            currentValue = findVariable(varName);
            if (currentValue == null) {
                throw error(expr, "Undefined variable '" + varName + "'");
            }
        }

        if (!(currentValue instanceof Number)) {
            throw error(expr, "Cannot increment/decrement non-numeric value");
        }

        double value = ((Number) currentValue).doubleValue();
        double newValue;

        if (expr.getOperator().getToken() == Token.TokenType.PLUS_PLUS) {
            newValue = value + 1;
        } else {
            newValue = value - 1;
        }

        if (!isArrayAccess) validateConstraint(varName, newValue, expr);

        if (isArrayAccess) {
            List<Object> array = (List<Object>) findVariable(varName);
            array.set(index, newValue);
        } else {
            env.updateVariable(varName, newValue);
        }

        return expr.isPrefix() ? newValue : value;
    }

    private String getTypeName(Object value) {
        if (value instanceof Double) return "number";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List<?>) return "array";
        return "unknown";
    }

    @Override
    public Object visitMethodCall(MethodCall methodCall) throws ParseException {
        String objectName = methodCall.getObjectName();
        String methodName = methodCall.getMethodName();
        List<Node> arguments = methodCall.getArguments();
        Node objectNode = methodCall.getObjectNode();

        if (objectName != null && importedModules.contains(objectName)) {
            Interpreter module = loadedModules.get(objectName);
            if (module == null) {
                throw error(methodCall, "Module '" + objectName + "' not loaded");
            }
            if (methodCall.isPropertyAccess()) {
                Object constant = module.findConstant(methodName);
                if (constant != null) return constant;
                throw error(methodCall, "'" + methodName + "' not found in module '" + objectName + "'");
            }
            List<Object> args = new ArrayList<>();
            for (Node arg : arguments) {
                args.add(arg.accept(this));
            }
            NativeFunction nf = module.nativeFunctions.get(methodName);
            if (nf != null) return nf.execute(args);
            FunctionDecl func = module.functions.get(methodName);
            if (func != null) {
                module.enterScope();
                try {
                    List<String> paramNames = func.getParamNames();
                    for (int i = 0; i < paramNames.size(); i++) {
                        module.setVariable(paramNames.get(i), args.get(i));
                    }
                    try {
                        return func.getBody().accept(module);
                    } catch (ReturnException e) {
                        return e.getValue();
                    }
                } finally {
                    module.exitScope();
                }
            }
            Object constant = module.findConstant(methodName);
            if (constant != null) return constant;
            throw error(methodCall, "'" + methodName + "' not found in module '" + objectName + "'");
        }

        Object object;
        if (objectName != null) {
            object = findVariable(objectName);
            if (object == null) {
                throw error(methodCall, "Undefined variable '" + objectName + "'");
            }
        } else if (objectNode != null) {
            object = objectNode.accept(this);
        } else {
            throw error(methodCall, "No object specified for method call");
        }

        if (methodName.equals("length") && (object instanceof List<?> || object instanceof String)) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'length' does not take any arguments");
            }
            return (double) (object instanceof List<?> list ? list.size() : ((String) object).length());
        }
        if (methodName.equals("repeat") && object instanceof String) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'repeat' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            if (!(arg instanceof Double)) {
                throw error(methodCall, "Argument must be a number");
            }
            int count = ((Double) arg).intValue();
            return String.valueOf(object).repeat(count);
        }
        if (methodName.equals("charAt") && object instanceof String) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'charAt' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            if (!(arg instanceof Double)) {
                throw error(methodCall, "Argument must be a number");
            }
            int index = ((Double) arg).intValue();
            if (index < 0 || index >= ((String) object).length()) {
                throw error(methodCall, "Index out of bounds");
            }
            return ((String) object).charAt(index);
        }
        if (methodName.equals("contains") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'contains' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            return ((List<?>) object).contains(arg);
        }
        if (methodName.equals("indexOf") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'indexOf' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            return (double) ((List<?>) object).indexOf(arg);
        }
        if (methodName.equals("lastIndexOf") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'lastIndexOf' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            return (double) ((List<?>) object).lastIndexOf(arg);
        }
        if (methodName.equals("isEmpty") && object instanceof List<?>) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'isEmpty' does not take any arguments");
            }
            return ((List<?>) object).isEmpty();
        }
        if (methodName.equals("isNotEmpty") && object instanceof List<?>) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'isNotEmpty' does not take any arguments");
            }
            return !((List<?>) object).isEmpty();
        }
        if (methodName.equals("clear") && object instanceof List<?>) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'clear' does not take any arguments");
            }
            ((List<?>) object).clear();
            return null;
        }
        if (methodName.equals("addAll") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'addAll' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            if (!(arg instanceof List<?>)) {
                throw error(methodCall, "Argument must be an array");
            }
            ((List<Object>) object).addAll((List<?>) arg);
            return null;
        }
        if (methodName.equals("remove") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'remove' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            return ((List<?>) object).remove(arg);
        }
        if (methodName.equals("removeAt") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'removeAt' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            if (!(arg instanceof Double)) {
                throw error(methodCall, "Argument must be a number");
            }
            int index = ((Double) arg).intValue();
            if (index < 0 || index >= ((List<?>) object).size()) {
                throw error(methodCall, "Index out of bounds");
            }
            return ((List<?>) object).remove(index);
        }
        if (methodName.equals("reverse") && object instanceof List<?>) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'reverse' does not take any arguments");
            }
            Collections.reverse((List<?>) object);
            return null;
        }
        if (methodName.equals("sort") && object instanceof List<?>) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'sort' does not take any arguments");
            }
            if (((List<?>) object).isEmpty()) {
                return null;
            }
            if (((List<?>) object).get(0) instanceof Double) {
                Collections.sort((List<Double>) object);
            } else if (((List<?>) object).get(0) instanceof String) {
                Collections.sort((List<String>) object);
            } else {
                throw error(methodCall, "Cannot sort array of this type");
            }
            return null;
        }
        if (methodName.equals("shuffle") && object instanceof List<?>) {
            if (!arguments.isEmpty()) {
                throw error(methodCall, "Method 'shuffle' does not take any arguments");
            }
            Collections.shuffle((List<?>) object);
            return null;
        }
        if (methodName.equals("join") && object instanceof List<?>) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'join' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            if (!(arg instanceof String)) {
                throw error(methodCall, "Argument must be a string");
            }
            return String.join((String) arg, (List<String>) object);
        }
        if (methodName.equals("split") && object instanceof String) {
            if (arguments.size() != 1) {
                throw error(methodCall, "Method 'split' takes exactly one argument");
            }
            Object arg = arguments.get(0).accept(this);
            if (!(arg instanceof String)) {
                throw error(methodCall, "Argument must be a string");
            }
            return Arrays.asList(((String) object).split((String) arg));
        }

        throw error(methodCall, "Undefined method '" + methodName + "'");
    }

    @Override
    public Object visitSwitchCase(SwitchCase switchCase) throws ParseException {
        return switchCase.getBody().accept(this);
    }

    @Override
    public Object visitForStatement(ForStatement forStatement) throws ParseException {
        String identifier = forStatement.getIdentifier();
        Object iterable = forStatement.getStart().accept(this);

        if (iterable instanceof List<?> list) {
            Object initialValue = findVariable(identifier);
            try {
                for (Object element : list) {
                    setVariable(identifier, element);
                    try {
                        forStatement.getBody().accept(this);
                    } catch (ContinueException ignored) {
                    }
                }
            } catch (BreakException ignored) {
            }

            if (initialValue != null) {
                setVariable(identifier, initialValue);
            } else {
                env.removeVariable(identifier);
            }
        } else if (iterable instanceof Double) {
            double startValue = (Double) iterable;
            Object end = forStatement.getEnd().accept(this);
            Object step = forStatement.getStep() != null ? forStatement.getStep().accept(this) : 1.0;

            double endValue = (Double) end;
            double stepValue = (Double) step;

            if (stepValue <= 0) {
                throw error(forStatement.getStep(), "Step must be positive, got: " + stepValue);
            }

            Object initialValue = findVariable(identifier);
            try {
                if (stepValue > 0) {
                    for (double i = startValue; i <= endValue; i += stepValue) {
                        setVariable(identifier, cachedDouble(i));
                        try {
                            forStatement.getBody().accept(this);
                        } catch (ContinueException ignored) {
                        }
                    }
                } else {
                    for (double i = startValue; i >= endValue; i += stepValue) {
                        setVariable(identifier, cachedDouble(i));
                        try {
                            forStatement.getBody().accept(this);
                        } catch (ContinueException ignored) {
                        }
                    }
                }
            } catch (BreakException ignored) {
            }

            if (initialValue != null) {
                setVariable(identifier, initialValue);
            } else {
                env.removeVariable(identifier);
            }
        }

        return null;
    }

    @Override
    public Object visitArrayLiteral(ArrayLiteral arrayLiteral) throws ParseException {
        List<Node> elements = arrayLiteral.getElements();
        List<Object> evaluatedElements = new ArrayList<>();

        for (Node element : elements) {
            Object evaluated = element.accept(this);
            evaluatedElements.add(evaluated);
        }

        return evaluatedElements;
    }

    @Override
    public Object visitBlock(Block block) throws ParseException {
        enterScope();
        Object result = null;
        try {
            for (Node statement : block.getStatements()) {
                result = statement.accept(this);
            }
        } finally {
            exitScope();
        }
        return result;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr unaryExpr) throws ParseException {
        Object operand = unaryExpr.getOperand().accept(this);

        return switch (unaryExpr.getOperator().getToken()) {
            case MINUS -> {
                if (!(operand instanceof Double)) throw error(unaryExpr.getOperand(), "Operand must be a number");
                yield -(Double) operand;
            }
            case PLUS -> {
                if (!(operand instanceof Double)) throw error(unaryExpr.getOperand(), "Operand must be a number");
                yield +(Double) operand;
            }
            case BANG -> {
                if (!(operand instanceof Boolean)) throw error(unaryExpr.getOperand(), "Operand must be a boolean");
                yield !(Boolean) operand;
            }
            default -> throw error(unaryExpr, "Unknown unary operator: " + unaryExpr.getOperator());
        };
    }

    @Override
    public Object visitInterpolatedString(InterpolatedString interpolatedString) throws ParseException {
        StringBuilder result = new StringBuilder();

        List<Node> expressions = interpolatedString.getExpressions();
        String rawString = interpolatedString.getRawString();
        int expressionIndex = 0;

        for (int i = 0; i < rawString.length(); i++) {
            if (rawString.charAt(i) == '$' && rawString.charAt(i + 1) == '{') {
                while (rawString.charAt(i) != '}') {
                    i++;
                }
                Object value = expressions.get(expressionIndex++).accept(this);
                result.append(formatValue(value));
            } else {
                result.append(rawString.charAt(i));
            }
        }

        return result.toString();
    }

    @Override
    public Object visitWhileStatement(WhileStatement whileStatement) throws ParseException {
        try {
            while (true) {
                Object condition = whileStatement.getCondition().accept(this);

                if (!(condition instanceof Boolean)) {
                    throw error(whileStatement.getCondition(), "Condition must be a boolean expression");
                }

                if (!(Boolean) condition) {
                    break;
                }

                try {
                    whileStatement.getBody().accept(this);
                } catch (ContinueException ignored) {
                }
            }
        } catch (BreakException ignored) {
        }

        return null;
    }

    @Override
    public Object visitAssignmentStatement(AssignmentStatement assignmentStatement) throws ParseException {
        Node target = assignmentStatement.getTarget();
        Token operator = assignmentStatement.getOperator();
        Object value = assignmentStatement.getValue().accept(this);

        if (target instanceof Identifier identifierNode) {
            String identifier = identifierNode.getName();
            if (findConstant(identifier) != null) {
                throw error(assignmentStatement, "Cannot reassign to constant '" + identifier + "'");
            }
            if (findVariable(identifier) == null) {
                throw error(assignmentStatement, "Undefined variable '" + identifier + "'");
            }

            Object currentValue = findVariable(identifier);
            Object newValue;

            switch (operator.getToken()) {
                case EQUAL -> newValue = value;
                case PLUS_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        newValue = (Double) currentValue + (Double) value;
                    } else if (currentValue instanceof String || value instanceof String) {
                        newValue = String.valueOf(currentValue) + value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '+=' operator");
                    }
                }
                case MINUS_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        newValue = (Double) currentValue - (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '-=' operator");
                    }
                }
                case ASTERISK_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        newValue = (Double) currentValue * (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '*=' operator");
                    }
                }
                case SLASH_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        if ((Double) value == 0) {
                            throw error(assignmentStatement, "Division by zero");
                        }
                        newValue = (Double) currentValue / (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '/=' operator");
                    }
                }
                case PERCENT_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        if ((Double) value == 0) {
                            throw error(assignmentStatement, "Modulo by zero");
                        }
                        newValue = (Double) currentValue % (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '%=' operator");
                    }
                }
                default -> throw error(assignmentStatement, "Invalid assignment operator");
            }

            validateConstraint(identifier, newValue, assignmentStatement);

            env.updateVariable(identifier, newValue);
        } else if (target instanceof ArrayAccess arrayAccess) {
            String identifier = arrayAccess.getIdentifier();
            Object indexObj = arrayAccess.getIndex().accept(this);
            Boolean isConstant = findConstant(identifier) != null;
            Object array = isConstant ? findConstant(identifier) : findVariable(identifier);
            if (array == null) {
                throw error(assignmentStatement, "Undefined array '" + identifier + "'");
            }
            if (isConstant) {
                throw error(assignmentStatement, "Cannot reassign to constant array '" + identifier + "'");
            }
            if (!(array instanceof List<?> list)) {
                throw error(assignmentStatement, "Variable '" + identifier + "' is not an array");
            }
            if (!(indexObj instanceof Double)) {
                throw error(assignmentStatement, "Array index must be a number");
            }
            int index = ((Double) indexObj).intValue();
            if (index < 0 || index >= list.size()) {
                throw error(assignmentStatement, "Array index out of bounds");
            }

            Object currentValue = list.get(index);
            Object newValue;

            switch (operator.getToken()) {
                case EQUAL -> newValue = value;
                case PLUS_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        newValue = (Double) currentValue + (Double) value;
                    } else if (currentValue instanceof String || value instanceof String) {
                        newValue = String.valueOf(currentValue) + value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '+=' operator");
                    }
                }
                case MINUS_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        newValue = (Double) currentValue - (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '-=' operator");
                    }
                }
                case ASTERISK_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        newValue = (Double) currentValue * (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '*=' operator");
                    }
                }
                case SLASH_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        if ((Double) value == 0) {
                            throw error(assignmentStatement, "Division by zero");
                        }
                        newValue = (Double) currentValue / (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '/=' operator");
                    }
                }
                case PERCENT_EQUAL -> {
                    if (currentValue instanceof Double && value instanceof Double) {
                        if ((Double) value == 0) {
                            throw error(assignmentStatement, "Modulo by zero");
                        }
                        newValue = (Double) currentValue % (Double) value;
                    } else {
                        throw error(assignmentStatement, "Invalid operands for '%=' operator");
                    }
                }
                default -> throw error(assignmentStatement, "Invalid assignment operator");
            }

            ((List<Object>) array).set(index, newValue);
        } else {
            throw error(assignmentStatement, "Invalid assignment target");
        }

        return null;
    }

    @Override
    public Object visitExpressionStatement(ExpressionStatement expressionStatement) throws ParseException {
        Object result = expressionStatement.getExpression().accept(this);
        return result;
    }

    @Override
    public Object visitImportStatement(ImportStatement importStatement) throws ParseException {
        String moduleName = importStatement.getModuleName();
        Interpreter module = loadModule(moduleName);
        List<ImportItem> items = importStatement.getItems();

        if (items.isEmpty()) {
            importedModules.add(moduleName);
            return null;
        }

        for (ImportItem item : items) {
            String name = item.getName();
            String alias = item.getAlias() != null ? item.getAlias() : name;

            if (name.equals("*")) {
                Map<String, Object> currentVars = env.getTopVariableScope();
                for (Map.Entry<String, Object> entry : module.env.getGlobalVariableScope().entrySet()) {
                    if (currentVars.containsKey(entry.getKey()) || functions.containsKey(entry.getKey())) {
                        throw error(importStatement, "Name conflict: '" + entry.getKey() + "' is already defined. Use 'as' alias to resolve: from " + moduleName + " import " + entry.getKey() + " as <alias>");
                    }
                    currentVars.put(entry.getKey(), entry.getValue());
                }
                Map<String, Object> currentConsts = env.getTopConstantScope();
                for (Map.Entry<String, Object> entry : module.env.getGlobalConstantScope().entrySet()) {
                    if (currentConsts.containsKey(entry.getKey())) {
                        throw error(importStatement, "Name conflict: '" + entry.getKey() + "' is already defined. Use 'as' alias to resolve: from " + moduleName + " import $" + entry.getKey() + " as <alias>");
                    }
                    currentConsts.put(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, FunctionDecl> entry : module.functions.entrySet()) {
                    if (functions.containsKey(entry.getKey()) || nativeFunctions.containsKey(entry.getKey())) {
                        throw error(importStatement, "Name conflict: '" + entry.getKey() + "' is already defined. Use 'as' alias to resolve: from " + moduleName + " import " + entry.getKey() + " as <alias>");
                    }
                    functions.put(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, NativeFunction> entry : module.nativeFunctions.entrySet()) {
                    if (nativeFunctions.containsKey(entry.getKey()) || functions.containsKey(entry.getKey())) {
                        throw error(importStatement, "Name conflict: '" + entry.getKey() + "' is already imported. Use 'as' alias to resolve: from " + moduleName + " import " + entry.getKey() + " as <alias>");
                    }
                    nativeFunctions.put(entry.getKey(), entry.getValue());
                }
            } else {
                if (env.hasVariableInCurrentScope(alias) || env.hasConstantInCurrentScope(alias) || functions.containsKey(alias) || nativeFunctions.containsKey(alias)) {
                    throw error(importStatement, "Name conflict: '" + alias + "' is already defined. Use 'as' alias to resolve: from " + moduleName + " import " + name + " as <alias>");
                }
                if (module.env.getGlobalVariableScope().containsKey(name)) {
                    setVariable(alias, module.env.getGlobalVariableScope().get(name));
                } else if (module.env.getGlobalConstantScope().containsKey(name)) {
                    setConstant(alias, module.env.getGlobalConstantScope().get(name));
                } else if (module.functions.containsKey(name)) {
                    functions.put(alias, module.functions.get(name));
                } else if (module.nativeFunctions.containsKey(name)) {
                    nativeFunctions.put(alias, module.nativeFunctions.get(name));
                } else {
                    throw error(importStatement, "Item '" + name + "' not found in module '" + moduleName + "'");
                }
            }
        }

        return null;
    }

    @Override
    public Object visitTypeCheckExpr(TypeCheckExpr expr) throws ParseException {
        Object value = expr.getExpression().accept(this);
        String targetType = expr.getType().getValue();

        return switch (targetType.toLowerCase()) {
            case "number" -> value instanceof Double;
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "char" -> value instanceof Character;
            case "array" -> value instanceof List;
            case "null" -> value == null;
            default -> throw error(expr, "Unknown type: " + targetType);
        };
    }

    @Override
    public Object visitTypeCastExpr(TypeCastExpr expr) throws ParseException {
        Object value = expr.getExpression().accept(this);
        String targetType = expr.getTargetType().getValue();

        try {
            return switch (targetType.toLowerCase()) {
                case "number" -> {
                    if (value instanceof String str) {
                        yield Double.parseDouble(str);
                    } else if (value instanceof Character c) {
                        yield (double) c;
                    } else if (value instanceof Boolean b) {
                        yield b ? 1.0 : 0.0;
                    }
                    throw error(expr, "Cannot cast " + value.getClass().getSimpleName() + " to number");
                }
                case "string" -> String.valueOf(value);
                case "boolean" -> {
                    if (value instanceof String str) {
                        yield Boolean.parseBoolean(str);
                    } else if (value instanceof Double d) {
                        yield d != 0;
                    }
                    throw error(expr, "Cannot cast " + value.getClass().getSimpleName() + " to boolean");
                }
                case "char" -> {
                    if (value instanceof String str && str.length() == 1) {
                        yield str.charAt(0);
                    } else if (value instanceof Double d) {
                        yield (char) d.intValue();
                    }
                    throw error(expr, "Cannot cast " + value.getClass().getSimpleName() + " to char");
                }
                default -> throw error(expr, "Unknown type: " + targetType);
            };
        } catch (Exception e) {
            throw error(expr, "Cast failed: " + e.getMessage());
        }
    }

    private ParseException error(Node node, String message) {
        int line = node.getLine();
        int column = node.getColumn();
        String errorLine = getErrorLine(line);

        return new ParseException(
                fileName,
                message,
                line,
                column,
                errorLine
        );
    }

    private String getErrorLine(int line) {
        if (line <= 0 || line > lines.size()) {
            return "";
        }
        return lines.get(line - 1);
    }

    @Override
    public Object visitBreakStatement(BreakStatement breakStatement) throws ParseException {
        throw new BreakException(fileName, breakStatement);
    }

    @Override
    public Object visitContinueStatement(ContinueStatement continueStatement) throws ParseException {
        throw new ContinueException(fileName, continueStatement);
    }

    private static class BreakException extends ParseException {
        BreakException(String fileName, BreakStatement breakStatement) {
            super(fileName, "Break statement outside of loop", breakStatement.getLine(), breakStatement.getColumn(), "");

        }
    }

    private static class ContinueException extends ParseException {
        ContinueException(String fileName, ContinueStatement continueStatement) {
            super(fileName, "Continue statement outside of loop", continueStatement.getLine(), continueStatement.getColumn(), "");
        }
    }

    private static class AssertionFailedException extends ParseException {
        AssertionFailedException(String fileName, String message, int line, int column, String errorLine) {
            super(fileName, message, line, column, errorLine);
        }
    }

    @Override
    public Object visitTestBlock(TestBlock testBlock) throws ParseException {
        if (!testMode) return null;

        try {
            enterScope();
            testBlock.getBody().accept(this);
            exitScope();
            testsPassed++;
            System.out.println("[PASS] " + testBlock.getName());
        } catch (AssertionFailedException e) {
            exitScope();
            testsFailed++;
            System.out.println("[FAIL] " + testBlock.getName() + " - " + e.getMessage());
        }
        return null;
    }

    @Override
    public Object visitAssertStatement(AssertStatement assertStatement) throws ParseException {
        Object result = assertStatement.getExpression().accept(this);
        if (!(result instanceof Boolean) || !(Boolean) result) {
            throw new AssertionFailedException(
                    fileName,
                    "Assertion failed at line " + assertStatement.getLine(),
                    assertStatement.getLine(),
                    assertStatement.getColumn(),
                    getErrorLine(assertStatement.getLine())
            );
        }
        return null;
    }
}