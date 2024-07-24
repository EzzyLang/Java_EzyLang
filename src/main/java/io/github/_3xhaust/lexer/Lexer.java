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

            if (Character.isDigit(current)) {
                tokenizeNumber();
            } else if (current == '\"' || current == '\'') {
                tokenizeStringLiteral(current);
            } else if (current == 'p') {
                tokenizePrintKeyword();
            } else if(current == 'i') {
                tokenizeIfKeyword();
            } else if(current == 'e') {
                tokenizeElseKeyword();
            } else if (current == '\n' || current == '\r') {
                line++;
                column = 1;
                position++;
            } else {
                tokenizeSymbol(current);
            }
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

    private void tokenizePrintKeyword() {
        if (input.startsWith("print", position)) {
            tokens.add(new Token(Token.PRINT, null, line, column));
            position += 5;
            column += 5;
        }
    }

    private void tokenizeIfKeyword() {
        if (input.startsWith("if", position)) {
            tokens.add(new Token(Token.IF, null, line, column));
            position += 2;
            column += 2;
        }
    }

    private void tokenizeElseKeyword() {
        if (input.startsWith("else", position)) {
            tokens.add(new Token(Token.ELSE, null, line, column));
            position += 4;
            column += 4;
        }
    }

    private void tokenizeStringLiteral(char quote) {
        StringBuilder stringLiteral = new StringBuilder();
        int startColumn = column;
        position++; // 시작 따옴표는 skip
        column++;

        while (position < input.length() && input.charAt(position) != quote) {
            if (input.charAt(position) == '\\') {
                position++; // \ escape 문자는 skip
                column++;
                if (position < input.length()) {
                    stringLiteral.append(getEscapedCharacter(input.charAt(position)));
                    column++;
                }
            } else {
                stringLiteral.append(input.charAt(position));
                position++;
                column++;
            }
        }
        position++; // 마지막 따옴표는 skip
        column++;

        tokens.add(new Token(Token.STRING_LITERAL, stringLiteral.toString(), line, startColumn));
    }

    private void tokenizeSymbol(char current) {
        int startColumn = column;
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
                } else {
                    tokens.add(new Token(Token.PLUS, null, line, column));
                }
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
                } else {
                    tokens.add(new Token(Token.MINUS, null, line, column));
                }
                break;
            case '*':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.ASTERISK_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.ASTERISK, null, line, column));
                }
                break;
            case '/':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.SLASH_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.SLASH, null, line, column));
                }
                break;
            case '%':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.PERCENT_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.PERCENT, null, line, column));
                }
                break;
            case '=':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.EQUAL_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.EQUAL, null, line, column));
                }
                break;
            case '!':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.NOT_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.BANG, null, line, column));
                }
                break;
            case '<':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.LESS_THAN_OR_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.LESS_THAN, null, line, column));
                }
                break;
            case '>':
                if (peek(1) == '=') {
                    tokens.add(new Token(Token.GREATER_THAN_OR_EQUAL, null, line, column));
                    position++;
                    column++;
                } else {
                    tokens.add(new Token(Token.GREATER_THAN, null, line, column));
                }
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
        }
        position++;
        column++;
    }

    private char getEscapedCharacter(char escaped) {
        switch (escaped) {
            case '\\': return '\\';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'b': return '\b';
            case 'f': return '\f';
            case '\'': return '\'';
            case '\"': return '\"';
            default: return escaped;
        }
    }

    private char peek(int offset) {
        if (position + offset >= input.length()) return '\0';
        return input.charAt(position + offset);
    }
}