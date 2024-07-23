package io.github._3xhaust.lexer;

import io.github._3xhaust.token.Token;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private final List<Token> tokens = new ArrayList<>();
    private int position = 0;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        while (position < input.length()) {
            char current = input.charAt(position);

            if (Character.isDigit(current)) {
                StringBuilder numberLiteral = new StringBuilder();

                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    numberLiteral.append(input.charAt(position));
                    position++;
                }

                // 새로운 Token 객체 생성
                tokens.add(new Token(Token.NUMBER_LITERAL, numberLiteral.toString()));
            } else if (current == 'p') {
                if (input.startsWith("print", position)) {
                    tokens.add(new Token(Token.PRINT));
                    position += 5;
                }
            } else if (current == '\"' || current == '\'') {
                char quote = current;
                position++;
                StringBuilder stringLiteral = new StringBuilder();
                while (position < input.length() && input.charAt(position) != quote) {
                    if (input.charAt(position) == '\\') {
                        position++;
                        if (position < input.length()) {
                            char escaped = input.charAt(position);
                            switch (escaped) {
                                case '\\':
                                    stringLiteral.append('\\');
                                    break;
                                case 'n':
                                    stringLiteral.append('\n');
                                    break;
                                case 'r':
                                    stringLiteral.append('\r');
                                    break;
                                case 't':
                                    stringLiteral.append('\t');
                                    break;
                                case 'b':
                                    stringLiteral.append('\b');
                                    break;
                                case 'f':
                                    stringLiteral.append('\f');
                                    break;
                                case '\'':
                                    stringLiteral.append('\'');
                                    break;
                                case '\"':
                                    stringLiteral.append('\"');
                                    break;
                                default:
                                    stringLiteral.append(escaped);
                            }
                        }
                    } else {
                        stringLiteral.append(input.charAt(position));
                    }
                    position++;
                }
                position++;

                tokens.add(new Token(Token.STRING_LITERAL, stringLiteral.toString()));
            } else {
                switch (current) {
                    case '+':
                        if (peek() == '+') {
                            tokens.add(new Token(Token.PLUS_PLUS));
                            position++;
                        } else if (peek() == '=') {
                            tokens.add(new Token(Token.PLUS_EQUAL));
                            position++;
                        } else {
                            tokens.add(new Token(Token.PLUS));
                        }
                        break;
                    case '-':
                        if (peek() == '-') {
                            tokens.add(new Token(Token.MINUS_MINUS));
                            position++;
                        } else if (peek() == '=') {
                            tokens.add(new Token(Token.MINUS_EQUAL));
                            position++;
                        } else {
                            tokens.add(new Token(Token.MINUS));
                        }
                        break;
                    case '*':
                        if (peek() == '=') {
                            tokens.add(new Token(Token.ASTERISK_EQUAL));
                            position++;
                        } else {
                            tokens.add(new Token(Token.ASTERISK));
                        }
                        break;
                    case '/':
                        if (peek() == '=') {
                            tokens.add(new Token(Token.SLASH_EQUAL));
                            position++;
                        } else {
                            tokens.add(new Token(Token.SLASH));
                        }
                        break;
                    case '%':
                        if (peek() == '=') {
                            tokens.add(new Token(Token.PERCENT_EQUAL));
                            position++;
                        } else {
                            tokens.add(new Token(Token.PERCENT));
                        }
                        break;
                    case '=':
                        if (peek() == '=') {
                            tokens.add(new Token(Token.EQUAL_EQUAL));
                            position++;
                        } else {
                            tokens.add(new Token(Token.EQUAL));
                        }
                        break;
                    case ')':
                        tokens.add(new Token(Token.RIGHT_PAREN));
                        break;
                    case '(':
                        tokens.add(new Token(Token.LEFT_PAREN));
                        break;
                    case '{':
                        tokens.add(new Token(Token.LEFT_BRACE));
                        break;
                    case '}':
                        tokens.add(new Token(Token.RIGHT_BRACE));
                        break;
                }
                position++;
            }
        }
        tokens.add(new Token(Token.EOF));
        return tokens;
    }

    private char peek() {
        if (position + 1 >= input.length()) return '\0';
        return input.charAt(position + 1);
    }
}