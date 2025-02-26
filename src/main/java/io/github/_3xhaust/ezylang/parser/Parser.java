package io.github._3xhaust.ezylang.parser;

import io.github._3xhaust.ezylang.exception.ParseException;
import io.github._3xhaust.ezylang.lexer.Lexer;
import io.github._3xhaust.ezylang.lexer.Token;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
        tokens.forEach(System.out::println);
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
        if (match(Token.TokenType.FUNC)) {
            return parseFunctionDecl();
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
            }
            else if (check(Token.TokenType.LEFT_PAREN)) {
                return parseFunctionCall();
            }
            else if (match(Token.TokenType.DOT)) {
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
            }
            else if (check(Token.TokenType.COLON)) {
                return parseVariableDecl(false);
            }
            else if (match(Token.TokenType.EQUAL, Token.TokenType.PLUS_EQUAL,
                    Token.TokenType.MINUS_EQUAL, Token.TokenType.ASTERISK_EQUAL,
                    Token.TokenType.SLASH_EQUAL, Token.TokenType.PERCENT_EQUAL)) {
                return parseAssignment();
            }
            else {
                Node expr = new Identifier(identifierToken.getValue(), identifierToken.getLine(), identifierToken.getColumn());
                return new ExpressionStatement(expr, expr.getLine(), expr.getColumn());
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

        List<ImportItem> items = new ArrayList<>();

        if (match(Token.TokenType.ASTERISK)) {
            items.add(new ImportItem("*", null, false));
        } else {
            do {
                boolean isConstant = match(Token.TokenType.DOLLAR); // $로 시작하는지 확인
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

        return new ImportStatement(moduleName, items, importToken.getLine(), importToken.getColumn());
    }

    private Node parseFromImportStatement() throws ParseException {
        String moduleName = consume(Token.TokenType.IDENTIFIER, "Expected module name after 'from'").getValue();
        consume(Token.TokenType.IMPORT, "Expected 'import' after module name");

        List<ImportItem> items = new ArrayList<>();

        if (match(Token.TokenType.ASTERISK)) {
            items.add(new ImportItem("*", null, false));
        } else {
            do {
                boolean isConstant = match(Token.TokenType.DOLLAR); // $로 시작하는지 확인
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

    private Node parseFunctionDecl() throws ParseException {
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
        return new FunctionDecl(name, paramNames, paramTypes, isArrayTypes, returnType, isReturnArray, body, previous().getLine(), previous().getColumn());
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

    private Node parseAssignment() throws ParseException {
        Node target = parsePrimary();

        Token operator;
        if (match(Token.TokenType.EQUAL)) {
            operator = previous();
        } else if (match(Token.TokenType.PLUS_EQUAL, Token.TokenType.MINUS_EQUAL,
                Token.TokenType.ASTERISK_EQUAL, Token.TokenType.SLASH_EQUAL,
                Token.TokenType.PERCENT_EQUAL)) {
            operator = previous();
        } else {
            throw new ParseException(fileName, "Expected '=' or a compound assignment operator", peek().getLine(), peek().getColumn(), getErrorLine(peek().getLine()));
        }

        Node value = parseExpression();

        if (!(target instanceof Identifier) && !(target instanceof ArrayAccess)) {
            throw new ParseException(fileName, "Invalid assignment target", target.getLine(), target.getColumn(), getErrorLine(target.getLine()));
        }

        return new AssignmentStatement(target, operator, value, target.getLine(), target.getColumn());
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

        boolean isArray = isArrayType();

        consume(Token.TokenType.EQUAL, "Expected '=' after type");
        Node initializer = parseExpression();

        validateType(initializer, typeToken, isArray);

        return isConstant ? new ConstantDecl(identifier, typeToken, initializer, typeToken.getLine(), typeToken.getColumn()) :
                new VariableDecl(typeToken, identifier, isArray, initializer, typeToken.getLine(), typeToken.getColumn());
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

            int endIndex = rawString.indexOf("}", dollarIndex + 2);
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
            } else {
                Node right = parseUnaryExpression();
                left = new BinaryExpr(left, operator, right, left.getLine(), left.getColumn());
            }
        }

        return left;
    }

    private Node parseUnaryExpression() throws ParseException {
        if (match(Token.TokenType.MINUS, Token.TokenType.PLUS)) {
            Token operator = previous();
            Node operand = parseUnaryExpression();
            return new UnaryExpr(operator, operand, operator.getLine(), operator.getColumn());
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
            Node literal = new Literal(token.getValue(), "string", token.getLine(), token.getColumn());
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
            if (match(Token.TokenType.LEFT_PAREN)) {
                List<Node> arguments = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after arguments");
                return new FunctionCall(token.getValue(), arguments, token.getLine(), token.getColumn());
            }
            if (match(Token.TokenType.LEFT_BRACKET)) {
                Node index = parseExpression();
                consume(Token.TokenType.RIGHT_BRACKET, "Expected ']' after index expression");
                return new ArrayAccess(token.getValue(), index, token.getLine(), token.getColumn());
            }
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
                return new MethodCall(token.getValue(), methodToken.getValue(), arguments, null, token.getLine(), token.getColumn());
            }
            return new Identifier(token.getValue(), token.getLine(), token.getColumn());
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

    public interface Visitor<R> {
        R visitProgram(Program program) throws ParseException;

        R visitVariableDecl(VariableDecl variableDecl) throws ParseException;

        R visitConstantDecl(ConstantDecl constantDecl) throws ParseException;

        R visitPrintStatement(PrintStatement printStatement) throws ParseException;

        R visitBinaryExpr(BinaryExpr binaryExpr) throws ParseException;

        R visitLiteral(Literal literal);

        R visitIdentifier(Identifier identifier) throws ParseException;

        R visitArrayAccess(ArrayAccess arrayAccess) throws ParseException;

        R visitIfStatement(IfStatement ifStatement) throws ParseException;

        R visitForStatement(ForStatement forStatement) throws ParseException;

        R visitArrayLiteral(ArrayLiteral arrayLiteral) throws ParseException;

        R visitBlock(Block block) throws ParseException;

        R visitUnaryExpr(UnaryExpr unaryExpr) throws ParseException;

        R visitInterpolatedString(InterpolatedString interpolatedString) throws ParseException;

        R visitWhileStatement(WhileStatement whileStatement) throws ParseException;

        R visitAssignmentStatement(AssignmentStatement assignmentStatement) throws ParseException;

        R visitTypeCastExpr(TypeCastExpr typeCastExpr) throws ParseException;

        R visitTypeCheckExpr(TypeCheckExpr typeCheckExpr) throws ParseException;

        R visitContinueStatement(ContinueStatement continueStatement) throws ParseException;

        R visitBreakStatement(BreakStatement breakStatement) throws ParseException;

        R visitSwitchCase(SwitchCase switchCase) throws ParseException;

        R visitSwitchStatement(SwitchStatement switchStatement) throws ParseException;

        R visitFunctionDecl(FunctionDecl functionDecl) throws ParseException;

        R visitFunctionCall(FunctionCall functionCall) throws ParseException;

        R visitMethodCall(MethodCall methodCall) throws ParseException;

        R visitExpressionStatement(ExpressionStatement expressionStatement) throws ParseException;

        R visitImportStatement(ImportStatement importStatement) throws ParseException;

        R visitReturnStatement(ReturnStatement returnStatement) throws ParseException;
    }

    @Getter
    public abstract static class Node {
        protected int line;
        protected int column;

        public abstract <R> R accept(Visitor<R> visitor) throws ParseException;
    }

    @Getter
    public static class Program extends Node {
        private final List<Node> statements;

        Program(List<Node> statements) {
            this.statements = statements;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitProgram(this);
        }
    }

    @Getter
    public static class VariableDecl extends Node {
        private final Token type;
        private final String identifier;
        private final boolean isArray;
        private final Node initializer;

        VariableDecl(Token type, String identifier, boolean isArray, Node initializer, int line, int column) {
            this.type = type;
            this.identifier = identifier;
            this.isArray = isArray;
            this.initializer = initializer;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitVariableDecl(this);
        }
    }

    @Getter
    public static class ConstantDecl extends Node {
        private final String identifier;
        private final Token type;
        private final Node value;

        ConstantDecl(String identifier, Token type, Node value, int line, int column) {
            this.identifier = identifier;
            this.type = type;
            this.value = value;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitConstantDecl(this);
        }
    }

    @Getter
    public static class PrintStatement extends Node {
        private final Node expression;
        private final boolean isPrintln;

        PrintStatement(Node expression, boolean isPrintln, int line, int column) {
            this.expression = expression;
            this.isPrintln = isPrintln;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitPrintStatement(this);
        }
    }

    @Getter
    public static class IfStatement extends Node {
        private final Node condition;
        private final Node thenBranch;
        private final Node elseBranch;

        IfStatement(Node condition, Node thenBranch, Node elseBranch, int line, int column) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitIfStatement(this);
        }
    }

    @Getter
    public static class ForStatement extends Node {
        private final String identifier;
        private final Token type;
        private final Node start;
        private final Node end;
        private final Node step;
        private final Node body;

        ForStatement(String identifier, Token type, Node start, Node end, Node step, Node body, int line, int column) {
            this.identifier = identifier;
            this.type = type;
            this.start = start;
            this.end = end;
            this.step = step;
            this.body = body;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitForStatement(this);
        }
    }

    @Getter
    public static class BinaryExpr extends Node {
        private final Node left;
        private final Token operator;
        private final Node right;

        BinaryExpr(Node left, Token operator, Node right, int line, int column) {
            this.left = left;
            this.operator = operator;
            this.right = right;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitBinaryExpr(this);
        }
    }

    @Getter
    public static class Literal extends Node {
        private final Object value;
        private final String type;

        public Literal(Object value, String type, int line, int column) {
            this.value = value;
            this.type = type;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteral(this);
        }
    }

    @Getter
    public static class Identifier extends Node {
        private final String name;

        Identifier(String name, int line, int column) {
            this.name = name;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitIdentifier(this);
        }
    }

    @Getter
    public static class ArrayAccess extends Node {
        private final String identifier;
        private final Node index;

        ArrayAccess(String identifier, Node index, int line, int column) {
            this.identifier = identifier;
            this.index = index;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitArrayAccess(this);
        }
    }

    @Getter
    public static class ArrayLiteral extends Node {
        private final List<Node> elements;

        public ArrayLiteral(List<Node> elements, int line, int column) {
            this.elements = elements;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitArrayLiteral(this);
        }

        public String getType() {
            if (elements.isEmpty()) {
                return "unknown[]";
            }

            if (elements.get(0) instanceof Literal literal) {
                String firstElementType = literal.getType();

                for (Node element : elements) {
                    if (element instanceof Literal literalElement) {
                        String currentType = literalElement.getType();

                        if (!currentType.equals(firstElementType)) {
                            throw new RuntimeException("Array elements must have the same type");
                        }
                    }
                }

                return firstElementType + "[]";
            }

            return null;
        }
    }

    @Getter
    public static class Block extends Node {
        private final List<Node> statements;

        public Block(List<Node> statements, int line, int column) {
            this.statements = statements;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitBlock(this);
        }

        public List<Node> getStatements() {
            return Collections.unmodifiableList(statements);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Block {\n");
            for (Node statement : statements) {
                sb.append("  ").append(statement.toString().replace("\n", "\n  ")).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Block block = (Block) o;
            return Objects.equals(statements, block.statements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(statements);
        }
    }

    @Getter
    public static class UnaryExpr extends Node {
        private final Token operator;
        private final Node operand;

        UnaryExpr(Token operator, Node operand, int line, int column) {
            this.operator = operator;
            this.operand = operand;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitUnaryExpr(this);
        }
    }

    @Getter
    public static class InterpolatedString extends Node {
        private final String rawString;
        private final String baseString;
        private final List<Node> expressions;

        InterpolatedString(String rawString, String baseString, List<Node> expressions, int line, int column) {
            this.rawString = rawString;
            this.baseString = baseString;
            this.expressions = expressions;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitInterpolatedString(this);
        }
    }

    @Getter
    public static class WhileStatement extends Node {
        private final Node condition;
        private final Node body;

        WhileStatement(Node condition, Node body, int line, int column) {
            this.condition = condition;
            this.body = body;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitWhileStatement(this);
        }
    }

    @Getter
    public static class AssignmentStatement extends Node {
        private final Node target;
        private final Token operator;
        private final Node value;

        AssignmentStatement(Node target, Token operator, Node value, int line, int column) {
            this.target = target;
            this.operator = operator;
            this.value = value;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitAssignmentStatement(this);
        }
    }

    @Getter
    public static class TypeCheckExpr extends Node {
        private final Node expression;
        private final Token type;

        TypeCheckExpr(Node expression, Token type, int line, int column) {
            this.expression = expression;
            this.type = type;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitTypeCheckExpr(this);
        }
    }

    @Getter
    public static class TypeCastExpr extends Node {
        private final Node expression;
        private final Token targetType;

        TypeCastExpr(Node expression, Token targetType, int line, int column) {
            this.expression = expression;
            this.targetType = targetType;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitTypeCastExpr(this);
        }
    }

    @Getter
    public static class BreakStatement extends Node {
        BreakStatement(int line, int column) {
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitBreakStatement(this);
        }
    }

    @Getter
    public static class ContinueStatement extends Node {
        ContinueStatement(int line, int column) {
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitContinueStatement(this);
        }
    }

    @Getter
    public static class SwitchStatement extends Node {
        private final Node expression;
        private final List<SwitchCase> cases;
        private final Node defaultCase;

        SwitchStatement(Node expression, List<SwitchCase> cases, Node defaultCase, int line, int column) {
            this.expression = expression;
            this.cases = cases;
            this.defaultCase = defaultCase;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitSwitchStatement(this);
        }
    }

    @Getter
    public static class SwitchCase extends Node {
        private final Node value;
        private final Node body;
        private final boolean isArrowStyle;

        SwitchCase(Node value, Node body, boolean isArrowStyle, int line, int column) {
            this.value = value;
            this.body = body;
            this.isArrowStyle = isArrowStyle;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitSwitchCase(this);
        }
    }

    @Getter
    public static class FunctionDecl extends Node {
        private final String name;
        private final List<String> paramNames;
        private final List<Token> paramTypes;
        private final List<Boolean> isArrayTypes;
        private final Token returnType;
        private final Boolean isReturnTypeArray;
        private final Node body;

        public FunctionDecl(String name, List<String> paramNames, List<Token> paramTypes, List<Boolean> isArrayTypes, Token returnType, Boolean isReturnTypeArray, Node body, int line, int column) {
            this.name = name;
            this.paramNames = paramNames;
            this.paramTypes = paramTypes;
            this.isArrayTypes = isArrayTypes;
            this.returnType = returnType;
            this.isReturnTypeArray = isReturnTypeArray;
            this.body = body;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitFunctionDecl(this);
        }
    }

    @Getter
    public static class FunctionCall extends Node {
        private final String name;
        private final List<Node> arguments;

        public FunctionCall(String name, List<Node> arguments, int line, int column) {
            this.name = name;
            this.arguments = arguments;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitFunctionCall(this);
        }
    }

    public static class MethodCall extends Node {
        private final String objectName;
        private final String methodName;
        private final List<Node> arguments;
        private final Node objectNode;

        public MethodCall(String objectName, String methodName, List<Node> arguments, Node objectNode, int line, int column) {
            this.objectName = objectName;
            this.methodName = methodName;
            this.arguments = arguments;
            this.objectNode = objectNode;
            this.line = line;
            this.column = column;
        }

        public String getObjectName() { return objectName; }
        public String getMethodName() { return methodName; }
        public List<Node> getArguments() { return arguments; }
        public Node getObjectNode() { return objectNode; }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitMethodCall(this);
        }
    }

    @Getter
    public static class ExpressionStatement extends Node {
        private final Node expression;

        public ExpressionStatement(Node expression, int line, int column) {
            this.expression = expression;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitExpressionStatement(this);
        }
    }

    @Getter
    public static class ImportStatement extends Node {
        private final String moduleName;
        private final List<ImportItem> items;

        public ImportStatement(String moduleName, List<ImportItem> items, int line, int column) {
            this.moduleName = moduleName;
            this.items = items;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitImportStatement(this);
        }
    }

    @Getter
    public static class ImportItem {
        private final String name;
        private final String alias;
        private final boolean isConstant;

        public ImportItem(String name, String alias, boolean isConstant) {
            this.name = name;
            this.alias = alias;
            this.isConstant = isConstant;
        }
    }

    @Getter
    public static class ReturnStatement extends Node {
        private final Node value;

        ReturnStatement(Node value, int line, int column) {
            this.value = value;
            this.line = line;
            this.column = column;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) throws ParseException {
            return visitor.visitReturnStatement(this);
        }
    }
}