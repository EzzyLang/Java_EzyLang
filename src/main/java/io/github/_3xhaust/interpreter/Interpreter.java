package io.github._3xhaust.interpreter;

import io.github._3xhaust.ezylang.exception.ParseException;
import io.github._3xhaust.ezylang.lexer.Token;
import io.github._3xhaust.ezylang.parser.Parser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter implements Visitor<Object> {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> constants = new HashMap<>();
    private final String fileName;
    private final List<String> lines;

    public Interpreter(String fileName, String sourceCode) {
        this.fileName = fileName;
        this.lines = List.of(sourceCode.split("\n"));
    }

    public void interpret(Node node) throws ParseException {
        node.accept(this);
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
        Object value = null;
        if (variableDecl.getIdentifier() != null) {
            value = variableDecl.getInitializer().accept(this);
        }
        variables.put(variableDecl.getIdentifier(), value);
        return null;
    }

    @Override
    public Object visitConstantDecl(ConstantDecl constantDecl) throws ParseException {
        Object value = constantDecl.getValue().accept(this);
        constants.put(constantDecl.getIdentifier(), value);
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
        if (variables.containsKey(name)) return variables.get(name);
        if (constants.containsKey(name)) return constants.get(name);

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
        Object array = variables.get(arrayAccess.getIdentifier());
        if (array == null) {
            array = constants.get(arrayAccess.getIdentifier());
        }
        if (array == null) {
            throw error(arrayAccess, "Undefined variable '" + arrayAccess.getIdentifier() + "'");
        }
        if (!(array instanceof List<?> list)) {
            throw error(arrayAccess, "Variable '" + arrayAccess.getIdentifier() + "' is not an array");
        }
        if (!(index instanceof Double)) {
            throw error(arrayAccess, "Array index must be a number");
        }
        int idx = ((Double) index).intValue();
        if (idx < 0 || idx >= list.size()) {
            throw error(arrayAccess, "Array Index out of bounds");
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
    public Object visitSwitchCase(SwitchCase switchCase) throws ParseException {
        return switchCase.getBody().accept(this);
    }

    @Override
    public Object visitForStatement(ForStatement forStatement) throws ParseException {
        String identifier = forStatement.getIdentifier();
        Object iterable = forStatement.getStart().accept(this);

        if (iterable instanceof List<?> list) {
            Object initialValue = variables.get(identifier);
            try {
                for (Object element : list) {
                    variables.put(identifier, element);
                    try {
                        forStatement.getBody().accept(this);
                    } catch (ContinueException ignored) {
                    }
                }
            } catch (BreakException ignored) {
            }

            if (initialValue != null) {
                variables.put(identifier, initialValue);
            } else {
                variables.remove(identifier);
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

            Object initialValue = variables.get(identifier);
            try {
                if (stepValue > 0) {
                    for (double i = startValue; i <= endValue; i += stepValue) {
                        variables.put(identifier, i);
                        try {
                            forStatement.getBody().accept(this);
                        } catch (ContinueException ignored) {
                        }
                    }
                } else {
                    for (double i = startValue; i >= endValue; i += stepValue) {
                        variables.put(identifier, i);
                        try {
                            forStatement.getBody().accept(this);
                        } catch (ContinueException ignored) {
                        }
                    }
                }
            } catch (BreakException ignored) {
            }

            if (initialValue != null) {
                variables.put(identifier, initialValue);
            } else {
                variables.remove(identifier);
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
        Object result = null;
        for (Node statement : block.getStatements()) {
            result = statement.accept(this);
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
        String identifier = assignmentStatement.getIdentifier();
        Token operator = assignmentStatement.getOperator();
        Object value = assignmentStatement.getValue().accept(this);

        if (!variables.containsKey(identifier)) {
            if (constants.containsKey(identifier)) {
                throw error(assignmentStatement, "Cannot reassign to constant '" + identifier + "'");
            }
            throw error(assignmentStatement, "Undefined variable '" + identifier + "'");
        }

        Object currentValue = variables.get(identifier);
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

        variables.put(identifier, newValue);
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