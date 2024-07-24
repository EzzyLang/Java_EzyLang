package io.github._3xhaust.lexer;

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

    public List<Token> tokenize() {
        while (position < input.length()) {
            char current = input.charAt(position);

            if (Character.isDigit(current))
                tokenizeNumber();
            else if (current == '\"' || current == '\'')
                tokenizeStringLiteral(current);
            else if (Character.isLetter(current) || current == '_')
                tokenizeIdentifierOrKeyword();
            else if (current == '\n' || current == '\r') {
                line++;
                column = 1;
                position++;
            } else
                tokenizeSymbol(current);
        }

        tokens.add(new Token(Token.EOF, null, line, column));
        return tokens;
    }

    private void tokenizeNumber() {
        StringBuilder numberLiteral = new StringBuilder();
        int startColumn = column;
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
            numberLiteral.append(input.charAt(position++));
            column++;
        }
        tokens.add(new Token(Token.NUMBER_LITERAL, numberLiteral.toString(), line, startColumn));
    }

    private void tokenizeStringLiteral(char quote) {
        StringBuilder stringLiteral = new StringBuilder();
        int startColumn = column;
        position++; // skip starting quote
        column++;

        while (position < input.length() && input.charAt(position) != quote) {
            if (input.charAt(position) == '\\') {
                position++; // skip escape character
                column++;
                if (position < input.length()) {
                    stringLiteral.append(getEscapedCharacter(input.charAt(position)));
                    column++;
                }
            } else if (input.charAt(position) == '$' && peek(1) == '{') {
                if (stringLiteral.length() > 0) {
                    tokens.add(new Token(Token.STRING_LITERAL, stringLiteral.toString(), line, startColumn));
                    stringLiteral.setLength(0);
                }
                position += 2; // skip ${
                column += 2;
                tokenizeVariableLiteral();
            } else {
                stringLiteral.append(input.charAt(position));
                position++;
                column++;
            }
        }
        position++; // skip ending quote
        column++;

        if (stringLiteral.length() > 0) {
            tokens.add(new Token(Token.STRING_LITERAL, stringLiteral.toString(), line, startColumn));
        }
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
        switch (word) {
            case "number", "char", "string", "boolean", "null", "void":
                tokens.add(new Token(word.toUpperCase(), word, line, startColumn));
                break;
            case "true", "false":
                tokens.add(new Token(Token.BOOLEAN_LITERAL, word, line, startColumn));
                break;
            case "print":
                tokens.add(new Token(Token.PRINT, word, line, startColumn));
                break;
            case "if":
                tokens.add(new Token(Token.IF, word, line, startColumn));
                break;
            case "else":
                if(peek(1) == 'i' && peek(2) == 'f') {
                    tokens.add(new Token(Token.ELSE_IF, word, line, startColumn));
                    position += 3;
                    column += 3;
                } else tokens.add(new Token(Token.ELSE, word, line, startColumn));

                break;
            default:
                tokens.add(new Token(Token.IDENTIFIER, word, line, startColumn));
        }
    }

    private void tokenizeSymbol(char current) {
        switch (current) {
            case '+':
                if (peek(1) == '+') {
                    tokens.add(new Token(Token.PLUS_PLUS, null, line, column));
                    position++;
                    column++;
                } else if (peek(1) == '=') {
                    tokens.add(new Token(Token.PLUS_EQUAL, null, line, column));
                    position++;
                    column++;
                } else  tokens.add(new Token(Token.PLUS, null, line, column));
                break;
            case '-':
                if (peek(1) == '-') {
                    tokens.add(new Token(Token.MINUS_MINUS, null, line, column));
                    position++;
                    column++;
                } else if (peek(1) == '=') {
                    tokens.add(new Token(Token.MINUS_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.MINUS, null, line, column));
                break;
            case '*':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.ASTERISK_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.ASTERISK, null, line, column));
                break;
            case '/':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.SLASH_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.SLASH, null, line, column));
                break;
            case '%':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.PERCENT_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.PERCENT, null, line, column));
                break;
            case '=':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.EQUAL_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.EQUAL, null, line, column));
                break;
            case '!':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.NOT_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.BANG, null, line, column));
                break;
            case '<':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.LESS_THAN_OR_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.LESS_THAN, null, line, column));
                break;
            case '>':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.GREATER_THAN_OR_EQUAL, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.GREATER_THAN, null, line, column));
                break;
            case '&':
                if (peek(1) == '&') {
                    tokens.add(new Token(Token.AND, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.BITWISE_AND, null, line, column));
                break;
            case '|':
                if (peek(1) == '|') {
                    tokens.add(new Token(Token.OR, null, line, column));
                    position++;
                    column++;
                } else tokens.add(new Token(Token.BITWISE_OR, null, line, column));
                break;
            case ')':
                tokens.add(new Token(Token.RIGHT_PAREN, null, line, column));
                break;
            case '(':
                tokens.add(new Token(Token.LEFT_PAREN, null, line, column));
                break;
            case '{':
                tokens.add(new Token(Token.LEFT_BRACE, null, line, column));
                break;
            case '}':
                tokens.add(new Token(Token.RIGHT_BRACE, null, line, column));
                break;
            case ';':
                tokens.add(new Token(Token.SEMICOLON, null, line, column));
                break;
            case ',':
                tokens.add(new Token(Token.COMMA, null, line, column));
                break;
            case '.':
                tokens.add(new Token(Token.DOT, null, line, column));
                break;
            case ':':
                tokens.add(new Token(Token.COLON, null, line, column));
                break;
            case '[':
                tokens.add(new Token(Token.LEFT_BRACKET, null, line, column));
                break;
            case ']':
                tokens.add(new Token(Token.RIGHT_BRACKET, null, line, column));
                break;
            case '$':
                tokens.add(new Token(Token.DOLLAR, null, line, column));
                break;
        }
        position++;
        column++;
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

    private char peek(int offset) {
        if (position + offset >= input.length()) return '\0';
        return input.charAt(position + offset);
    }
}