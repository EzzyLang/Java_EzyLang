package io.github._3xhaust.parser;
import io.github._3xhaust.token.Token;

import java.math.BigDecimal;
import java.math.MathContext;
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
            BigDecimal result = expression();

            if (result.stripTrailingZeros().scale() <= 0) {
                System.out.println(result.toBigInteger());
            } else {
                System.out.println(result);
            }
        }

        position++; // ")" 토큰 처리
    }

    private BigDecimal expression() {
        BigDecimal left = term();

        while (Objects.equals(currentPosition().getToken(), new Token(Token.PLUS).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.MINUS).getToken())) {
            Token operator = currentPosition();
            position++;
            BigDecimal right = term();

            switch (operator.getToken()) {
                case Token.PLUS:
                    left = left.add(right);
                    break;
                case Token.MINUS:
                    left = left.subtract(right);
                    break;
            }
        }

        return left;
    }

    private BigDecimal term() {
        BigDecimal left = primary();

        while (Objects.equals(currentPosition().getToken(), new Token(Token.ASTERISK).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.SLASH).getToken()) ||
                Objects.equals(currentPosition().getToken(), new Token(Token.PERCENT).getToken())) {
            Token operator = currentPosition();
            position++;
            BigDecimal right = primary();

            switch (operator.getToken()) {
                case Token.ASTERISK:
                    left = left.multiply(right);
                    break;
                case Token.SLASH:
                    left = left.divide(right, MathContext.DECIMAL128);
                    break;
                case Token.PERCENT:
                    left = left.remainder(right);
                    break;
            }
        }

        return left;
    }

    private BigDecimal primary() {
        Token token = currentPosition();
        position++; // Move past the current token
        return new BigDecimal(token.getValue());
    }

    private Token currentPosition() {
        return tokens.get(position);
    }
}