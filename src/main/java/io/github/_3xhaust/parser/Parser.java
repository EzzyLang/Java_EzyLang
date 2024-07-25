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
    private final Map<String, String> variableTypes = new HashMap<>();
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
            while (!isAtEnd()) {
                statement();
            }
        } catch (ParseException e) {
            System.err.println(e.getFormattedMessage());
        }
    }

    private void statement() throws ParseException {
        switch (currentPosition().getToken()) {
            case Token.PRINT, Token.PRINTLN -> printStatement();
            case Token.FOR -> forStatement();
            case Token.IF -> ifStatement();
            case Token.IDENTIFIER, Token.DOLLAR -> {
                if (peek(1).getToken().equals(Token.EQUAL) ||
                        peek(1).getToken().equals(Token.LEFT_BRACKET)) {
                    variableAssignment();
                } else {
                    variableDeclaration();
                }
            }
            default -> throw unexpectedTokenException("Invalid start of statement");
        }
    }

    private void variableDeclaration() throws ParseException {
        boolean isConstant = currentPosition().getToken().equals(Token.DOLLAR);
        if (isConstant) consume(Token.DOLLAR);

        String variableName = consume(Token.IDENTIFIER).getValue();
        ensureVariableNotDeclared(variableName);

        consume(Token.COLON);
        String type = consume(currentPosition().getToken()).getToken();

        consume(Token.EQUAL);

        if (type.contains("array")) {
            declareArrayVariable(variableName, type, isConstant);
        } else {
            declareVariable(variableName, type, isConstant);
        }

        variableTypes.put(variableName, type);
    }

    private void declareVariable(String variableName, String type, boolean isConstant) throws ParseException {
        Object value;

        if (currentPosition().getToken().equals(Token.IDENTIFIER) && peek(1).getToken().equals(Token.LEFT_BRACKET)) {
            String arrayVariable = consume(Token.IDENTIFIER).getValue();
            consume(Token.LEFT_BRACKET);

            int index = ((BigDecimal) expression()).intValue();

            consume(Token.RIGHT_BRACKET);
            value = getVariableValueAtIndex(arrayVariable, index);
        } else if (currentPosition().getToken().equals(Token.IDENTIFIER) && peek(1).getToken().equals(Token.DOT_LENGTH)) {
            String arrayVariable = consume(Token.IDENTIFIER).getValue();
            consume(Token.DOT_LENGTH);

            value = getArrayLength(arrayVariable);
        } else {
            value = getTypedValue(type);
        }

        assignVariable(variableName, value, isConstant);
    }

    private void declareArrayVariable(String variableName, String type, boolean isConstant) throws ParseException {
        consume(Token.LEFT_BRACKET);

        List<Object> array = new ArrayList<>();
        while (!currentPosition().getToken().equals(Token.RIGHT_BRACKET)) {
            array.add(parseArrayElement(type));
            if (currentPosition().getToken().equals(Token.COMMA)) {
                consume(Token.COMMA);
            }
        }
        consume(Token.RIGHT_BRACKET);

        assignVariable(variableName, array, isConstant);
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
            return switch (type) {
                case Token.NUMBER -> expressionNumber();
                case Token.STRING -> expressionString();
                case Token.BOOLEAN -> expressionBoolean();
                case Token.CHAR -> expressionChar();
                case Token.NULL -> expressionNull();
                default -> throw new ParseException(fileName, "Unsupported type: " + type.toLowerCase(),
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            };
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
        if (currentPosition().getToken().equals(Token.IDENTIFIER) &&
                peek(1).getToken().equals(Token.LEFT_BRACKET)) {
            assignToArrayElement();
        } else {
            assignToVariable();
        }
    }

    private void assignToArrayElement() throws ParseException {
        String arrayName = consume(Token.IDENTIFIER).getValue();
        consume(Token.LEFT_BRACKET);
        int index = ((BigDecimal) expression()).intValue();
        consume(Token.RIGHT_BRACKET);

        consume(Token.EQUAL);

        Object value = expression();
        assignValueAtIndex(arrayName, index, value);
    }

    private void assignToVariable() throws ParseException {
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

    private void assignValueAtIndex(String arrayName, int index, Object value) throws ParseException {
        Object array = variables.get(arrayName);

        if (array instanceof List<?> arrayList) {
            if (index < 0 || index >= arrayList.size()) {
                throw new ParseException(fileName, "Array index out of bounds: " + index,
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }

            String arrayType = getVariableType(arrayName).replace(" array", "");
            if (!checkElementType(arrayType, value)) {
                throw new ParseException(fileName,
                        "Type mismatch: Cannot assign " + value.getClass().getSimpleName() + " to " + arrayType,
                        currentPosition().getLine(),
                        currentPosition().getColumn(),
                        getCurrentLine());
            }

            ((List) arrayList).set(index, value);
        } else {
            throw new ParseException(fileName, "Variable '" + arrayName + "' is not an array",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private boolean checkElementType(String arrayType, Object element) {
        String elementType = arrayType.replace(" array", "");
        return switch (elementType) {
            case Token.NUMBER -> element instanceof BigDecimal;
            case Token.STRING -> element instanceof String;
            case Token.BOOLEAN -> element instanceof Boolean;
            case Token.CHAR -> element instanceof Character;
            case Token.NULL -> element == null;
            default -> false;
        };
    }

    private String getVariableType(String variableName) throws ParseException {
        if (!this.variables.containsKey(variableName)) {
            throw new ParseException(fileName, "Undefined variable: " + variableName,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        return variableTypes.get(variableName);
    }

    private void forStatement() throws ParseException {
        consume(Token.FOR);
        consume(Token.LEFT_PAREN);

        String variable = consume(Token.IDENTIFIER).getValue();
        consume(Token.COLON);
        String type = consume(currentPosition().getToken()).getToken();
        consume(Token.IN);

        if (currentPosition().getToken().equals(Token.IDENTIFIER)) {
            iterateOverArray(variable, type);
        } else if (currentPosition().getToken().equals(Token.NUMBER_LITERAL) ||
                currentPosition().getToken().equals(Token.DOT_LENGTH)) {
            iterateOverRange(variable, type);
        } else {
            throw unexpectedTokenException("Expected identifier or number literal after 'in'");
        }
    }

    private void iterateOverArray(String variable, String type) throws ParseException {
        String arrayVariable = consume(Token.IDENTIFIER).getValue();
        consume(Token.RIGHT_PAREN);

        if (!variables.containsKey(arrayVariable)) {
            throw new ParseException(fileName, "Undefined variable: " + arrayVariable,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        Object arrayObject = variables.get(arrayVariable);
        if (!(arrayObject instanceof List<?> array)) {
            throw new ParseException(fileName, "Variable '" + arrayVariable + "' is not an array",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        int forLoopStartPosition = position;

        for (Object element : array) {
            position = forLoopStartPosition;
            checkType(element, type);
            variables.put(variable, element);
            executeForLoopBody();
        }
    }

    private void iterateOverRange(String variable, String type) throws ParseException {
        BigDecimal start = getRangeStart();
        consume(Token.DOT_DOT);
        BigDecimal end = expressionNumber();
        BigDecimal step = BigDecimal.ONE;

        if (currentPosition().getToken().equals(Token.DOT_DOT)) {
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

        int forLoopStartPosition = position;

        if (step.compareTo(BigDecimal.ZERO) > 0) {
            for (BigDecimal i = start; i.compareTo(end) <= 0; i = i.add(step)) {
                position = forLoopStartPosition;
                checkType(i, type);
                variables.put(variable, i);
                executeForLoopBody();
            }
        } else {
            for (BigDecimal i = start; i.compareTo(end) >= 0; i = i.add(step)) {
                position = forLoopStartPosition;
                checkType(i, type);
                variables.put(variable, i);
                executeForLoopBody();
            }
        }
    }

    private BigDecimal getRangeStart() throws ParseException {
        if (currentPosition().getToken().equals(Token.DOT_LENGTH)) {
            consume(Token.DOT_LENGTH);
            String arrayVariable = consume(Token.IDENTIFIER).getValue();

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

            return new BigDecimal(((List<?>) arrayObject).size());
        } else {
            return expressionNumber();
        }
    }

    private void checkType(Object value, String type) throws ParseException {
        boolean isValidType = switch (type) {
            case Token.NUMBER -> value instanceof BigDecimal;
            case Token.STRING -> value instanceof String;
            case Token.BOOLEAN -> value instanceof Boolean;
            case Token.CHAR -> value instanceof Character;
            case Token.NULL -> value == null;
            default -> false;
        };

        if (!isValidType) {
            throw new ParseException(fileName, "Type mismatch: Expected " + type + ", found " + value.getClass().getSimpleName(),
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

    private void printStatement() throws ParseException {
        boolean ln = currentPosition().getToken().equals(Token.PRINTLN);
        consume(ln ? Token.PRINTLN : Token.PRINT);

        consume(Token.LEFT_PAREN);
        StringBuilder result = new StringBuilder();

        while (!currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
            result.append(evaluateStringExpression());
        }

        System.out.print(ln ? result + "\n" : result);

        consume(Token.RIGHT_PAREN);
    }

    private String evaluateStringExpression() throws ParseException {
        StringBuilder result = new StringBuilder();

        while (!currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
            if (currentPosition().getToken().equals(Token.VARIABLE_LITERAL)) {
                result.append(getVariableValue(consume(Token.VARIABLE_LITERAL).getValue()));
            } else if (currentPosition().getToken().equals(Token.STRING_LITERAL)) {
                result.append(consume(Token.STRING_LITERAL).getValue());
            } else if (currentPosition().getToken().equals(Token.IDENTIFIER)) {
                result.append(getVariableValue(consume(Token.IDENTIFIER).getValue()));
            } else {
                result.append(expression());
            }
        }
        return result.toString();
    }

    private void ifStatement() throws ParseException {
        boolean conditionMet = false;

        consume(Token.IF);
        consume(Token.LEFT_PAREN);
        boolean condition = evaluateCondition();
        consume(Token.RIGHT_PAREN);

        if (condition) {
            conditionMet = executeConditionalBlock();
        } else {
            skipConditionalBlock();
        }

        handleElseIf(conditionMet);
        handleElse(conditionMet);
    }

    private boolean executeConditionalBlock() throws ParseException {
        if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
            consume(Token.LEFT_BRACE);
            block();
            consume(Token.RIGHT_BRACE);
        } else {
            statement();
        }
        return true;
    }

    private void skipConditionalBlock() throws ParseException {
        if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
            consume(Token.LEFT_BRACE);
            skipBlock();
            consume(Token.RIGHT_BRACE);
        } else {
            skipStatement();
        }
    }

    private void handleElseIf(boolean conditionMet) throws ParseException {
        while (currentPosition().getToken().equals(Token.ELSE_IF)) {
            consume(Token.ELSE_IF);
            consume(Token.LEFT_PAREN);
            boolean condition = evaluateCondition();
            consume(Token.RIGHT_PAREN);

            if (!conditionMet && condition) {
                conditionMet = executeConditionalBlock();
            } else {
                skipConditionalBlock();
            }
        }
    }

    private void handleElse(boolean conditionMet) throws ParseException {
        if (currentPosition().getToken().equals(Token.ELSE)) {
            consume(Token.ELSE);

            if (!conditionMet) {
                executeConditionalBlock();
            } else {
                skipConditionalBlock();
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
        return consume(Token.STRING_LITERAL).getValue();
    }

    private Boolean expressionBoolean() throws ParseException {
        return Boolean.parseBoolean(consume(Token.BOOLEAN_LITERAL).getValue());
    }

    private Character expressionChar() throws ParseException {
        return consume(Token.CHAR_LITERAL).getValue().charAt(0);
    }

    private Object expressionNull() throws ParseException {
        if (!currentPosition().getToken().equals(Token.NULL)) {
            throw unexpectedTokenException("Type mismatch: Expected null");
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
        BigDecimal left = (BigDecimal) arithmeticExpression();
        if (isComparisonOperator(currentPosition().getToken())) {
            String operator = currentPosition().getToken();
            position++;
            BigDecimal right = (BigDecimal) arithmeticExpression();
            return evaluateComparison(left, right, operator);
        }
        return left;
    }

    private boolean isComparisonOperator(String token) {
        return token.equals(Token.EQUAL_EQUAL) ||
                token.equals(Token.NOT_EQUAL) ||
                token.equals(Token.LESS_THAN) ||
                token.equals(Token.GREATER_THAN) ||
                token.equals(Token.LESS_THAN_OR_EQUAL) ||
                token.equals(Token.GREATER_THAN_OR_EQUAL);
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

    private Object arithmeticExpression() throws ParseException {
        Object left = term(); // 먼저 곱셈/나눗셈을 처리

        while (currentPosition().getToken().equals(Token.PLUS) ||
                currentPosition().getToken().equals(Token.MINUS)) {
            String operator = currentPosition().getToken();
            consume(operator);
            Object right = term();
            left = applyOperator(left, operator, right); // 덧셈/뺄셈 결과를 누적
        }

        return left; // 최종 결과 반환
    }

    private Object term() throws ParseException {
        Object left = factor();

        while (currentPosition().getToken().equals(Token.ASTERISK) ||
                currentPosition().getToken().equals(Token.SLASH) ||
                currentPosition().getToken().equals(Token.PERCENT)) {
            String operator = currentPosition().getToken();
            consume(operator);
            Object right = factor();
            left = applyOperator(left, operator, right);
        }
        return left;
    }

    private Object factor() throws ParseException {
        Token current = currentPosition();

        return switch (current.getToken()) {
            case Token.NUMBER_LITERAL -> new BigDecimal(consume(Token.NUMBER_LITERAL).getValue());
            case Token.VARIABLE_LITERAL -> getVariableValue(consume(Token.VARIABLE_LITERAL).getValue());
            case Token.STRING_LITERAL -> consume(Token.STRING_LITERAL).getValue();
            case Token.LEFT_PAREN -> {
                consume(Token.LEFT_PAREN);
                Object result = expression();
                consume(Token.RIGHT_PAREN);
                yield result;
            }
            case Token.IDENTIFIER, Token.DOLLAR -> {
                String identifier = consumeVariableName(current);

                if (currentPosition().getToken().equals(Token.LEFT_BRACKET)) {
                    consume(Token.LEFT_BRACKET);
                    int index = ((BigDecimal) expression()).intValue();
                    consume(Token.RIGHT_BRACKET);
                    yield getVariableValueAtIndex(identifier, index);
                } else {
                    yield getVariableValue(identifier);
                }
            }
            default -> throw unexpectedTokenException("Unexpected token in factor");
        };
    }

    private Object applyOperator(Object left, String operator, Object right) throws ParseException {
        if (left instanceof BigDecimal && right instanceof BigDecimal) {
            return switch (operator) {
                case Token.PLUS -> ((BigDecimal) left).add((BigDecimal) right);
                case Token.MINUS -> ((BigDecimal) left).subtract((BigDecimal) right);
                case Token.ASTERISK -> ((BigDecimal) left).multiply((BigDecimal) right);
                case Token.PERCENT -> ((BigDecimal) left).remainder((BigDecimal) right);
                case Token.SLASH -> {
                    if (((BigDecimal) right).compareTo(BigDecimal.ZERO) == 0) {
                        throw new ParseException(fileName, "Division by zero",
                                currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
                    }
                    yield ((BigDecimal) left).divide((BigDecimal) right, MathContext.DECIMAL128);
                }
                default -> throw new ParseException(fileName, "Unsupported operator: " + operator,
                        currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
            };
        } else if (operator.equals(Token.PLUS) && (left instanceof String || right instanceof String)) {
            return left.toString() + right.toString();
        } else {
            throw new ParseException(fileName, "Invalid operation between types",
                    currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
        }
    }


    private BigDecimal getArrayLength(String arrayName) throws ParseException {
        Object array = getVariableValue(arrayName);
        if (array instanceof List) {
            return new BigDecimal(((List<?>) array).size());
        } else {
            throw new ParseException(fileName, "Cannot get length of non-array variable: " + arrayName,
                    currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
        }
    }

    private Object getVariableValueAtIndex(String variableName, int index) throws ParseException {
        Object variable = variables.get(variableName);
        if (variable instanceof List<?> array) {
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
        Object left = arithmeticExpression();

        while (isExpressionOperator(currentPosition().getToken()) ||
                currentPosition().getToken().equals(Token.IS)) {

            Token operator = currentPosition();
            consume(operator.getToken());

            if (operator.getToken().equals(Token.IS)) {
                String type = consume(currentPosition().getToken()).getToken();
                left = evaluateIs(left, type);
            } else {
                Object right = arithmeticExpression();
                left = evaluateExpressionOperation(left, right, operator.getToken());
            }
        }
        return left;
    }

    private boolean isExpressionOperator(String token) {
        return token.equals(Token.PLUS) ||
                token.equals(Token.MINUS) ||
                token.equals(Token.EQUAL_EQUAL) ||
                token.equals(Token.NOT_EQUAL) ||
                token.equals(Token.GREATER_THAN) ||
                token.equals(Token.LESS_THAN) ||
                token.equals(Token.GREATER_THAN_OR_EQUAL) ||
                token.equals(Token.LESS_THAN_OR_EQUAL);
    }

    private Object evaluateExpressionOperation(Object left, Object right, String operator) throws ParseException {
        if (left instanceof BigDecimal && right instanceof BigDecimal) {
            return evaluateArithmeticOperation((BigDecimal) left, (BigDecimal) right, operator);
        } else if (left instanceof String && right instanceof String) {
            return evaluateStringOperation((String) left, (String) right, operator);
        } else if (left instanceof Boolean && right instanceof Boolean) { // Boolean 타입 처리 추가
            return evaluateBooleanOperation((Boolean) left, (Boolean) right, operator);
        } else {
            throw new ParseException(fileName, "Invalid operation between types",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private Object evaluateBooleanOperation(Boolean left, Boolean right, String operator) { // Boolean 연산 함수 추가
        return switch (operator) {
            case Token.EQUAL_EQUAL -> left == right;
            case Token.NOT_EQUAL -> left != right;
            case Token.AND -> left && right; // AND 연산 추가
            case Token.OR -> left || right;  // OR 연산 추가
            default -> throw new IllegalArgumentException("Unsupported operator for boolean: " + operator);
        };
    }

    private Object evaluateArithmeticOperation(BigDecimal left, BigDecimal right, String operator) {
        return switch (operator) {
            case Token.EQUAL_EQUAL -> left.compareTo(right) == 0;
            case Token.NOT_EQUAL -> left.compareTo(right) != 0;
            case Token.GREATER_THAN -> left.compareTo(right) > 0;
            case Token.LESS_THAN -> left.compareTo(right) < 0;
            case Token.GREATER_THAN_OR_EQUAL -> left.compareTo(right) >= 0;
            case Token.LESS_THAN_OR_EQUAL -> left.compareTo(right) <= 0;
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
        if (position + offset >= tokens.size())
            return new Token(Token.EOF, null, currentPosition().getLine(), currentPosition().getColumn());
        return tokens.get(position + offset);
    }

    private ParseException unexpectedTokenException(String message) {
        return new ParseException(fileName, message,
                currentPosition().getLine(),
                currentPosition().getColumn(),
                getCurrentLine());
    }
}