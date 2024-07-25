package io.github._3xhaust.parser;

import io.github._3xhaust.exception.ParseException;
import io.github._3xhaust.token.Token;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private int position = 0;
    private final Map<String, Object> variables = new HashMap<>();
    private final Set<String> constants = new HashSet<>();
    private final Set<String> declaredVariables = new HashSet<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        tokens.forEach(token -> System.out.println(token.getToken()));
    }

    public void parse() {
        try {
            while (!currentPosition().getToken().equals(Token.EOF)) {
                statement();
            }
        } catch (ParseException e) {
            System.err.println(e.getFormattedMessage());
        }
    }

    private void statement() throws ParseException {
        switch (currentPosition().getToken()) {
            case Token.PRINT -> printStatement();
            case Token.IF -> ifStatement();
            case Token.IDENTIFIER, Token.DOLLAR -> {
                if (peek(1).getToken().equals(Token.EQUAL)) {
                    variableAssignment();
                } else {
                    variableDeclaration();
                }
            }
            default -> throw new ParseException("Syntax error: Unexpected token '" + currentPosition().getValue() + "'", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private Token peek(int offset) {
        if (position + offset >= tokens.size()) return new Token(Token.EOF, null, currentPosition().getLine(), currentPosition().getColumn());
        return tokens.get(position + offset);
    }

    private void variableDeclaration() throws ParseException {
        boolean isConstant = false;
        if (currentPosition().getToken().equals(Token.DOLLAR)) {
            isConstant = true;
            consume(Token.DOLLAR);
        }

        String variableName = consume(Token.IDENTIFIER).getValue();

        if (declaredVariables.contains(variableName)) {
            throw new ParseException("Variable '" + variableName + "' already declared", currentPosition().getLine(), currentPosition().getColumn());
        }

        consume(Token.COLON);
        String type = currentPosition().getToken();
        position++;

        Object value;

        if (currentPosition().getToken().equals(Token.EQUAL)) {
            consume(Token.EQUAL);
            try {
                value = switch (type) {
                    case Token.NUMBER -> expressionNumber();
                    case Token.STRING -> expressionString();
                    case Token.BOOLEAN -> expressionBoolean();
                    case Token.CHAR -> expressionChar();
                    case Token.NULL -> null;
                    default -> throw new ParseException("Unsupported type: " + type.toLowerCase(), currentPosition().getLine(), currentPosition().getColumn());
                };
            } catch (ParseException e) {
                throw new ParseException("Type mismatch: Cannot assign " + currentPosition().getToken().toLowerCase() + " to " + type.toLowerCase(), currentPosition().getLine(), currentPosition().getColumn());
            }
        }else throw new ParseException("Variable declaration must include initialization", currentPosition().getLine(), currentPosition().getColumn());

        if (isConstant) {
            constants.add(variableName);
        }

        variables.put(variableName, value);
        declaredVariables.add(variableName);
    }

    private void variableAssignment() throws ParseException {
        String variableName = consume(Token.IDENTIFIER).getValue();
        consume(Token.EQUAL);

        if (!variables.containsKey(variableName)) {
            throw new ParseException("Undefined variable: " + variableName, currentPosition().getLine(), currentPosition().getColumn());
        }

        if (constants.contains(variableName)) {
            throw new ParseException("Cannot reassign constant variable: " + variableName, currentPosition().getLine(), currentPosition().getColumn());
        }

        Object value = expression();
        variables.put(variableName, value);
    }

    private void printStatement() throws ParseException {
        consume(Token.PRINT);
        consume(Token.LEFT_PAREN);

        do {
            if (currentPosition().getToken().equals(Token.VARIABLE_LITERAL)) {
                String variableName = consume(Token.VARIABLE_LITERAL).getValue();
                if (!variables.containsKey(variableName)) {
                    throw new ParseException("Undefined variable: " + variableName, currentPosition().getLine(), currentPosition().getColumn());
                }
                System.out.print(variables.get(variableName));
            } else {
                Object result = expression();
                System.out.print(result);
            }
        } while (currentPosition().getToken().equals(Token.VARIABLE_LITERAL) ||
                currentPosition().getToken().equals(Token.STRING_LITERAL));

        System.out.println();
        consume(Token.RIGHT_PAREN);
    }

    private void ifStatement() throws ParseException {
        boolean conditionMet = false;

        consume(Token.IF);
        consume(Token.LEFT_PAREN);
        boolean condition = evaluateCondition();
        consume(Token.RIGHT_PAREN);

        if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
            consume(Token.LEFT_BRACE);
            if (condition) {
                block();
                conditionMet = true;
            } else {
                skipBlock();
            }
            consume(Token.RIGHT_BRACE);
        } else {
            if (condition) {
                statement();
                conditionMet = true;
            } else {
                skipStatement();
            }
        }

        handleElseIf(conditionMet);
        handleElse(conditionMet);
    }

    private void handleElseIf(boolean conditionMet) throws ParseException {
        while (currentPosition().getToken().equals(Token.ELSE_IF)) {
            consume(Token.ELSE_IF);
            consume(Token.LEFT_PAREN);
            boolean condition = evaluateCondition();
            consume(Token.RIGHT_PAREN);

            if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
                consume(Token.LEFT_BRACE);
                if (!conditionMet && condition) {
                    block();
                    conditionMet = true;
                } else {
                    skipBlock();
                }
                consume(Token.RIGHT_BRACE);
            } else {
                if (!conditionMet && condition) {
                    statement();
                    conditionMet = true;
                } else {
                    skipStatement();
                }
            }
        }
    }

    private void handleElse(boolean conditionMet) throws ParseException {
        if (currentPosition().getToken().equals(Token.ELSE)) {
            consume(Token.ELSE);

            if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
                consume(Token.LEFT_BRACE);
                if (!conditionMet) {
                    block();
                } else {
                    skipBlock();
                }
                consume(Token.RIGHT_BRACE);
            } else {
                if (!conditionMet) {
                    statement();
                } else {
                    skipStatement();
                }
            }
        }
    }

    private boolean evaluateCondition() throws ParseException {
        if (currentPosition().getToken().equals(Token.BOOLEAN_LITERAL)) {
            return Boolean.parseBoolean(consume(Token.BOOLEAN_LITERAL).getValue());
        } else {
            return evaluateExpression();
        }
    }

    private void skipStatement() {
        while (!isAtEnd() && !currentPosition().getToken().equals(Token.SEMICOLON)) {
            position++;
        }
        if (!isAtEnd()) {
            position++;
        }
    }

    private void block() throws ParseException {
        while (!currentPosition().getToken().equals(Token.RIGHT_BRACE) && !isAtEnd()) {
            statement();
        }
    }

    private void skipBlock() {
        int braceCount = 1;
        while (braceCount > 0 && !isAtEnd()) {
            if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
                braceCount++;
            } else if (currentPosition().getToken().equals(Token.RIGHT_BRACE)) {
                braceCount--;
            }
            position++;
        }
        position--;
    }

    private boolean isAtEnd() {
        return position >= tokens.size();
    }

    private Object convertType(Object value, String targetType) throws ParseException {
        if (value == null) return null;

        return switch (targetType) {
            case Token.NUMBER -> convertToNumber(value);
            case Token.STRING -> value.toString();
            case Token.BOOLEAN -> convertToBoolean(value);
            case Token.CHAR -> convertToChar(value);
            case Token.NULL -> null;
            default -> throw new ParseException("Cannot convert " + value.getClass().getSimpleName() + " to " + targetType, currentPosition().getLine(), currentPosition().getColumn());
        };
    }

    private BigDecimal convertToNumber(Object value) throws ParseException {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof String) return new BigDecimal((String) value);
        if (value instanceof Boolean) return (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
        if (value instanceof Character) return new BigDecimal((int) (Character) value);
        throw new ParseException("Cannot convert " + value.getClass().getSimpleName() + " to number", currentPosition().getLine(), currentPosition().getColumn());
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof BigDecimal) return ((BigDecimal) value).compareTo(BigDecimal.ZERO) != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof Character) return ((Character) value) != '\0';
        return false;
    }

    private Character convertToChar(Object value) throws ParseException {
        if (value instanceof Character) return (Character) value;
        if (value instanceof String && ((String) value).length() == 1) return ((String) value).charAt(0);
        if (value instanceof BigDecimal) {
            int intValue = ((BigDecimal) value).intValue();
            if (intValue >= 0 && intValue <= 65535) return (char) intValue;
        }
        throw new ParseException("Cannot convert " + value.getClass().getSimpleName() + " to char", currentPosition().getLine(), currentPosition().getColumn());
    }

    private BigDecimal expressionNumber() throws ParseException {
        return logicalOrExpression();
    }

    private String expressionString() throws ParseException {
        if (currentPosition().getToken().equals(Token.NUMBER_LITERAL) ||
                currentPosition().getToken().equals(Token.BOOLEAN_LITERAL) ||
                currentPosition().getToken().equals(Token.CHAR_LITERAL)) {
            throw new ParseException("Type mismatch: Cannot convert " + currentPosition().getToken() + " to string", currentPosition().getLine(), currentPosition().getColumn());
        }

        StringBuilder result = new StringBuilder();
        while (currentPosition().getToken().equals(Token.STRING_LITERAL) || currentPosition().getToken().equals(Token.VARIABLE_LITERAL)) {
            if (currentPosition().getToken().equals(Token.STRING_LITERAL)) {
                result.append(consume(Token.STRING_LITERAL).getValue());
            } else if (currentPosition().getToken().equals(Token.VARIABLE_LITERAL)) {
                String variableName = consume(Token.VARIABLE_LITERAL).getValue();
                if (!variables.containsKey(variableName)) {
                    throw new ParseException("Undefined variable: " + variableName, currentPosition().getLine(), currentPosition().getColumn());
                }
                result.append(variables.get(variableName).toString());
            }
        }
        return result.toString();
    }

    private Boolean expressionBoolean() throws ParseException {
        if (currentPosition().getToken().equals(Token.NUMBER_LITERAL) ||
                currentPosition().getToken().equals(Token.STRING_LITERAL) ||
                currentPosition().getToken().equals(Token.CHAR_LITERAL)) {
            throw new ParseException("Type mismatch: Cannot convert " + currentPosition().getToken() + " to boolean", currentPosition().getLine(), currentPosition().getColumn());
        }

        return Boolean.parseBoolean(consume(Token.BOOLEAN_LITERAL).getValue());
    }

    private Character expressionChar() throws ParseException {
        return consume(Token.CHAR_LITERAL).getValue().charAt(0);
    }

    private BigDecimal logicalOrExpression() throws ParseException {
        BigDecimal left = logicalAndExpression();
        while (currentPosition().getToken().equals(Token.OR)) {
            position++;
            BigDecimal right = logicalAndExpression();
            left = (left.compareTo(BigDecimal.ZERO) != 0 || right.compareTo(BigDecimal.ZERO) != 0) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return left;
    }

    private BigDecimal logicalAndExpression() throws ParseException {
        BigDecimal left = equalityExpression();
        while (currentPosition().getToken().equals(Token.AND)) {
            position++;
            BigDecimal right = equalityExpression();
            left = (left.compareTo(BigDecimal.ZERO) != 0 && right.compareTo(BigDecimal.ZERO) != 0) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return left;
    }

    private BigDecimal equalityExpression() throws ParseException {
        BigDecimal left = arithmeticExpression();
        if (isComparisonOperator(currentPosition().getToken())) {
            String operator = currentPosition().getToken();
            position++;
            BigDecimal right = arithmeticExpression();
            return evaluateComparison(left, right, operator);
        }
        return left;
    }

    private boolean isComparisonOperator(String token) {
        return token.equals(Token.EQUAL_EQUAL) || token.equals(Token.NOT_EQUAL) ||
                token.equals(Token.LESS_THAN) || token.equals(Token.GREATER_THAN) ||
                token.equals(Token.LESS_THAN_OR_EQUAL) || token.equals(Token.GREATER_THAN_OR_EQUAL);
    }

    private BigDecimal evaluateComparison(BigDecimal left, BigDecimal right, String operator) {
        return switch (operator) {
            case Token.EQUAL_EQUAL -> left.compareTo(right) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case Token.NOT_EQUAL -> left.compareTo(right) != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case Token.LESS_THAN -> left.compareTo(right) < 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case Token.GREATER_THAN -> left.compareTo(right) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case Token.LESS_THAN_OR_EQUAL -> left.compareTo(right) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case Token.GREATER_THAN_OR_EQUAL -> left.compareTo(right) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal arithmeticExpression() throws ParseException {
        BigDecimal left = (BigDecimal) term();
        while (currentPosition().getToken().equals(Token.PLUS) || currentPosition().getToken().equals(Token.MINUS)) {
            Token operator = currentPosition();
            position++;
            BigDecimal right = (BigDecimal) term();
            left = evaluateArithmetic(left, right, operator.getToken());
        }
        return left;
    }

    private BigDecimal evaluateArithmetic(BigDecimal left, BigDecimal right, String operator) {
        return switch (operator) {
            case Token.PLUS -> left.add(right);
            case Token.MINUS -> left.subtract(right);
            default -> left;
        };
    }

    private Object term() throws ParseException {
        Object left = factor();
        while (isMultiplicativeOperator(currentPosition().getToken())) {
            String operator = currentPosition().getToken();
            position++;
            Object right = factor();
            left = evaluateMultiplicative(left, right, operator);
        }
        return left;
    }

    private boolean isMultiplicativeOperator(String token) {
        return token.equals(Token.ASTERISK) || token.equals(Token.SLASH) || token.equals(Token.PERCENT);
    }

    private Object evaluateMultiplicative(Object left, Object right, String operator) throws ParseException {
        if (left instanceof BigDecimal && right instanceof BigDecimal) {
            return switch (operator) {
                case Token.ASTERISK -> ((BigDecimal) left).multiply((BigDecimal) right);
                case Token.SLASH -> {
                    if (((BigDecimal) right).compareTo(BigDecimal.ZERO) == 0) {
                        throw new ParseException("Division by zero", currentPosition().getLine(), currentPosition().getColumn());
                    }
                    yield ((BigDecimal) left).divide((BigDecimal) right, MathContext.DECIMAL128);
                }
                case Token.PERCENT -> ((BigDecimal) left).remainder((BigDecimal) right);
                default -> left;
            };
        } else {
            throw new ParseException("Invalid operation between types", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private Object factor() throws ParseException {
        Token current = currentPosition();
        return switch (current.getToken()) {
            case Token.NUMBER_LITERAL -> new BigDecimal(consume(Token.NUMBER_LITERAL).getValue());
            case Token.VARIABLE_LITERAL -> getVariableValue(consume(Token.VARIABLE_LITERAL).getValue());
            case Token.STRING_LITERAL -> consume(Token.STRING_LITERAL).getValue();
            case Token.LEFT_PAREN -> {
                position++;
                Object result = expression();
                if (currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
                    position++;
                }
                yield result;
            }
            case Token.IDENTIFIER, Token.DOLLAR -> getVariableValue(consumeVariableName(current));
            default -> throw new ParseException("Unexpected token", current.getLine(), current.getColumn());
        };
    }

    private String consumeVariableName(Token current) throws ParseException {
        if (current.getToken().equals(Token.DOLLAR)) {
            position++;
            return consume(Token.IDENTIFIER).getValue();
        } else {
            position++;
            return current.getValue();
        }
    }

    private Object getVariableValue(String variableName) throws ParseException {
        if (!variables.containsKey(variableName)) {
            throw new ParseException("Undefined variable: " + variableName, currentPosition().getLine(), currentPosition().getColumn());
        }
        return variables.get(variableName);
    }

    private boolean evaluateExpression() throws ParseException {
        Object result = expression();
        if (result instanceof Boolean) {
            return (Boolean) result;
        } else {
            throw new ParseException("Expected a boolean expression", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private boolean evaluateIs(Object left, String type) throws ParseException {
        return switch (type) {
            case Token.NUMBER -> left instanceof BigDecimal;
            case Token.STRING -> left instanceof String;
            case Token.BOOLEAN -> left instanceof Boolean;
            case Token.CHAR -> left instanceof Character;
            default -> throw new ParseException("Unsupported type: " + type.toLowerCase(), currentPosition().getLine(), currentPosition().getColumn());
        };
    }

    private Object expression() throws ParseException {
        Object left = term();

        while (isExpressionOperator(currentPosition().getToken()) || currentPosition().getToken().equals(Token.IS)) {

            Token operator = currentPosition();
            consume(operator.getToken());
            if (operator.getToken().equals(Token.IS)) {
                String type = consume(currentPosition().getToken()).getToken();
                left = evaluateIs(left, type);
            } else {
                Object right = term();
                left = evaluateExpressionOperation(left, right, operator.getToken());
            }
        }
        return left;
    }

    private boolean isExpressionOperator(String token) {
        return token.equals(Token.PLUS) || token.equals(Token.MINUS) ||
                token.equals(Token.EQUAL_EQUAL) || token.equals(Token.NOT_EQUAL) ||
                token.equals(Token.GREATER_THAN) || token.equals(Token.LESS_THAN) ||
                token.equals(Token.GREATER_THAN_OR_EQUAL) || token.equals(Token.LESS_THAN_OR_EQUAL);
    }

    private Object evaluateExpressionOperation(Object left, Object right, String operator) throws ParseException {
        if (left instanceof BigDecimal && right instanceof BigDecimal) {
            return evaluateArithmeticOperation((BigDecimal) left, (BigDecimal) right, operator);
        } else if (left instanceof String && right instanceof String) {
            return evaluateStringOperation((String) left, (String) right, operator);
        } else {
            throw new ParseException("Invalid operation between types", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private Object evaluateArithmeticOperation(BigDecimal left, BigDecimal right, String operator) {
        return switch (operator) {
            case Token.EQUAL_EQUAL -> left.equals(right);
            case Token.NOT_EQUAL -> !left.equals(right);
            default -> left;
        };
    }

    private Object evaluateStringOperation(String left, String right, String operator) {
        return switch (operator) {
            case Token.PLUS -> left + right;
            case Token.EQUAL_EQUAL -> left.equals(right);
            case Token.NOT_EQUAL -> !left.equals(right);
            default -> left;
        };
    }

    private Token currentPosition() {
        if (position >= tokens.size()) {
            return new Token(Token.EOF, null, tokens.get(tokens.size() - 1).getLine(), tokens.get(tokens.size() - 1).getColumn());
        }
        return tokens.get(position);
    }

    private Token consume(String expectedToken) throws ParseException {
        Token current = currentPosition();
        if (current.getToken().equals(expectedToken)) {
            position++;
            return current;
        } else {
            throw new ParseException("Expected '" + expectedToken + "', found '" + current.getToken() + "'", current.getLine(), current.getColumn());
        }
    }
}