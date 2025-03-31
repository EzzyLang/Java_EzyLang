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
import java.util.Stack;

import io.github._3xhaust.ezylang.exception.ParseException;
import io.github._3xhaust.ezylang.lexer.Lexer;
import io.github._3xhaust.ezylang.lexer.Token;
import io.github._3xhaust.ezylang.parser.Parser;
import io.github._3xhaust.ezylang.parser.Parser.ArrayAccess;
import io.github._3xhaust.ezylang.parser.Parser.ArrayLiteral;
import io.github._3xhaust.ezylang.parser.Parser.AssignmentStatement;
import io.github._3xhaust.ezylang.parser.Parser.BinaryExpr;
import io.github._3xhaust.ezylang.parser.Parser.Block;
import io.github._3xhaust.ezylang.parser.Parser.BreakStatement;
import io.github._3xhaust.ezylang.parser.Parser.ConstantDecl;
import io.github._3xhaust.ezylang.parser.Parser.ContinueStatement;
import io.github._3xhaust.ezylang.parser.Parser.ExpressionStatement;
import io.github._3xhaust.ezylang.parser.Parser.ForStatement;
import io.github._3xhaust.ezylang.parser.Parser.FunctionCall;
import io.github._3xhaust.ezylang.parser.Parser.FunctionDecl;
import io.github._3xhaust.ezylang.parser.Parser.Identifier;
import io.github._3xhaust.ezylang.parser.Parser.IfStatement;
import io.github._3xhaust.ezylang.parser.Parser.ImportItem;
import io.github._3xhaust.ezylang.parser.Parser.ImportStatement;
import io.github._3xhaust.ezylang.parser.Parser.InterpolatedString;
import io.github._3xhaust.ezylang.parser.Parser.Literal;
import io.github._3xhaust.ezylang.parser.Parser.MethodCall;
import io.github._3xhaust.ezylang.parser.Parser.Node;
import io.github._3xhaust.ezylang.parser.Parser.PrintStatement;
import io.github._3xhaust.ezylang.parser.Parser.Program;
import io.github._3xhaust.ezylang.parser.Parser.SwitchCase;
import io.github._3xhaust.ezylang.parser.Parser.SwitchStatement;
import io.github._3xhaust.ezylang.parser.Parser.TypeCastExpr;
import io.github._3xhaust.ezylang.parser.Parser.TypeCheckExpr;
import io.github._3xhaust.ezylang.parser.Parser.UnaryExpr;
import io.github._3xhaust.ezylang.parser.Parser.VariableDecl;
import io.github._3xhaust.ezylang.parser.Parser.Visitor;
import io.github._3xhaust.ezylang.parser.Parser.WhileStatement;

public class Interpreter implements Visitor<Object> {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> constants = new HashMap<>();
    private final Map<String, FunctionDecl> functions = new HashMap<>();
    private final Map<String, NativeFunction> nativeFunctions = new HashMap<>();
    private final Map<String, Interpreter> loadedModules = new HashMap<>();
    private final String fileName;
    private final List<String> lines;
    private final Stack<Map<String, Object>> variableScopes = new Stack<>();
    private final Stack<Map<String, Object>> constantScopes = new Stack<>();

    private interface NativeFunction {
        Object execute(List<Object> args) throws ParseException;
    }

    public Interpreter(String fileName, String sourceCode) {
        this.fileName = fileName;
        this.lines = List.of(sourceCode.split("\n"));
        variableScopes.push(new HashMap<>());
        constantScopes.push(new HashMap<>());
        registerNativeFunctions();
    }

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
            Path currentDir = Paths.get(fileName).getParent();
            String moduleFileName = moduleName + ".ezy";
            Path modulePath = currentDir != null ? currentDir.resolve(moduleFileName) : Paths.get(moduleFileName);

            String sourceCode = new String(Files.readAllBytes(modulePath));
            Lexer lexer = new Lexer(sourceCode);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(modulePath.toString(), sourceCode, tokens);
            Program program = parser.parse();

            Interpreter moduleInterpreter = new Interpreter(modulePath.toString(), sourceCode);
            moduleInterpreter.interpret(program);
            loadedModules.put(moduleName, moduleInterpreter);
            return moduleInterpreter;
        } catch (Exception e) {
            throw new ParseException(fileName, "Failed to load module '" + moduleName + "': " + e.getMessage(), 1, 1, "");
        }
    }

    private void registerNativeFunctions() {
    }

    private void enterScope() {
        variableScopes.push(new HashMap<>());
        constantScopes.push(new HashMap<>());
    }

    private void exitScope() {
        if (!variableScopes.isEmpty()) variableScopes.pop();
        if (!constantScopes.isEmpty()) constantScopes.pop();
    }

    private Object findVariable(String name) {
        for (int i = variableScopes.size() - 1; i >= 0; i--) {
            if (variableScopes.get(i).containsKey(name)) {
                return variableScopes.get(i).get(name);
            }
        }
        return null;
    }

    private Object findConstant(String name) {
        for (int i = constantScopes.size() - 1; i >= 0; i--) {
            if (constantScopes.get(i).containsKey(name)) {
                return constantScopes.get(i).get(name);
            }
        }
        return null;
    }

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
        if (variableScopes.peek().containsKey(identifier)) {
            throw error(variableDecl, "Variable '" + identifier + "' is already defined in this scope");
        }
        if (variableDecl.getInitializer() != null) {
            value = variableDecl.getInitializer().accept(this);
        }
        variableScopes.peek().put(identifier, value);
        return null;
    }

    @Override
    public Object visitConstantDecl(ConstantDecl constantDecl) throws ParseException {
        String identifier = constantDecl.getIdentifier();
        if (constantScopes.peek().containsKey(identifier)) {
            throw error(constantDecl, "Constant '" + identifier + "' is already defined in this scope");
        }
        Object value = constantDecl.getValue().accept(this);
        constantScopes.peek().put(identifier, value);
        return null;
    }

    @Override
    public Object visitPrintStatement(PrintStatement printStatement) throws ParseException {
        Object result = printStatement.getExpression().accept(this);
        if (printStatement.isPrintln()) {
            System.out.println(result);
        } else {
            System.out.print(result);
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
                    yield String.valueOf(left) + right;
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
            System.out.println("Error: " + e.getMessage());
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
                variableScopes.peek().put(paramNames.get(i), value);
            }

            try {
                Object result = function.getBody().accept(this);
                return result;
            } catch (ReturnException returnEx) {
                return returnEx.getValue();
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
    public Object visitReturnStatement(Parser.ReturnStatement returnStatement) throws ParseException {
        Object value = returnStatement.getValue().accept(this);
        throw new ReturnException(value);
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
                    variableScopes.peek().put(identifier, element);
                    try {
                        forStatement.getBody().accept(this);
                    } catch (ContinueException ignored) {
                    }
                }
            } catch (BreakException ignored) {
            }

            if (initialValue != null) {
                variableScopes.peek().put(identifier, initialValue);
            } else {
                variableScopes.peek().remove(identifier);
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
                        variableScopes.peek().put(identifier, i);
                        try {
                            forStatement.getBody().accept(this);
                        } catch (ContinueException ignored) {
                        }
                    }
                } else {
                    for (double i = startValue; i >= endValue; i += stepValue) {
                        variableScopes.peek().put(identifier, i);
                        try {
                            forStatement.getBody().accept(this);
                        } catch (ContinueException ignored) {
                        }
                    }
                }
            } catch (BreakException ignored) {
            }

            if (initialValue != null) {
                variableScopes.peek().put(identifier, initialValue);
            } else {
                variableScopes.peek().remove(identifier);
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

        if (!(operand instanceof Double)) {
            throw error(unaryExpr.getOperand(), "Operand must be a number");
        }

        return switch (unaryExpr.getOperator().getToken()) {
            case MINUS -> -(Double) operand;
            case PLUS -> +(Double) operand;
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
                result.append(value);
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

            for (int i = variableScopes.size() - 1; i >= 0; i--) {
                if (variableScopes.get(i).containsKey(identifier)) {
                    variableScopes.get(i).put(identifier, newValue);
                    break;
                }
            }
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

        for (ImportItem item : items) {
            String name = item.getName();
            String alias = item.getAlias() != null ? item.getAlias() : name;

            if (name.equals("*")) {
                variableScopes.peek().putAll(module.variableScopes.get(0));
                constantScopes.peek().putAll(module.constantScopes.get(0));
                functions.putAll(module.functions);
            } else {
                if (module.variableScopes.get(0).containsKey(name)) {
                    variableScopes.peek().put(alias, module.variableScopes.get(0).get(name));
                } else if (module.constantScopes.get(0).containsKey(name)) {
                    constantScopes.peek().put(alias, module.constantScopes.get(0).get(name));
                } else if (module.functions.containsKey(name)) {
                    functions.put(alias, module.functions.get(name));
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
}