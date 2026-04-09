package io.github._3xhaust.ezylang.parser;

import io.github._3xhaust.ezylang.ast.Ast.*;
import io.github._3xhaust.ezylang.exception.ParseException;
import io.github._3xhaust.ezylang.lexer.Lexer;
import io.github._3xhaust.ezylang.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private final String fileName;
    private final List<String> lines;
    private int current = 0;

    public Parser(String fileName, String sourceCode, List<Token> tokens) {
        this.fileName = fileName;
        this.tokens = tokens;
        this.lines = List.of(sourceCode.split("\n"));
    }

    public Program parse() throws ParseException {
        List<Node> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(parseStatement());
        }

        return new Program(statements);
    }

    private Node parseStatement() throws ParseException {
        if (match(Token.TokenType.FROM)) {
            return parseFromImportStatement();
        }
        if (match(Token.TokenType.RETURN)) {
            return parseReturnStatement();
        }
        if (match(Token.TokenType.IMPORT)) {
            return parseImportStatement();
        }
        if (match(Token.TokenType.MEMO)) {
            consume(Token.TokenType.FUNC, "Expected 'func' after 'memo'");
            return parseFunctionDecl(true);
        }
        if (match(Token.TokenType.FUNC)) {
            return parseFunctionDecl(false);
        }
        if (match(Token.TokenType.IDENTIFIER)) {
            Token identifierToken = previous();
            if (match(Token.TokenType.LEFT_BRACKET)) {
                Node index = parseExpression();
                consume(Token.TokenType.RIGHT_BRACKET, "Expected ']' after index expression");
                Node target = new ArrayAccess(identifierToken.getValue(), index, identifierToken.getLine(), identifierToken.getColumn());

                if (match(Token.TokenType.EQUAL, Token.TokenType.PLUS_EQUAL,
                        Token.TokenType.MINUS_EQUAL, Token.TokenType.ASTERISK_EQUAL,
                        Token.TokenType.SLASH_EQUAL, Token.TokenType.PERCENT_EQUAL)) {
                    Token operator = previous();
                    Node value = parseExpression();
                    return new AssignmentStatement(target, operator, value, target.getLine(), target.getColumn());
                } else {
                    return new ExpressionStatement(target, target.getLine(), target.getColumn());
                }
            } else if (check(Token.TokenType.LEFT_PAREN)) {
                return parseFunctionCall();
            } else if (match(Token.TokenType.DOT)) {
                Token methodToken = consume(Token.TokenType.IDENTIFIER, "Expected method name after '.'");
                consume(Token.TokenType.LEFT_PAREN, "Expected '(' after method name");
                List<Node> arguments = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                Node methodCall = new MethodCall(identifierToken.getValue(), methodToken.getValue(), arguments, null, identifierToken.getLine(), identifierToken.getColumn());
                return new ExpressionStatement(methodCall, methodCall.getLine(), methodCall.getColumn());
            } else if (check(Token.TokenType.COLON)) {
                return parseVariableDecl(false);
            } else if (match(Token.TokenType.EQUAL, Token.TokenType.PLUS_EQUAL,
                    Token.TokenType.MINUS_EQUAL, Token.TokenType.ASTERISK_EQUAL,
                    Token.TokenType.SLASH_EQUAL, Token.TokenType.PERCENT_EQUAL)) {
                Token operator = previous();
                Node value = parseExpression();
                Node target = new Identifier(identifierToken.getValue(), identifierToken.getLine(), identifierToken.getColumn());
                return new AssignmentStatement(target, operator, value, target.getLine(), target.getColumn());
            } else if (match(Token.TokenType.PLUS_PLUS, Token.TokenType.MINUS_MINUS)) {
                Token operator = previous();
                Node target = new Identifier(identifierToken.getValue(), identifierToken.getLine(), identifierToken.getColumn());
                return new IncrementDecrementExpr(target, operator, false, target.getLine(), target.getColumn());
            } else {
                return new ExpressionStatement(new Identifier(identifierToken.getValue(), identifierToken.getLine(), identifierToken.getColumn()), identifierToken.getLine(), identifierToken.getColumn());
            }
        }
        if (match(Token.TokenType.DOLLAR)) {
            return parseVariableDecl(true);
        }
        if (match(Token.TokenType.PRINT, Token.TokenType.PRINTLN)) {
            return parsePrintStatement();
        }
        if (match(Token.TokenType.SWITCH)) {
            return parseSwitchStatement();
        }
        if (match(Token.TokenType.BREAK)) {
            Token breakToken = previous();
            return new BreakStatement(breakToken.getLine(), breakToken.getColumn());
        }
        if (match(Token.TokenType.CONTINUE)) {
            Token continueToken = previous();
            return new ContinueStatement(continueToken.getLine(), continueToken.getColumn());
        }
        if (match(Token.TokenType.TEST)) {
            return parseTestBlock();
        }
        if (match(Token.TokenType.ASSERT)) {
            return parseAssertStatement();
        }
        if (match(Token.TokenType.IF)) {
            return parseIfStatement();
        }
        if (match(Token.TokenType.LEFT_BRACE)) {
            return parseBlock();
        }
        if (match(Token.TokenType.FOR)) {
            return parseForStatement();
        }
        if (match(Token.TokenType.WHILE)) {
            return parseWhileStatement();
        }

        Node expr = parseExpression();
        return new ExpressionStatement(expr, expr.getLine(), expr.getColumn());
    }

    private Node parseImportStatement() throws ParseException {
        Token importToken = previous();
        String moduleName = consume(Token.TokenType.IDENTIFIER, "Expected module name after 'import'").getValue();
        return new ImportStatement(moduleName, new ArrayList<>(), importToken.getLine(), importToken.getColumn());
    }

    private Node parseFromImportStatement() throws ParseException {
        String moduleName = consume(Token.TokenType.IDENTIFIER, "Expected module name after 'from'").getValue();
        consume(Token.TokenType.IMPORT, "Expected 'import' after module name");

        List<ImportItem> items = new ArrayList<>();

        if (match(Token.TokenType.ASTERISK)) {
            items.add(new ImportItem("*", null, false));
        } else {
            do {
                boolean isConstant = match(Token.TokenType.DOLLAR);
                String name;
                if (isConstant) {
                    name = consume(Token.TokenType.IDENTIFIER, "Expected identifier after '$'").getValue();
                } else {
                    name = consume(Token.TokenType.IDENTIFIER, "Expected identifier for import item").getValue();
                }
                String alias = null;
                if (match(Token.TokenType.AS)) {
                    alias = consume(Token.TokenType.IDENTIFIER, "Expected alias name after 'as'").getValue();
                }
                items.add(new ImportItem(name, alias, isConstant));
            } while (match(Token.TokenType.COMMA));
        }

        return new ImportStatement(moduleName, items, previous().getLine(), previous().getColumn());
    }

    private Node parseReturnStatement() throws ParseException {
        Token returnToken = previous();
        Node value = parseExpression();
        return new ReturnStatement(value, returnToken.getLine(), returnToken.getColumn());
    }

    private Node parseFunctionDecl(boolean isMemo) throws ParseException {
        String name = consume(Token.TokenType.IDENTIFIER, "Expected function name").getValue();
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after function name");

        List<String> paramNames = new ArrayList<>();
        List<Token> paramTypes = new ArrayList<>();
        List<Boolean> isArrayTypes = new ArrayList<>();
        if (!check(Token.TokenType.RIGHT_PAREN)) {
            do {
                String paramName = consume(Token.TokenType.IDENTIFIER, "Expected parameter name").getValue();
                consume(Token.TokenType.COLON, "Expected ':' after parameter name");
                Token paramType = peek();
                advance();
                boolean isArray = isArrayType();
                paramNames.add(paramName);
                paramTypes.add(paramType);
                isArrayTypes.add(isArray);
            } while (match(Token.TokenType.COMMA));
        }
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after parameters");

        Token returnType = null;
        Boolean isReturnArray = false;
        if (match(Token.TokenType.COLON)) {
            returnType = peek();
            advance();
            isReturnArray = isArrayType();
        }

        Node body = parseStatement();
        return new FunctionDecl(name, paramNames, paramTypes, isArrayTypes, returnType, isReturnArray, isMemo, body, previous().getLine(), previous().getColumn());
    }

    private Node parseFunctionCall() throws ParseException {
        Token nameToken = previous();
        String name = nameToken.getValue();
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after function name");

        List<Node> arguments = new ArrayList<>();
        if (!check(Token.TokenType.RIGHT_PAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(Token.TokenType.COMMA));
        }
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");

        return new FunctionCall(name, arguments, nameToken.getLine(), nameToken.getColumn());
    }

    private Node parseSwitchStatement() throws ParseException {
        Token switchToken = previous();
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'switch'");
        Node expression = parseExpression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after switch expression");
        consume(Token.TokenType.LEFT_BRACE, "Expected '{' after switch statement");

        List<SwitchCase> cases = new ArrayList<>();
        Node defaultCase = null;

        while (!check(Token.TokenType.RIGHT_BRACE) && !isAtEnd()) {
            if (match(Token.TokenType.CASE)) {
                Node value = parseExpression();

                boolean isArrowStyle = match(Token.TokenType.ARROW);
                if (!isArrowStyle) {
                    consume(Token.TokenType.COLON, "Expected ':' or '->' after case value");
                }

                Node body;
                if (isArrowStyle) {
                    body = parseStatement();
                } else {
                    if (match(Token.TokenType.LEFT_BRACE)) {
                        body = parseBlock();
                    } else {
                        body = parseStatement();
                    }
                }

                cases.add(new SwitchCase(value, body, isArrowStyle, value.getLine(), value.getColumn()));
            } else if (match(Token.TokenType.DEFAULT)) {
                if (defaultCase != null) {
                    throw new ParseException(fileName, "Switch statement can only have one default case", previous().getLine(), previous().getColumn(), getErrorLine(previous().getLine()));
                }

                consume(Token.TokenType.COLON, "Expected ':' after 'default'");

                if (match(Token.TokenType.LEFT_BRACE)) {
                    defaultCase = parseBlock();
                } else {
                    defaultCase = parseStatement();
                }
            } else {
                throw new ParseException(fileName, "Expected 'case' or 'default' in switch statement", peek().getLine(), peek().getColumn(), getErrorLine(peek().getLine()));
            }
        }

        consume(Token.TokenType.RIGHT_BRACE, "Expected '}' after switch cases");
        return new SwitchStatement(expression, cases, defaultCase, switchToken.getLine(), switchToken.getColumn());
    }

    private Node parseBlock() throws ParseException {
        List<Node> statements = new ArrayList<>();

        while (!check(Token.TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }

        consume(Token.TokenType.RIGHT_BRACE, "Expected '}' after block");
        return new Block(statements, previous().getLine(), previous().getColumn());
    }

    private Node parseWhileStatement() throws ParseException {
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'while'");
        Node condition = parseExpression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after condition");
        Node body = parseStatement();
        return new WhileStatement(condition, body, condition.getLine(), condition.getColumn());
    }

    private Node parseForStatement() throws ParseException {
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'for'");
        String identifier = consume(Token.TokenType.IDENTIFIER, "Expected identifier").getValue();
        consume(Token.TokenType.COLON, "Expected ':' after identifier");
        Token type = peek();
        advance();

        consume(Token.TokenType.IN, "Expected 'in' after type");

        if (!match(Token.TokenType.IDENTIFIER)) {
            Node start = parseExpression();
            consume(Token.TokenType.DOT_DOT, "Expected '..' after start expression");
            Node end = parseExpression();
            Node step = null;
            if (match(Token.TokenType.DOT_DOT)) {
                step = parseExpression();
            }
            consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after for expression");
            Node body = parseStatement();
            return new ForStatement(identifier, type, start, end, step, body, start.getLine(), start.getColumn());
        }

        Node start = new Identifier(previous().getValue(), previous().getLine(), previous().getColumn());
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after for expression");
        Node body = parseStatement();
        return new ForStatement(identifier, type, start, null, null, body, start.getLine(), start.getColumn());
    }

    private Node parseTestBlock() throws ParseException {
        Token testToken = previous();
        Token nameToken = consume(Token.TokenType.STRING_LITERAL, "Expected test name string after 'test'");
        consume(Token.TokenType.LEFT_BRACE, "Expected '{' after test name");
        Node body = parseBlock();
        return new TestBlock(nameToken.getValue(), body, testToken.getLine(), testToken.getColumn());
    }

    private Node parseAssertStatement() throws ParseException {
        Token assertToken = previous();
        Node expression = parseExpression();
        return new AssertStatement(expression, assertToken.getLine(), assertToken.getColumn());
    }

    private Node parseIfStatement() throws ParseException {
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'if'");
        Node condition = parseExpression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after condition");

        Node thenBranch = parseStatement();
        Node elseBranch = null;

        if (match(Token.TokenType.ELSE)) {
            elseBranch = parseStatement();
        }

        return new IfStatement(condition, thenBranch, elseBranch, condition.getLine(), condition.getColumn());
    }

    private Node parseVariableDecl(boolean isConstant) throws ParseException {
        if (isConstant) consume(Token.TokenType.IDENTIFIER, "Expected identifier after '$'");

        String identifier = previous().getValue();
        consume(Token.TokenType.COLON, "Expected ':' after identifier");

        Token typeToken = peek();
        advance();

        Constraint constraint = null;
        if (match(Token.TokenType.LEFT_PAREN)) {
            if (typeToken.getToken() == Token.TokenType.NUMBER) {
                Node min = parseExpression();
                consume(Token.TokenType.DOT_DOT, "Expected '..' in range constraint");
                Node max = parseExpression();
                constraint = new Constraint(min, max);
            } else if (typeToken.getToken() == Token.TokenType.STRING || typeToken.getToken() == Token.TokenType.CHAR) {
                List<Node> allowedValues = new ArrayList<>();
                do {
                    allowedValues.add(parseExpression());
                } while (match(Token.TokenType.COMMA));
                constraint = new Constraint(allowedValues);
            } else {
                throw new ParseException(fileName, "Constraints not supported for type '" + typeToken.getValue() + "'", typeToken.getLine(), typeToken.getColumn(), getErrorLine(typeToken.getLine()));
            }
            consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after constraint");
        }

        boolean isArray = isArrayType();

        consume(Token.TokenType.EQUAL, "Expected '=' after type");
        Node initializer = parseExpression();

        validateType(initializer, typeToken, isArray);

        return isConstant ? new ConstantDecl(identifier, typeToken, initializer, typeToken.getLine(), typeToken.getColumn()) :
                new VariableDecl(typeToken, identifier, isArray, initializer, constraint, typeToken.getLine(), typeToken.getColumn());
    }

    private boolean isArrayType() throws ParseException {
        if (match(Token.TokenType.LEFT_BRACKET)) {
            consume(Token.TokenType.RIGHT_BRACKET, "Expected ']' after '['");
            return true;
        }
        return false;
    }

    private void validateType(Node initializer, Token typeToken, boolean isArray) throws ParseException {
        String expectedType = isArray ? typeToken.getValue() + "[]" : typeToken.getValue();

        if (initializer instanceof ArrayLiteral arrayLiteral && !isValidArrayType(arrayLiteral, expectedType)) {
            throw new ParseException(
                    fileName,
                    "Type mismatch: expected " + expectedType + " but got " + arrayLiteral.getType(),
                    arrayLiteral.getLine(),
                    arrayLiteral.getColumn(),
                    getErrorLine(typeToken.getLine())
            );
        }

        if (initializer instanceof Literal literal && !literal.getType().equals(expectedType)) {
            throw new ParseException(
                    fileName,
                    "Type mismatch: expected " + expectedType + " but got " + literal.getType(),
                    literal.getLine(),
                    literal.getColumn(),
                    getErrorLine(typeToken.getLine())
            );
        }
    }

    private boolean isValidArrayType(ArrayLiteral arrayLiteral, String expectedType) {
        return arrayLiteral.getType().equals(expectedType);
    }

    private Node parsePrintStatement() throws ParseException {
        boolean isPrintln = previous().getToken() == Token.TokenType.PRINTLN;
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'print'");
        Node expression = parseStringInterpolation(parseExpression());
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after expression");
        return new PrintStatement(expression, isPrintln, expression.getLine(), expression.getColumn());
    }

    private Node parseStringInterpolation(Node expression) throws ParseException {
        if (!(expression instanceof Literal literal) || !literal.getType().equals("string")) {
            return expression;
        }

        String rawString = (String) literal.getValue();
        List<Node> interpolatedExpressions = new ArrayList<>();
        StringBuilder baseString = new StringBuilder();


        int startIndex = 0;
        while (true) {
            int dollarIndex = rawString.indexOf("${", startIndex);

            if (dollarIndex == -1) {
                baseString.append(rawString.substring(startIndex));
                break;
            }

            baseString.append(rawString, startIndex, dollarIndex);

            int endIndex = -1;
            int depth = 1;
            boolean inNestedString = false;
            for (int i = dollarIndex + 2; i < rawString.length(); i++) {
                char ch = rawString.charAt(i);
                if (ch == '"') {
                    inNestedString = !inNestedString;
                } else if (!inNestedString) {
                    if (ch == '{') depth++;
                    else if (ch == '}') {
                        depth--;
                        if (depth == 0) { endIndex = i; break; }
                    }
                }
            }
            if (endIndex == -1) {
                throw new ParseException(fileName, "Unclosed string interpolation expression", literal.getLine(), literal.getColumn(), getErrorLine(literal.getLine()));
            }

            String expressionString = rawString.substring(dollarIndex + 2, endIndex).trim();


            Lexer lexer = new Lexer(expressionString);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(this.fileName, expressionString, tokens);
            Node parsedExpression = parser.parseExpression();

            interpolatedExpressions.add(parsedExpression);

            startIndex = endIndex + 1;
        }

        if (interpolatedExpressions.isEmpty()) {
            return expression;
        } else {
            return new InterpolatedString(rawString, baseString.toString(), interpolatedExpressions, expression.getLine(), expression.getColumn());
        }
    }

    private Node parseExpression() throws ParseException {
        return parseBinaryExpression();
    }

    private Node parseBinaryExpression() throws ParseException {
        Node left = parseUnaryExpression();

        while (match(Token.TokenType.PLUS, Token.TokenType.MINUS, Token.TokenType.ASTERISK,
                Token.TokenType.SLASH, Token.TokenType.PERCENT, Token.TokenType.EQUAL_EQUAL,
                Token.TokenType.NOT_EQUAL, Token.TokenType.LESS_THAN, Token.TokenType.GREATER_THAN,
                Token.TokenType.LESS_THAN_OR_EQUAL, Token.TokenType.GREATER_THAN_OR_EQUAL,
                Token.TokenType.AND, Token.TokenType.OR, Token.TokenType.IS, Token.TokenType.AS)) {
            Token operator = previous();
            if (operator.getToken() == Token.TokenType.IS) {
                Token type = peek();
                advance();
                left = new TypeCheckExpr(left, type, left.getLine(), left.getColumn());
            } else if (operator.getToken() == Token.TokenType.AS) {
                Token type = peek();
                advance();
                left = new TypeCastExpr(left, type, left.getLine(), left.getColumn());
            } else if (isComparisonOperator(operator) && left instanceof BinaryExpr prevBinary && isComparisonOperator(prevBinary.getOperator())) {
                Node right = parseUnaryExpression();
                Node chainedRight = new BinaryExpr(prevBinary.getRight(), operator, right, operator.getLine(), operator.getColumn());
                Token andToken = new Token(Token.TokenType.AND, "&&", operator.getLine(), operator.getColumn());
                left = new BinaryExpr(left, andToken, chainedRight, left.getLine(), left.getColumn());
            } else {
                Node right = parseUnaryExpression();
                left = new BinaryExpr(left, operator, right, left.getLine(), left.getColumn());
            }
        }

        return left;
    }

    private boolean isComparisonOperator(Token token) {
        return switch (token.getToken()) {
            case LESS_THAN, GREATER_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL, EQUAL_EQUAL, NOT_EQUAL -> true;
            default -> false;
        };
    }

    private Node parseUnaryExpression() throws ParseException {
        if (match(Token.TokenType.MINUS, Token.TokenType.PLUS, Token.TokenType.BANG)) {
            Token operator = previous();
            Node operand = parseUnaryExpression();
            return new UnaryExpr(operator, operand, operator.getLine(), operator.getColumn());
        }

        if (match(Token.TokenType.PLUS_PLUS, Token.TokenType.MINUS_MINUS)) {
            Token operator = previous();
            Node operand = parseUnaryExpression();

            if (!(operand instanceof Identifier) && !(operand instanceof ArrayAccess)) {
                throw new ParseException(fileName,
                        "Invalid operand for prefix " + (operator.getToken() == Token.TokenType.PLUS_PLUS ? "increment" : "decrement") + " operator",
                        operand.getLine(), operand.getColumn(), getErrorLine(operand.getLine()));
            }

            return new IncrementDecrementExpr(operand, operator, true, operator.getLine(), operator.getColumn());
        }

        return parsePrimary();
    }

    private Node parsePrimary() throws ParseException {
        if (match(Token.TokenType.LEFT_BRACKET)) {
            return parseArrayLiteral();
        }
        if (match(Token.TokenType.NUMBER_LITERAL)) {
            Token token = previous();
            Node literal = new Literal(Double.parseDouble(token.getValue()), "number", token.getLine(), token.getColumn());
            if (match(Token.TokenType.DOT)) {
                Token methodToken = consume(Token.TokenType.IDENTIFIER, "Expected method name after '.'");
                consume(Token.TokenType.LEFT_PAREN, "Expected '(' after method name");
                List<Node> arguments = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                return new MethodCall(null, methodToken.getValue(), arguments, literal, token.getLine(), token.getColumn());
            }
            return literal;
        }
        if (match(Token.TokenType.STRING_LITERAL)) {
            Token token = previous();
            Node literal = token.getValue().length() == 1 ?
                    new Literal(token.getValue().charAt(0), "char", token.getLine(), token.getColumn()) :
                    new Literal(token.getValue(), "string", token.getLine(), token.getColumn());
            if (match(Token.TokenType.DOT)) {
                Token methodToken = consume(Token.TokenType.IDENTIFIER, "Expected method name after '.'");
                consume(Token.TokenType.LEFT_PAREN, "Expected '(' after method name");
                List<Node> arguments = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                return new MethodCall(null, methodToken.getValue(), arguments, literal, token.getLine(), token.getColumn());
            }
            return literal;
        }
        if (match(Token.TokenType.BOOLEAN_LITERAL)) {
            Token token = previous();
            Node literal = new Literal(Boolean.parseBoolean(token.getValue()), "boolean", token.getLine(), token.getColumn());
            if (match(Token.TokenType.DOT)) {
                Token methodToken = consume(Token.TokenType.IDENTIFIER, "Expected method name after '.'");
                consume(Token.TokenType.LEFT_PAREN, "Expected '(' after method name");
                List<Node> arguments = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                return new MethodCall(null, methodToken.getValue(), arguments, literal, token.getLine(), token.getColumn());
            }
            return literal;
        }
        if (match(Token.TokenType.IDENTIFIER)) {
            Token token = previous();
            Node expr;
            if (match(Token.TokenType.LEFT_PAREN)) {
                List<Node> arguments = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                expr = new FunctionCall(token.getValue(), arguments, token.getLine(), token.getColumn());
            } else if (match(Token.TokenType.LEFT_BRACKET)) {
                Node index = parseExpression();
                consume(Token.TokenType.RIGHT_BRACKET, "Expected ']' after index expression");
                expr = new ArrayAccess(token.getValue(), index, token.getLine(), token.getColumn());
            } else if (match(Token.TokenType.DOT)) {
                Token methodToken = consume(Token.TokenType.IDENTIFIER, "Expected method name after '.'");
                if (match(Token.TokenType.LEFT_PAREN)) {
                    List<Node> arguments = new ArrayList<>();
                    if (!check(Token.TokenType.RIGHT_PAREN)) {
                        do {
                            arguments.add(parseExpression());
                        } while (match(Token.TokenType.COMMA));
                    }
                    consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                    expr = new MethodCall(token.getValue(), methodToken.getValue(), arguments, null, token.getLine(), token.getColumn());
                } else {
                    expr = new MethodCall(token.getValue(), methodToken.getValue(), new ArrayList<>(), null, token.getLine(), token.getColumn());
                    ((MethodCall) expr).setPropertyAccess(true);
                }
            } else {
                expr = new Identifier(token.getValue(), token.getLine(), token.getColumn());
            }

            if (match(Token.TokenType.PLUS_PLUS, Token.TokenType.MINUS_MINUS)) {
                Token operator = previous();
                if (!(expr instanceof Identifier) && !(expr instanceof ArrayAccess)) {
                    throw new ParseException(fileName,
                            "Invalid operand for postfix " + (operator.getToken() == Token.TokenType.PLUS_PLUS ? "increment" : "decrement") + " operator",
                            expr.getLine(), expr.getColumn(), getErrorLine(expr.getLine()));
                }
                return new IncrementDecrementExpr(expr, operator, false, operator.getLine(), operator.getColumn());
            }

            return expr;
        }

        throw new ParseException(fileName, "Expected expression", peek().getLine(), peek().getColumn(), getErrorLine(peek().getLine()));
    }

    private Node parseArrayLiteral() throws ParseException {
        List<Node> elements = new ArrayList<>();

        while (!check(Token.TokenType.RIGHT_BRACKET)) {
            elements.add(parseExpression());
            if (!match(Token.TokenType.COMMA)) {
                break;
            }
        }

        consume(Token.TokenType.RIGHT_BRACKET, "Expected ']' at the end of array literal");
        return new ArrayLiteral(elements, previous().getLine(), previous().getColumn());
    }

    private String getErrorLine(int line) {
        if (line <= 0 || line > lines.size()) {
            return "";
        }
        return lines.get(line - 1);
    }

    private boolean match(Token.TokenType... types) {
        for (Token.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(Token.TokenType type, String message) throws ParseException {
        if (check(type)) return advance();

        Token token = peek();
        throw new ParseException(
                fileName,
                message,
                token.getLine(),
                token.getColumn(),
                getErrorLine(token.getLine())
        );
    }

    private boolean check(Token.TokenType type) {
        if (isAtEnd()) return false;
        return peek().getToken() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getToken() == Token.TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

}
