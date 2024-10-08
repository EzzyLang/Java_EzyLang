package io.github._3xhaust.lexer;

import io.github._3xhaust.exception.ParseException;
import io.github._3xhaust.token.Token;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private final List<Token> tokens = new ArrayList<>();
    private int position = 0;
    private int line = 1;
    private int column = 1;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() throws ParseException {
        while (position < input.length()) {
            char current = input.charAt(position);

            if (Character.isDigit(current)) {
                tokenizeNumber();
            } else if (current == '\"' || current == '\'') {
                tokenizeStringLiteral(current);
            } else if (Character.isLetter(current) || current == '_') {
                tokenizeIdentifierOrKeyword();
            } else if (current == '\n' || current == '\r') {
                handleNewline();
            }
            else {
                tokenizeSymbol(current);
            }
        }

        tokens.add(new Token(Token.EOF, null, line, column));
        return tokens;
    }

    private void tokenizeNumber() {
        StringBuilder numberLiteral = new StringBuilder();
        int startColumn = column;
        boolean hasDecimalPoint = false;

        while (position < input.length()) {
            char currentChar = input.charAt(position);

            if (currentChar == '.' && !hasDecimalPoint) {
                if (peek(1) == '.') break;
                hasDecimalPoint = true;
            } else if (!Character.isDigit(currentChar)) {
                break;
            }

            numberLiteral.append(currentChar);
            position++;
            column++;
        }

        tokens.add(new Token(Token.NUMBER_LITERAL, numberLiteral.toString(), line, startColumn));
    }


    private void tokenizeStringLiteral(char quote) throws ParseException {
        StringBuilder stringLiteral = new StringBuilder();
        int startColumn = column;
        position++; // skip starting quote
        column++;

        while (position < input.length() && input.charAt(position) != quote) {
            if (input.charAt(position) == '\\') {
                handleEscapeCharacter(stringLiteral);
            } else if (input.charAt(position) == '$' && peek(1) == '{') {
                handleVariableInString(stringLiteral, startColumn);
            } else {
                stringLiteral.append(input.charAt(position));
                position++;
                column++;
            }
        }
        position++; // skip ending quote
        column++;

        if (quote == '\'') {
            tokens.add(new Token(Token.CHAR_LITERAL, stringLiteral.toString(), line, startColumn));
        } else if (quote == '\"') {
            tokens.add(new Token(Token.STRING_LITERAL, stringLiteral.toString(), line, startColumn));
        }
    }

    private void handleEscapeCharacter(StringBuilder stringLiteral) {
        position++; // skip escape character
        column++;
        if (position < input.length()) {
            stringLiteral.append(getEscapedCharacter(input.charAt(position)));
            position++;
            column++;
        }
    }

    private void handleVariableInString(StringBuilder stringLiteral, int startColumn) {
        if (!stringLiteral.isEmpty()) {
            tokens.add(new Token(Token.STRING_LITERAL, stringLiteral.toString(), line, startColumn));
            stringLiteral.setLength(0);
        }
        position += 2; // skip ${
        column += 2;
        tokenizeVariableLiteral();
    }

    private void tokenizeVariableLiteral() {
        int startColumn = column;
        StringBuilder variableName = new StringBuilder();
        while (position < input.length() && input.charAt(position) != '}') {
            variableName.append(input.charAt(position));
            position++;
            column++;
        }
        position++; // skip ending }
        column++;
        tokens.add(new Token(Token.VARIABLE_LITERAL, variableName.toString(), line, startColumn));
    }

    private void tokenizeIdentifierOrKeyword() {
        StringBuilder sb = new StringBuilder();
        int startColumn = column;
        while (position < input.length() && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
            sb.append(input.charAt(position++));
            column++;
        }
        String word = sb.toString();

        int arrayDimension = checkArrayDimension();

        if (arrayDimension > 0) {
            word += " array".repeat(arrayDimension);
            tokens.add(new Token(word.toLowerCase(), word, line, startColumn));
            return;
        }

        switch (word) {
            case "number", "char", "string", "boolean", "null", "void" -> tokens.add(new Token(word.toLowerCase(), word, line, startColumn));
            case "true", "false" -> tokens.add(new Token(Token.BOOLEAN_LITERAL, word, line, startColumn));
            case "println" -> tokens.add(new Token(Token.PRINTLN, word, line, startColumn));
            case "print" -> tokens.add(new Token(Token.PRINT, word, line, startColumn));
            case "if" -> tokens.add(new Token(Token.IF, word, line, startColumn));
            case "else" -> handleElseToken(word, startColumn);
            case "is" -> tokens.add(new Token(Token.IS, word, line, startColumn));
            case "for" -> tokens.add(new Token(Token.FOR, word, line, startColumn));
            case "func" -> tokens.add(new Token(Token.FUNC, word, line, startColumn));
            case "return" -> tokens.add(new Token(Token.RETURN, word, line, startColumn));
            case "in" -> tokens.add(new Token(Token.IN, word, line, startColumn));
            case "array" -> tokens.add(new Token(Token.ARRAY, word, line, startColumn));
            case "break" -> tokens.add(new Token(Token.BREAK, word, line, startColumn));
            case "continue" -> tokens.add(new Token(Token.CONTINUE, word, line, startColumn));
            case "while" -> tokens.add(new Token(Token.WHILE, word, line, startColumn));
            case "as" -> tokens.add(new Token(Token.AS, word, line, startColumn));
            default -> tokens.add(new Token(Token.IDENTIFIER, word, line, startColumn));
        }
    }

    private int checkArrayDimension() {
        int dimension = 0;
        int tempPosition = position;
        int tempColumn = column;

        while (tempPosition < input.length() && input.charAt(tempPosition) == '[') {
            if (peek(tempPosition - position + 1) == ']') {
                dimension++;
                tempPosition += 2;
                tempColumn += 2;
            } else {
                break;
            }
        }

        if (dimension > 0) {
            position = tempPosition;
            column = tempColumn;
        }

        return dimension;
    }


    private void handleElseToken(String word, int startColumn) {
        if (peek(1) == 'i' && peek(2) == 'f') {
            tokens.add(new Token(Token.ELSE_IF, word, line, startColumn));
            position += 3;
            column += 3;
        } else {
            tokens.add(new Token(Token.ELSE, word, line, startColumn));
        }
    }

    private void tokenizeSymbol(char current) {
        switch (current) {
            case '+' -> handlePlus();
            case '-' -> handleMinus();
            case '*' -> handleAsterisk();
            case '/' -> handleSlash();
            case '%' -> handlePercent();
            case '=' -> handleEqual();
            case '!' -> handleBang();
            case '<' -> handleLessThan();
            case '>' -> handleGreaterThan();
            case '&' -> handleAmpersand();
            case '|' -> handlePipe();
            case '(' -> tokens.add(new Token(Token.LEFT_PAREN, null, line, column));
            case ')' -> tokens.add(new Token(Token.RIGHT_PAREN, null, line, column));
            case '{' -> tokens.add(new Token(Token.LEFT_BRACE, null, line, column));
            case '}' -> tokens.add(new Token(Token.RIGHT_BRACE, null, line, column));
            case ';' -> tokens.add(new Token(Token.SEMICOLON, null, line, column));
            case ',' -> tokens.add(new Token(Token.COMMA, null, line, column));
            case '.' -> {
                if (peek(1) == '.') { // 다음 문자도 '.'인지 확인
                    tokens.add(new Token(Token.DOT_DOT, null, line, column)); // .. 토큰 추가
                    position++; // 다음 문자 ('.' )도 스킵
                    column++;
                } else {
                    tokens.add(new Token(Token.DOT, null, line, column));
                }
            }
            case ':' -> tokens.add(new Token(Token.COLON, null, line, column));
            case '[' -> tokens.add(new Token(Token.LEFT_BRACKET, null, line, column));
            case ']' -> tokens.add(new Token(Token.RIGHT_BRACKET, null, line, column));
            case '$' -> tokens.add(new Token(Token.DOLLAR, null, line, column));
        }
        position++;
        column++;
    }

    private void handlePlus() {
        if (peek(1) == '+') {
            tokens.add(new Token(Token.PLUS_PLUS, null, line, column));
            position++;
            column++;
        } else if (peek(1) == '=') {
            tokens.add(new Token(Token.PLUS_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.PLUS, null, line, column));
        }
    }

    private void handleMinus() {
        if (peek(1) == '-') {
            tokens.add(new Token(Token.MINUS_MINUS, null, line, column));
            position++;
            column++;
        } else if (peek(1) == '=') {
            tokens.add(new Token(Token.MINUS_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.MINUS, null, line, column));
        }
    }

    private void handleAsterisk() {
        if (peek(1) == '=') {
            tokens.add(new Token(Token.ASTERISK_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.ASTERISK, null, line, column));
        }
    }

    private void handleSlash() {
        if (peek(1) == '/') {
            tokenizeSingleLineComment();
        } else if (peek(1) == '*') {
            tokenizeBlockComment();
        } else if (peek(1) == '=') {
            tokens.add(new Token(Token.SLASH_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.SLASH, null, line, column));
        }
    }

    private void handlePercent() {
        if (peek(1) == '=') {
            tokens.add(new Token(Token.PERCENT_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.PERCENT, null, line, column));
        }
    }

    private void handleEqual() {
        if (peek(1) == '=') {
            tokens.add(new Token(Token.EQUAL_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.EQUAL, null, line, column));
        }
    }

    private void handleBang() {
        if (peek(1) == '=') {
            tokens.add(new Token(Token.NOT_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.BANG, null, line, column));
        }
    }

    private void handleLessThan() {
        if (peek(1) == '=') {
            tokens.add(new Token(Token.LESS_THAN_OR_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.LESS_THAN, null, line, column));
        }
    }

    private void handleGreaterThan() {
        if (peek(1) == '=') {
            tokens.add(new Token(Token.GREATER_THAN_OR_EQUAL, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.GREATER_THAN, null, line, column));
        }
    }

    private void handleAmpersand() {
        if (peek(1) == '&') {
            tokens.add(new Token(Token.AND, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.BITWISE_AND, null, line, column));
        }
    }

    private void handlePipe() {
        if (peek(1) == '|') {
            tokens.add(new Token(Token.OR, null, line, column));
            position++;
            column++;
        } else {
            tokens.add(new Token(Token.BITWISE_OR, null, line, column));
        }
    }

    private void handleNewline() {
        line++;
        position++;
        column = 1;
    }

    private char getEscapedCharacter(char escaped) {
        return switch (escaped) {
            case '\\' -> '\\';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case '\'' -> '\'';
            case '\"' -> '\"';
            default -> escaped;
        };
    }

    private void tokenizeSingleLineComment() {
        position += 2; // skip //
        column += 2;
        while (position < input.length() && input.charAt(position) != '\n') {
            position++;
            column++;
        }

        line++;
        column = 1;
    }

    private void tokenizeBlockComment() {
        position += 2; // skip /*
        column += 2;
        while (position < input.length() && !(input.charAt(position) == '*' && peek(1) == '/')) {
            if (input.charAt(position) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            position++;
        }
        line++;
        position += 2; // skip */
        column = 1;
    }

    private char peek(int offset) {
        if (position + offset >= input.length()) return '\0';
        return input.charAt(position + offset);
    }
}