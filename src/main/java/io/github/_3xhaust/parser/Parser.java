package io.github._3xhaust.parser;

import io.github._3xhaust.exception.ParseException;
import io.github._3xhaust.token.Token;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private final String fileName;
    private final String[] lines;
    private int position = 0;
    private final Map<String, Object> variables = new HashMap<>();
    private final Set<String> constants = new HashSet<>();
    private final Set<String> declaredVariables = new HashSet<>();

    public Parser(List<Token> tokens, String fileName, String input) {
        this.tokens = tokens;
        this.fileName = fileName;
        this.lines = input.split("\n");
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
            case Token.PRINT -> printStatement(false);
            case Token.PRINTLN -> printStatement(true);
            case Token.FOR -> forStatement();
            case Token.IF -> ifStatement();
            case Token.IDENTIFIER, Token.DOLLAR -> {
                if (peek(1).getToken().equals(Token.EQUAL)) {
                    variableAssignment();
                } else {
                    variableDeclaration();
                }
            }
            default -> throw new ParseException(fileName, "Syntax error: Unexpected token '" + currentPosition().getValue() + "'",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private void variableDeclaration() throws ParseException {
        boolean isConstant = currentPosition().getToken().equals(Token.DOLLAR);
        if (isConstant) consume(Token.DOLLAR);

        String variableName = consume(Token.IDENTIFIER).getValue();
        ensureVariableNotDeclared(variableName);

        consume(Token.COLON);
        String type = consume(currentPosition().getToken()).getToken();

        if (type.contains("array")) {
            consume(Token.EQUAL);
            consume(Token.LEFT_BRACKET);

            List<Object> array = new ArrayList<>();
            while (!currentPosition().getToken().equals(Token.RIGHT_BRACKET)) {
                array.add(parseArrayElement(type)); // 배열 요소 파싱
                if (currentPosition().getToken().equals(Token.COMMA)) {
                    consume(Token.COMMA);
                }
            }
            consume(Token.RIGHT_BRACKET);

            assignVariable(variableName, array, isConstant);
        } else {
            consume(Token.EQUAL);
            Object value = getTypedValue(type);
            assignVariable(variableName, value, isConstant);
        }
    }

    private Object parseArrayElement(String arrayType) throws ParseException {
        String elementType = arrayType.replace(" array", "");
        return getTypedValue(elementType);
    }

    private void assignVariable(String variableName, Object value, boolean isConstant) {
        if (isConstant) {
            constants.add(variableName);
        }
        variables.put(variableName, value);
        declaredVariables.add(variableName);
    }

    private void ensureVariableNotDeclared(String variableName) throws ParseException {
        if (declaredVariables.contains(variableName)) {
            throw new ParseException(fileName, "Variable '" + variableName + "' already declared",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private Object getTypedValue(String type) throws ParseException {
        String currentTokenType = currentPosition().getToken();

        if (currentTokenType.equals(getTokenLiteralByType(type))) {
            switch (type) {
                case Token.NUMBER: return expressionNumber();
                case Token.STRING: return expressionString();
                case Token.BOOLEAN: return expressionBoolean();
                case Token.CHAR: return expressionChar();
                case Token.NULL: return expressionNull();
                default: throw new ParseException(fileName, "Unsupported type: " + type.toLowerCase(),
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }
        } else {
            throw new ParseException(fileName, "Type mismatch: Cannot assign " + currentTokenType.toLowerCase() + " to " + type.toLowerCase(),
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private String getTokenLiteralByType(String type) {
        return switch (type) {
            case Token.NUMBER -> Token.NUMBER_LITERAL;
            case Token.STRING -> Token.STRING_LITERAL;
            case Token.BOOLEAN -> Token.BOOLEAN_LITERAL;
            case Token.CHAR -> Token.CHAR_LITERAL;
            case Token.NULL -> Token.NULL;
            default -> "";
        };
    }

    private void variableAssignment() throws ParseException {
        String variableName = consume(Token.IDENTIFIER).getValue();
        consume(Token.EQUAL);

        if (!variables.containsKey(variableName)) {
            throw new ParseException(fileName, "Undefined variable: " + variableName,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        if (constants.contains(variableName)) {
            throw new ParseException(fileName, "Cannot reassign constant variable: " + variableName,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        Object value = expression();
        variables.put(variableName, value);
    }

    private void forStatement() throws ParseException {
        consume(Token.FOR);
        consume(Token.LEFT_PAREN);

        String variable = consume(Token.IDENTIFIER).getValue();

        consume(Token.COLON);

        String type = consume(currentPosition().getToken()).getToken();

        consume(Token.IN);

        if (currentPosition().getToken().equals(Token.IDENTIFIER)) { // 배열 순회
            String arrayVariable = consume(Token.IDENTIFIER).getValue();
            consume(Token.RIGHT_PAREN);

            if (!variables.containsKey(arrayVariable)) {
                throw new ParseException(fileName, "Undefined variable: " + arrayVariable,
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }

            Object arrayObject = variables.get(arrayVariable);
            if (!(arrayObject instanceof List)) {
                throw new ParseException(fileName, "Variable '" + arrayVariable + "' is not an array",
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }

            List<?> array = (List<?>) arrayObject;
            for (Object element : array) {
                variables.put(variable, element);
                if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
                    consume(Token.LEFT_BRACE);
                    block();
                    consume(Token.RIGHT_BRACE);
                } else {
                    statement();
                }
            }

        } else if (currentPosition().getToken().equals(Token.NUMBER_LITERAL)) { // 범위 순회
            BigDecimal start = expressionNumber();
            consume(Token.DOT_DOT);
            BigDecimal end = expressionNumber();
            BigDecimal step = BigDecimal.ONE;

            if (currentPosition().getToken().equals(Token.DOT_DOT)) { // step 지정된 경우
                consume(Token.DOT_DOT);
                step = expressionNumber();
            }
            consume(Token.RIGHT_PAREN);

            if (step.compareTo(BigDecimal.ZERO) == 0) {
                throw new ParseException(fileName, "Step cannot be zero",
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }

            if (step.compareTo(BigDecimal.ZERO) > 0) {
                for (BigDecimal i = start; i.compareTo(end) <= 0; i = i.add(step)) {
                    variables.put(variable, i);
                    executeForLoopBody();
                }
            } else {
                for (BigDecimal i = start; i.compareTo(end) >= 0; i = i.add(step)) {
                    variables.put(variable, i);
                    executeForLoopBody();
                }
            }

        } else {
            throw new ParseException(fileName, "Expected identifier or number literal after 'in'",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private void executeForLoopBody() throws ParseException {
        if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
            consume(Token.LEFT_BRACE);
            block();
            consume(Token.RIGHT_BRACE);
        } else {
            statement();
        }
    }

    private void printStatement(boolean ln) throws ParseException {
        if (ln) consume(Token.PRINTLN);
        else consume(Token.PRINT);

        consume(Token.LEFT_PAREN);

        StringBuilder result = new StringBuilder();
        while (!currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
            if (currentPosition().getToken().equals(Token.VARIABLE_LITERAL)) {
                String variableName = consume(Token.VARIABLE_LITERAL).getValue();

                if (!variables.containsKey(variableName)) {
                    throw new ParseException(fileName, "Undefined variable: " + variableName,
                            currentPosition().getLine(),
                            currentPosition().getColumn(),
                            getCurrentLine());
                }

                result.append(variables.get(variableName));
            } else if (currentPosition().getToken().equals(Token.STRING_LITERAL)) {
                result.append(consume(Token.STRING_LITERAL).getValue());
            } else if (currentPosition().getToken().equals(Token.PLUS)) {
                consume(Token.PLUS);
            } else {
                result.append(expression());
            }
        }

        if (ln) System.out.println(result.toString());
        else System.out.print(result.toString());

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
        return position >= tokens.size() || currentPosition().getToken().equals(Token.EOF);
    }

    private BigDecimal expressionNumber() throws ParseException {
        return logicalOrExpression();
    }

    private String expressionString() throws ParseException {
        StringBuilder result = new StringBuilder();
        while (currentPosition().getToken().equals(Token.STRING_LITERAL) || currentPosition().getToken().equals(Token.VARIABLE_LITERAL) || currentPosition().getToken().equals(Token.PLUS)) {
            if (currentPosition().getToken().equals(Token.STRING_LITERAL)) {
                result.append(consume(Token.STRING_LITERAL).getValue());
            } else if (currentPosition().getToken().equals(Token.VARIABLE_LITERAL)) {
                String variableName = consume(Token.VARIABLE_LITERAL).getValue();
                if (!variables.containsKey(variableName)) {
                    throw new ParseException(fileName, "Undefined variable: " + variableName,
                            currentPosition().getLine(),
                            currentPosition().getColumn(),
                            getCurrentLine());
                }
                result.append(variables.get(variableName).toString());
            } else if (currentPosition().getToken().equals(Token.PLUS)) {
                consume(Token.PLUS);
            }
        }
        return result.toString();
    }

    private Boolean expressionBoolean() throws ParseException {
        return Boolean.parseBoolean(consume(Token.BOOLEAN_LITERAL).getValue());
    }

    private Character expressionChar() throws ParseException {
        return consume(Token.CHAR_LITERAL).getValue().charAt(0);
    }

    private Object expressionNull() throws ParseException{
        if(!currentPosition().getToken().equals(Token.NULL)) {
            throw new ParseException(fileName, "Type mismatch: Expected null",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        consume(Token.NULL);
        return null;
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
                        throw new ParseException(fileName, "Division by zero",
                                currentPosition().getLine(),
                                currentPosition().getColumn(),
                                getCurrentLine());
                    }
                    yield ((BigDecimal) left).divide((BigDecimal) right, MathContext.DECIMAL128);
                }
                case Token.PERCENT -> ((BigDecimal) left).remainder((BigDecimal) right);
                default -> left;
            };
        } else {
            throw new ParseException(fileName, "Invalid operation between types",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
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
            case Token.IDENTIFIER -> {
                String variableName = consume(Token.IDENTIFIER).getValue();
                if (currentPosition().getToken().equals(Token.LEFT_BRACKET)) {
                    consume(Token.LEFT_BRACKET);
                    int index = ((BigDecimal) expression()).intValue();
                    consume(Token.RIGHT_BRACKET);
                    yield getVariableValueAtIndex(variableName, index);
                } else {
                    yield getVariableValue(variableName);
                }
            }
            case Token.DOLLAR -> getVariableValue(consumeVariableName(current));
            default -> throw new ParseException(fileName, "Unexpected token",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        };
    }

    private Object getVariableValueAtIndex(String variableName, int index) throws ParseException {
        Object variable = variables.get(variableName);
        if (variable instanceof List) {
            List<?> array = (List<?>) variable;
            if (index < 0 || index >= array.size()) {
                throw new ParseException(fileName, "Array index out of bounds: " + index,
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }
            return array.get(index);
        } else {
            throw new ParseException(fileName, "Variable '" + variableName + "' is not an array",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
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
            throw new ParseException(fileName, "Undefined variable: " + variableName,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
        return variables.get(variableName);
    }

    private boolean evaluateExpression() throws ParseException {
        Object result = expression();
        if (result instanceof Boolean) {
            return (Boolean) result;
        } else {
            throw new ParseException(fileName, "Expected a boolean expression",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private boolean evaluateIs(Object left, String type) throws ParseException {
        return switch (type) {
            case Token.NUMBER -> left instanceof BigDecimal;
            case Token.STRING -> left instanceof String;
            case Token.BOOLEAN -> left instanceof Boolean;
            case Token.CHAR -> left instanceof Character;
            case Token.ARRAY -> left instanceof List;
            case Token.NULL -> left == null;
            default -> throw new ParseException(fileName, "Unsupported type: " + type.toLowerCase(),
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        };
    }

    private Object expression() throws ParseException {
        Object left = term();

        while (isExpressionOperator(currentPosition().getToken()) ||
                currentPosition().getToken().equals(Token.IS)) {

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
            throw new ParseException(fileName, "Invalid operation between types",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
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
            throw new ParseException(fileName, "Expected '" + expectedToken + "', found '" + current.getToken() + "'",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private String getCurrentLine() {
        return lines[currentPosition().getLine() - 1];
    }

    private Token peek(int offset) {
        if (position + offset >= tokens.size()) return new Token(Token.EOF, null, currentPosition().getLine(), currentPosition().getColumn());
        return tokens.get(position + offset);
    }
}