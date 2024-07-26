package io.github._3xhaust.parser;

import io.github._3xhaust.exception.ParseException;
import io.github._3xhaust.token.Token;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

public class Parser {
    private static class FunctionInfo {
        String name;
        List<Token> parameters;
        String returnType;
        List<Token> body;

        public FunctionInfo(String name, List<Token> parameters, String returnType, List<Token> body) {
            this.name = name;
            this.parameters = parameters;
            this.returnType = returnType;
            this.body = body;
        }
    }

    private final Map<String, FunctionInfo> functions = new HashMap<>();
    private final List<Token> tokens;
    private final String fileName;
    private final String[] lines;
    private int position = 0;

    private final Deque<Map<String, Object>> scopes = new LinkedList<>();
    private final Map<String, String> variableTypes = new HashMap<>();
    private final Set<String> constants = new HashSet<>();

    public Parser(List<Token> tokens, String fileName, String input) {
        this.fileName = fileName;
        this.lines = input.split("\n");
        this.tokens = tokens;
        // 전역 스코프 초기화
        scopes.push(new HashMap<>());
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
            case Token.FUNC -> functionDeclaration();
            case Token.FOR -> forStatement();
            case Token.IF -> ifStatement();
            case Token.RETURN -> returnStatement();
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

    private void functionDeclaration() throws ParseException {
        consume(Token.FUNC);
        String functionName = consume(Token.IDENTIFIER).getValue();
        consume(Token.LEFT_PAREN);

        List<Token> parameters = new ArrayList<>();
        while (!currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
            parameters.add(consume(Token.IDENTIFIER));
            if (currentPosition().getToken().equals(Token.COMMA)) {
                consume(Token.COMMA);
            }
        }
        consume(Token.RIGHT_PAREN);

        consume(Token.COLON);
        String returnType = consume(currentPosition().getToken()).getToken();

        consume(Token.LEFT_BRACE);
        List<Token> body = new ArrayList<>();
        while (!currentPosition().getToken().equals(Token.RIGHT_BRACE)) {
            body.add(currentPosition());
            position++;
        }
        consume(Token.RIGHT_BRACE);

        functions.put(functionName, new FunctionInfo(functionName, parameters, returnType, body));
    }

    private void variableDeclaration() throws ParseException {
        boolean isConstant = currentPosition().getToken().equals(Token.DOLLAR);
        if (isConstant) consume(Token.DOLLAR);

        String variableName = consume(Token.IDENTIFIER).getValue();
        ensureVariableNotDeclaredLocal(variableName);

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
        Object value = getTypedValue(type);

        if (isConstant) {
            constants.add(variableName);
        }
        getCurrentScope().put(variableName, value);
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

        if (isConstant) {
            constants.add(variableName);
        }
        getCurrentScope().put(variableName, array);
    }

    private Object parseArrayElement(String arrayType) throws ParseException {
        String elementType = arrayType.replace(" array", "");
        return getTypedValue(elementType);
    }

    private void ensureVariableNotDeclaredLocal(String variableName) throws ParseException {
        if (getCurrentScope().containsKey(variableName)) {
            throw new ParseException(fileName, "Variable '" + variableName + "' already declared in this scope",
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
            Object result = expression();

            if ((type.equals(Token.NUMBER) && !(result instanceof BigDecimal)) ||
                    (type.equals(Token.STRING) && !(result instanceof String)) ||
                    (type.equals(Token.BOOLEAN) && !(result instanceof Boolean)) ||
                    (type.equals(Token.CHAR) && !(result instanceof Character)) ||
                    (type.equals(Token.NULL) && result != null)) {
                throw new ParseException(fileName, "Type mismatch: Expected " + type.toLowerCase() + ", found " + result.getClass().getSimpleName(),
                        currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
            }

            return result;
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

        if (isConstantDeclared(variableName)) {
            throw new ParseException(fileName, "Cannot reassign constant variable: " + variableName,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        Object value = expression();
        if (!assignVariableIfDeclared(variableName, value)) {
            throw new ParseException(fileName, "Undefined variable: " + variableName,
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }
    }

    private void assignValueAtIndex(String arrayName, int index, Object value) throws ParseException {
        Object array = getVariableValue(arrayName);

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
        if (!isVariableDeclared(variableName)) {
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

        Object arrayObject = getVariableValue(arrayVariable);
        if (!(arrayObject instanceof List<?> array)) {
            throw new ParseException(fileName, "Variable '" + arrayVariable + "' is not an array",
                    currentPosition().getLine(),
                    currentPosition().getColumn(),
                    getCurrentLine());
        }

        enterScope(); // for 루프 스코프 시작

        int forLoopStartPosition = position;
        for (Object element : array) {
            position = forLoopStartPosition;
            checkType(element, type);
            getCurrentScope().put(variable, element);
            executeForLoopBody();
        }

        exitScope(); // for 루프 스코프 종료
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

        enterScope(); // for 루프 스코프 시작

        int forLoopStartPosition = position;
        if (step.compareTo(BigDecimal.ZERO) > 0) {
            for (BigDecimal i = start; i.compareTo(end) <= 0; i = i.add(step)) {
                position = forLoopStartPosition;
                checkType(i, type);
                getCurrentScope().put(variable, i);
                executeForLoopBody();
            }
        } else {
            for (BigDecimal i = start; i.compareTo(end) >= 0; i = i.add(step)) {
                position = forLoopStartPosition;
                checkType(i, type);
                getCurrentScope().put(variable, i);
                executeForLoopBody();
            }
        }

        exitScope(); // for 루프 스코프 종료
    }

    private BigDecimal getRangeStart() throws ParseException {
        if (currentPosition().getToken().equals(Token.DOT_LENGTH)) {
            consume(Token.DOT_LENGTH);
            String arrayVariable = consume(Token.IDENTIFIER).getValue();

            Object arrayObject = getVariableValue(arrayVariable);
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

        List<Object> printArgs = new ArrayList<>();
        while (!currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
            printArgs.add(expression());
            if (currentPosition().getToken().equals(Token.COMMA)) {
                consume(Token.COMMA);
            }
        }

        consume(Token.RIGHT_PAREN);

        for (Object arg : printArgs) {
            String output = (arg instanceof String) ? (String) arg : String.valueOf(arg);
            System.out.print(output);
        }

        if (ln) {
            System.out.println();
        }
    }

    private void ifStatement() throws ParseException {
        consume(Token.IF);
        consume(Token.LEFT_PAREN);
        boolean condition = evaluateCondition();
        consume(Token.RIGHT_PAREN);

        if (condition) {
            enterScope();  // if 조건문 스코프 시작
            executeConditionalBlock();
            exitScope();  // if 조건문 스코프 종료
        } else {
            skipConditionalBlock();
        }

        handleElseIf(condition);
        handleElse(condition);
    }

    private void handleElseIf(boolean previousConditionMet) throws ParseException {
        while (currentPosition().getToken().equals(Token.ELSE_IF)) {
            consume(Token.ELSE_IF);
            consume(Token.LEFT_PAREN);
            boolean condition = evaluateCondition();
            consume(Token.RIGHT_PAREN);

            if (!previousConditionMet && condition) {
                enterScope();  // else if 조건문 스코프 시작
                executeConditionalBlock();
                exitScope();  // else if 조건문 스코프 종료
                previousConditionMet = true;
            } else {
                skipConditionalBlock();
            }
        }
    }

    private void handleElse(boolean previousConditionMet) throws ParseException {
        if (currentPosition().getToken().equals(Token.ELSE)) {
            consume(Token.ELSE);

            if (!previousConditionMet) {
                enterScope();  // else 블록 스코프 시작
                executeConditionalBlock();
                exitScope();  // else 블록 스코프 종료
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

    private void executeConditionalBlock() throws ParseException {
        if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
            consume(Token.LEFT_BRACE);
            block();
            consume(Token.RIGHT_BRACE);
        } else {
            statement();
        }
    }

    private void skipConditionalBlock() {
        if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
            int braceCount = 1;
            position++;
            while (braceCount > 0 && !isAtEnd()) {
                if (currentPosition().getToken().equals(Token.LEFT_BRACE)) {
                    braceCount++;
                } else if (currentPosition().getToken().equals(Token.RIGHT_BRACE)) {
                    braceCount--;
                }
                position++;
            }
        } else {
            skipStatement();
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
        enterScope();  // 블록 스코프 시작
        while (!currentPosition().getToken().equals(Token.RIGHT_BRACE) && !isAtEnd()) {
            statement();
        }
        exitScope();  // 블록 스코프 종료
    }

    private void enterScope() {
        scopes.push(new HashMap<>());
    }

    private void exitScope() {
        scopes.pop();
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
        String charValue = consume(Token.CHAR_LITERAL).getValue();
        if (charValue.length() != 1) {
            throw new ParseException(fileName, "Invalid char literal: " + charValue,
                    currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
        }
        return charValue.charAt(0);
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
        Object left = term();
        while (currentPosition().getToken().equals(Token.PLUS) ||
                currentPosition().getToken().equals(Token.MINUS)) {
            String operator = currentPosition().getToken();
            consume(operator);
            Object right = term();
            left = applyOperator(left, operator, right);
        }
        return left;
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
                if (currentPosition().getToken().equals(Token.LEFT_PAREN)) {  // 함수 호출
                    return functionCall(identifier);
                } else if (currentPosition().getToken().equals(Token.LEFT_BRACKET)) {
                    consume(Token.LEFT_BRACKET);
                    int index = ((BigDecimal) expression()).intValue();
                    consume(Token.RIGHT_BRACKET);
                    yield getVariableValueAtIndex(identifier, index);
                } else if (currentPosition().getToken().equals(Token.DOT_LENGTH)) {
                    consume(Token.DOT_LENGTH);
                    yield getArrayLength(identifier);
                } else {
                    yield getVariableValue(identifier);
                }
            }
            default -> throw unexpectedTokenException("Unexpected token in factor");
        };
    }

    private Object functionCall(String functionName) throws ParseException {
        consume(Token.LEFT_PAREN);

        List<Object> arguments = new ArrayList<>();
        while (!currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
            arguments.add(expression());
            if (currentPosition().getToken().equals(Token.COMMA)) {
                consume(Token.COMMA);
            }
        }
        consume(Token.RIGHT_PAREN);

        return executeFunction(functionName, arguments);
    }

    private Object executeFunction(String functionName, List<Object> arguments) throws ParseException {
        FunctionInfo function = functions.get(functionName);
        if (function == null) {
            throw new ParseException(fileName, "Undefined function: " + functionName,
                    currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
        }

        if (arguments.size() != function.parameters.size()) {
            throw new ParseException(fileName, "Incorrect number of arguments passed to function: " + functionName,
                    currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
        }

        enterScope(); // 함수 스코프 생성
        for (int i = 0; i < arguments.size(); i++) {
            // 함수 매개변수를 현재 스코프에 추가
            getCurrentScope().put(function.parameters.get(i).getValue(), arguments.get(i));
        }

        // 함수 바디 실행
        int returnPosition = position; // 현재 위치 저장
        position = 0;
        List<Token> functionBody = new ArrayList<>(function.body);
        Object returnValue = null;

        try {
            while (position < functionBody.size()) {
                Token current = functionBody.get(position);
                if (current.getToken().equals(Token.RETURN)) {
                    position++;
                    returnValue = expression();
                    break;
                } else {
                    statementFromTokens(functionBody);
                }
            }
        } catch (ParseException e) {
            throw new ParseException(fileName, e.getMessage(), e.getLine(), e.getColumn(), lines[e.getLine() - 1]);
        } finally {
            position = returnPosition; // 원래 위치로 복귀
            exitScope();  // 함수 스코프 종료
        }

        // 반환 타입 확인
        if (returnValue == null && !function.returnType.equals(Token.VOID)) {
            throw new ParseException(fileName, "Function " + functionName + " should return a value of type " + function.returnType,
                    currentPosition().getLine(), currentPosition().getColumn(), getCurrentLine());
        }

        // 반환 값 타입 체크
        if (returnValue != null) {
            checkTypeCompatibility(returnValue, function.returnType);
        }

        return returnValue;
    }
    private void statementFromTokens(List<Token> body) throws ParseException {
        // ... 기존 statement() 메서드와 동일한 로직 ...
        // 단, currentPosition() 대신 body.get(position) 사용
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
        Object variable = getVariableValue(variableName);
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
        // Iterate through scopes from the most recent to the global scope
        Iterator<Map<String, Object>> iterator = scopes.descendingIterator();
        while (iterator.hasNext()) {
            Map<String, Object> scope = iterator.next();
            if (scope.containsKey(variableName)) {
                return scope.get(variableName);
            }
        }

        throw new ParseException(fileName, "Undefined variable: " + variableName,
                currentPosition().getLine(),
                currentPosition().getColumn(),
                getCurrentLine());
    }

    private boolean isVariableDeclared(String variableName) {
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(variableName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConstantDeclared(String variableName) {
        return constants.contains(variableName);
    }

    private boolean assignVariableIfDeclared(String variableName, Object value) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Map<String, Object> scope = scopes.getLast();
            if (scope.containsKey(variableName)) {
                scope.put(variableName, value);
                return true;
            }
        }
        return false;
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
                token.equals(Token.LESS_THAN_OR_EQUAL) ||
                token.equals(Token.AND) ||
                token.equals(Token.OR);
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

    private Map<String, Object> getCurrentScope() {
        return scopes.peek();
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