package io.github._3xhaust.parser;

import io.github._3xhaust.exception.ParseException;
import io.github._3xhaust.token.Token;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private int position = 0;

    // 변수와 그 값을 저장하는 맵. 값은 Object 타입으로 저장하여 다양한 타입을 지원합니다.
    private final Map<String, Object> variables = new HashMap<>();

    // 상수 이름을 저장하는 집합
    private final Set<String> constants = new HashSet<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        for(Token token : tokens) {
            System.out.println(token.getToken());
        }
    }

    public void parse() {
        try {
            while (!Objects.equals(currentPosition().getToken(), Token.EOF)) {
                statement();
            }
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Line " + e.getLine() + ", Column " + e.getColumn());
        }
    }

    private void statement() throws ParseException {
        if (Objects.equals(currentPosition().getToken(), Token.PRINT)) {
            printStatement();
        } else if (Objects.equals(currentPosition().getToken(), Token.IF)) {
            ifStatement();
        } else if (Objects.equals(currentPosition().getToken(), Token.IDENTIFIER) ||
                Objects.equals(currentPosition().getToken(), Token.DOLLAR)) {
            variableDeclaration();
        }

        if (!isAtEnd() && currentPosition().getToken().equals(Token.SEMICOLON)) {
            throw new ParseException("Unexpected ';'", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private void variableDeclaration() throws ParseException {
        boolean isConstant = false;
        if (Objects.equals(currentPosition().getToken(), Token.DOLLAR)) {
            isConstant = true;
            consume(Token.DOLLAR);
        }

        String variableName = consume(Token.IDENTIFIER).getValue();

        if (isConstant && variables.containsKey(variableName)) {
            throw new ParseException("Cannot reassign constant " + variableName, currentPosition().getLine(), currentPosition().getColumn());
        }

        consume(Token.COLON);

        String type = currentPosition().getToken();
        position++;
        consume(Token.EQUAL);

        Object value;
        switch (type) {
            case Token.NUMBER:
                value = expressionNumber();
                break;
            case Token.STRING:
                value = expressionString();
                break;
            case Token.BOOLEAN:
                value = expressionBoolean();
                break;
            case Token.CHAR:
                value = expressionChar();
                break;
            default:
                throw new ParseException("Unsupported type: " + type, currentPosition().getLine(), currentPosition().getColumn());
        }

        if (isConstant) {
            constants.add(variableName);
        }
        variables.put(variableName, convertType(value, type));
    }

    private void printStatement() throws ParseException {
        consume(Token.PRINT);
        consume(Token.LEFT_PAREN);

        Object result = expression();
        System.out.println(result);

        consume(Token.RIGHT_PAREN);
    }

    private void ifStatement() throws ParseException {
        boolean conditionMet = false;

        consume(Token.IF);
        consume(Token.LEFT_PAREN);
        boolean condition = evaluateExpression();
        consume(Token.RIGHT_PAREN);
        consume(Token.LEFT_BRACE);

        if (condition) {
            block();
            conditionMet = true;
        } else {
            skipBlock();
        }

        consume(Token.RIGHT_BRACE);

        while (currentPosition().getToken().equals(Token.ELSE_IF)) {
            consume(Token.ELSE_IF);
            consume(Token.LEFT_PAREN);
            condition = evaluateExpression();
            consume(Token.RIGHT_PAREN);
            consume(Token.LEFT_BRACE);

            if (!conditionMet && condition) {
                block();
                conditionMet = true;
            } else {
                skipBlock();
            }

            consume(Token.RIGHT_BRACE);
        }

        if (currentPosition().getToken().equals(Token.ELSE)) {
            consume(Token.ELSE);
            consume(Token.LEFT_BRACE);

            if (!conditionMet) {
                block();
            } else {
                skipBlock();
            }

            consume(Token.RIGHT_BRACE);
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
        if (value == null) {
            return null;
        }

        switch (targetType) {
            case Token.NUMBER:
                if (value instanceof BigDecimal) {
                    return value;
                } else if (value instanceof String) {
                    try {
                        return new BigDecimal((String) value);
                    } catch (NumberFormatException e) {
                        throw new ParseException("Cannot convert String to number", currentPosition().getLine(), currentPosition().getColumn());
                    }
                } else if (value instanceof Boolean) {
                    return ((Boolean) value) ? BigDecimal.ONE : BigDecimal.ZERO;
                } else if (value instanceof Character) {
                    return new BigDecimal((int) ((Character) value).charValue());
                }
                break;
            case Token.STRING:
                return value.toString();
            case Token.BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                } else if (value instanceof BigDecimal) {
                    return ((BigDecimal) value).compareTo(BigDecimal.ZERO) != 0;
                } else if (value instanceof String) {
                    return !((String) value).isEmpty();
                } else if (value instanceof Character) {
                    return ((Character) value) != '\0';
                }
                break;
            case Token.CHAR:
                if (value instanceof Character) {
                    return value;
                } else if (value instanceof String && ((String) value).length() == 1) {
                    return ((String) value).charAt(0);
                } else if (value instanceof BigDecimal) {
                    int intValue = ((BigDecimal) value).intValue();
                    if (intValue >= 0 && intValue <= 65535) {
                        return (char) intValue;
                    }
                }
                break;
        }
        throw new ParseException("Cannot convert " + value.getClass().getSimpleName() + " to " + targetType, currentPosition().getLine(), currentPosition().getColumn());
    }

    private BigDecimal expressionNumber() throws ParseException {
        return logicalOrExpression();
    }

    private String expressionString() throws ParseException {
        Token current = consume(Token.STRING_LITERAL);
        return current.getValue();
    }

    private Boolean expressionBoolean() throws ParseException {
        Token current = consume(Token.BOOLEAN_LITERAL);
        return Boolean.parseBoolean(current.getValue());
    }

    private Character expressionChar() throws ParseException {
        Token current = consume(Token.CHAR_LITERAL);
        return current.getValue().charAt(0);
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

        if (currentPosition().getToken().equals(Token.EQUAL_EQUAL) ||
                currentPosition().getToken().equals(Token.NOT_EQUAL) ||
                currentPosition().getToken().equals(Token.LESS_THAN) ||
                currentPosition().getToken().equals(Token.GREATER_THAN) ||
                currentPosition().getToken().equals(Token.LESS_THAN_OR_EQUAL) ||
                currentPosition().getToken().equals(Token.GREATER_THAN_OR_EQUAL)) {

            String operator = currentPosition().getToken();
            position++;
            BigDecimal right = arithmeticExpression();

            return switch (operator) {
                case Token.EQUAL_EQUAL -> Objects.equals(left, right) ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.NOT_EQUAL -> !Objects.equals(left, right) ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.LESS_THAN -> compareObjects(left, right) < 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.GREATER_THAN -> compareObjects(left, right) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.LESS_THAN_OR_EQUAL -> compareObjects(left, right) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.GREATER_THAN_OR_EQUAL -> compareObjects(left, right) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                default -> throw new ParseException("Unexpected comparison operator", currentPosition().getLine(), currentPosition().getColumn());
            };
        }

        return left;
    }

    private int compareObjects(Object left, Object right) throws ParseException {
        if (left instanceof Comparable && right instanceof Comparable) {
            try {
                return ((Comparable) left).compareTo(right);
            } catch (ClassCastException e) {
                throw new ParseException("Cannot compare " + left.getClass().getSimpleName() + " with " + right.getClass().getSimpleName(), currentPosition().getLine(), currentPosition().getColumn());
            }
        }
        throw new ParseException("Cannot compare " + left.getClass().getSimpleName() + " with " + right.getClass().getSimpleName(), currentPosition().getLine(), currentPosition().getColumn());
    }

    private BigDecimal arithmeticExpression() throws ParseException {
        BigDecimal left = (BigDecimal) term();

        while (Objects.equals(currentPosition().getToken(), Token.PLUS) ||
                Objects.equals(currentPosition().getToken(), Token.MINUS)) {
            Token operator = currentPosition();
            position++;
            BigDecimal right = (BigDecimal) term();

            switch (operator.getToken()) {
                case Token.PLUS:
                    left = left.add(right);
                    break;
                case Token.MINUS:
                    left = left.subtract(right);
                    break;
            }
        }

        return left;
    }

    private Object factor() throws ParseException {
        Token current = currentPosition();
        if (current.getToken().equals(Token.NUMBER_LITERAL)) {
            position++;
            return new BigDecimal(current.getValue());
        } else if (current.getToken().equals(Token.STRING_LITERAL)) {
            position++;
            return current.getValue();
        } else if (current.getToken().equals(Token.LEFT_PAREN)) {
            position++;
            Object result = expression();
            if (currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
                position++;
            }
            return result;
        } else if (current.getToken().equals(Token.IDENTIFIER) || current.getToken().equals(Token.DOLLAR)) {
            String variableName = current.getValue();
            if (current.getToken().equals(Token.DOLLAR)) {
                position++;
                variableName = consume(Token.IDENTIFIER).getValue();
            } else {
                position++;
            }
            if (!variables.containsKey(variableName)) {
                throw new ParseException("Undefined variable: " + variableName, current.getLine(), current.getColumn());
            }
            return variables.get(variableName);
        } else {
            throw new ParseException("Unexpected token", current.getLine(), current.getColumn());
        }
    }

    private boolean evaluateExpression() throws ParseException {
        Object result = expression();
        if (result instanceof Boolean) {
            return (Boolean) result;
        } else {
            throw new ParseException("Cannot evaluate expression as boolean", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private Object expression() throws ParseException {
        Object left = term();

        while (Objects.equals(currentPosition().getToken(), Token.PLUS) ||
                Objects.equals(currentPosition().getToken(), Token.MINUS) ||
                Objects.equals(currentPosition().getToken(), Token.EQUAL_EQUAL) ||
                Objects.equals(currentPosition().getToken(), Token.NOT_EQUAL)) {
            Token operator = currentPosition();
            position++;
            Object right = term();

            if (left instanceof BigDecimal && right instanceof BigDecimal) {
                switch (operator.getToken()) {
                    case Token.PLUS:
                        left = ((BigDecimal) left).add((BigDecimal) right);
                        break;
                    case Token.MINUS:
                        left = ((BigDecimal) left).subtract((BigDecimal) right);
                        break;
                    case Token.EQUAL_EQUAL:
                        left = ((BigDecimal) left).compareTo((BigDecimal) right) == 0;
                        break;
                    case Token.NOT_EQUAL:
                        left = ((BigDecimal) left).compareTo((BigDecimal) right) != 0;
                        break;
                }
            } else if (left instanceof String && right instanceof String) {
                switch (operator.getToken()) {
                    case Token.PLUS:
                        left = (String) left + (String) right;
                        break;
                    case Token.EQUAL_EQUAL:
                        left = left.equals(right);
                        break;
                    case Token.NOT_EQUAL:
                        left = !left.equals(right);
                        break;
                }
            } else {
                throw new ParseException("Invalid operation between types", currentPosition().getLine(), currentPosition().getColumn());
            }
        }

        return left;
    }

    private Object term() throws ParseException {
        Object left = factor();

        while (currentPosition().getToken().equals(Token.ASTERISK) ||
                currentPosition().getToken().equals(Token.SLASH) ||
                currentPosition().getToken().equals(Token.PERCENT)) {
            String operator = currentPosition().getToken();
            position++;
            Object right = factor();

            if (left instanceof BigDecimal && right instanceof BigDecimal) {
                switch (operator) {
                    case Token.ASTERISK:
                        left = ((BigDecimal) left).multiply((BigDecimal) right);
                        break;
                    case Token.SLASH:
                        if (((BigDecimal) right).compareTo(BigDecimal.ZERO) == 0) {
                            throw new ParseException("Division by zero", currentPosition().getLine(), currentPosition().getColumn());
                        }
                        left = ((BigDecimal) left).divide((BigDecimal) right, MathContext.DECIMAL128);
                        break;
                    case Token.PERCENT:
                        left = ((BigDecimal) left).remainder((BigDecimal) right);
                        break;
                }
            } else {
                throw new ParseException("Invalid operation between types", currentPosition().getLine(), currentPosition().getColumn());
            }
        }

        return left;
    }


    private Token currentPosition() {
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