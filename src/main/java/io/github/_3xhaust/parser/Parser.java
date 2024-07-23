package io.github._3xhaust.parser;
import io.github._3xhaust.token.Token;

import java.util.List;
import java.util.Objects;

public class Parser {
    private final List<Token> tokens;
    private int position = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        //for(Token token : tokens) System.out.println(token.getValue());
    }

    public void parse() {
        while (!Objects.equals(currentPosition().getToken(), new Token(Token.EOF).getToken())) {
            if (Objects.equals(currentPosition().getToken(), new Token(Token.PRINT).getToken())) {
                printStatement();
            } else {
                // print 문이 아니면 다음 토큰으로 이동
                position++;
            }
        }
    }

    private void printStatement() {
        position++; // "print" 토큰 처리
        position++; // "(" 토큰 처리

        if(Objects.equals(currentPosition().getToken(), new Token(Token.STRING_LITERAL).getToken())) {
            System.out.println(currentPosition().getValue());
        }else {
            System.out.println(expression());
        }

        position++; // ")" 토큰 처리
    }

    private int expression() {
        int left = primary();

        while (Objects.equals(currentPosition().getToken(), new Token(Token.PLUS).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.MINUS).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.ASTERISK).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.SLASH).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.PERCENT).getToken())) {
            Token operator = currentPosition();
            position++; // 연산자 토큰 처리
            int right = primary();

            if (Objects.equals(operator.getToken(), new Token(Token.PLUS).getToken())) {
                left += right;
            } else if (Objects.equals(operator.getToken(), new Token(Token.MINUS).getToken())) {
                left -= right;
            } else if (Objects.equals(operator.getToken(), new Token(Token.ASTERISK).getToken())) {
                left *= right;
            } else if (Objects.equals(operator.getToken(), new Token(Token.SLASH).getToken())) {
                left /= right;
            } else if (Objects.equals(operator.getToken(), new Token(Token.PERCENT).getToken())) {
                left %= right;
            }
        }

        return left;
    }

    private int primary() {
        Token token = currentPosition();
        position++;
        return Integer.parseInt(token.getValue());
    }

    private Token currentPosition() {
        return tokens.get(position);
    }
}