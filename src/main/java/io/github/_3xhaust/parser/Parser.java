package io.github._3xhaust.parser;

import io.github._3xhaust.exception.ParseException;
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
    }

    public void parse() {
        try {
            while (!Objects.equals(currentPosition().getToken(), Token.EOF)) {
                statement();
            }
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Line " + e.getLine() + ", Column " + e.getColumn());
        }
    }

    private void statement() throws ParseException {
        if (Objects.equals(currentPosition().getToken(), Token.PRINT)) {
            printStatement();
        } else if (Objects.equals(currentPosition().getToken(), Token.IF)) {
            ifStatement();
        }

        if (!isAtEnd() && !currentPosition().getToken().equals(Token.SEMICOLON)) {
            throw new ParseException("Expected ';'", currentPosition().getLine(), currentPosition().getColumn());
        }
    }

    private void printStatement() throws ParseException {
        consume(Token.PRINT);
        consume(Token.LEFT_PAREN);

        if (currentPosition().getToken().equals(Token.STRING_LITERAL)) {
            System.out.println(currentPosition().getValue());
            consume(Token.STRING_LITERAL);
        } else {
            BigDecimal result = expression();

            if (result.compareTo(BigDecimal.ZERO) == 0) {
                System.out.println("false");
            } else if (result.compareTo(BigDecimal.ONE) == 0) {
                System.out.println("true");
            } else {
                if (result.stripTrailingZeros().scale() <= 0) {
                    System.out.println(result.toBigInteger());
                } else {
                    System.out.println(result);
                }
            }
        }

        consume(Token.RIGHT_PAREN);
    }

    private void ifStatement() throws ParseException {
        boolean conditionMet = false;

        consume(Token.IF);
        consume(Token.LEFT_PAREN);
        boolean condition = evaluateExpression();
        consume(Token.RIGHT_PAREN);
        consume(Token.LEFT_BRACE);

        if (condition) {
            block();
            conditionMet = true;
        } else {
            skipBlock();
        }

        consume(Token.RIGHT_BRACE);

        while (currentPosition().getToken().equals(Token.ELSE_IF)) {
            consume(Token.ELSE_IF);
            consume(Token.LEFT_PAREN);
            condition = evaluateExpression();
            consume(Token.RIGHT_PAREN);
            consume(Token.LEFT_BRACE);

            if (!conditionMet && condition) {
                block();
                conditionMet = true;
            } else {
                skipBlock();
            }

            consume(Token.RIGHT_BRACE);
        }

        if (currentPosition().getToken().equals(Token.ELSE)) {
            consume(Token.ELSE);
            consume(Token.LEFT_BRACE);

            if (!conditionMet) {
                block();
            } else {
                skipBlock();
            }

            consume(Token.RIGHT_BRACE);
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
        return position >= tokens.size();
    }

    private boolean evaluateExpression() throws ParseException {
        BigDecimal result = expression();
        return result.compareTo(BigDecimal.ZERO) != 0;
    }

    private BigDecimal expression() throws ParseException {
        return logicalOrExpression();
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
        BigDecimal left = arithmeticExpression();

        if (currentPosition().getToken().equals(Token.EQUAL_EQUAL) ||
                currentPosition().getToken().equals(Token.NOT_EQUAL) ||
                currentPosition().getToken().equals(Token.LESS_THAN) ||
                currentPosition().getToken().equals(Token.GREATER_THAN) ||
                currentPosition().getToken().equals(Token.LESS_THAN_OR_EQUAL) ||
                currentPosition().getToken().equals(Token.GREATER_THAN_OR_EQUAL)) {

            String operator = currentPosition().getToken();
            position++;
            BigDecimal right = arithmeticExpression();

            return switch (operator) {
                case Token.EQUAL_EQUAL -> left.compareTo(right) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.NOT_EQUAL -> left.compareTo(right) != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.LESS_THAN -> left.compareTo(right) < 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.GREATER_THAN -> left.compareTo(right) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.LESS_THAN_OR_EQUAL -> left.compareTo(right) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                case Token.GREATER_THAN_OR_EQUAL -> left.compareTo(right) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                default -> throw new ParseException("Unexpected comparison operator", currentPosition().getLine(), currentPosition().getColumn());
            };
        }

        return left;
    }

    private BigDecimal arithmeticExpression() throws ParseException {
        BigDecimal left = term();

        while (Objects.equals(currentPosition().getToken(), Token.PLUS) ||
                Objects.equals(currentPosition().getToken(), Token.MINUS)) {
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

    private BigDecimal term() throws ParseException {
        BigDecimal left = factor();

        while (currentPosition().getToken().equals(Token.ASTERISK) ||
                currentPosition().getToken().equals(Token.SLASH) ||
                currentPosition().getToken().equals(Token.PERCENT)) {
            String operator = currentPosition().getToken();
            position++;
            BigDecimal right = factor();

            switch (operator) {
                case Token.ASTERISK -> left = left.multiply(right);
                case Token.SLASH -> {
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ParseException("Division by zero", currentPosition().getLine(), currentPosition().getColumn());
                    }
                    left = left.divide(right, MathContext.DECIMAL128);
                }
                case Token.PERCENT -> left = left.remainder(right);
            }
        }

        return left;
    }

    private BigDecimal factor() throws ParseException {
        Token current = currentPosition();
        if (current.getToken().equals(Token.NUMBER_LITERAL)) {
            position++;
            return new BigDecimal(current.getValue());
        } else if (current.getToken().equals(Token.LEFT_PAREN)) {
            position++;
            BigDecimal result = arithmeticExpression();
            if (currentPosition().getToken().equals(Token.RIGHT_PAREN)) {
                position++;
            }
            return result;
        } else if (current.getToken().equals(Token.BOOLEAN_LITERAL)) {
            position++;
            return current.getValue().equals("true") ? BigDecimal.ONE : BigDecimal.ZERO;
        } else {
            throw new ParseException("Unexpected token", current.getLine(), current.getColumn());
        }
    }


    private Token currentPosition() {
        return tokens.get(position);
    }

    private void consume(String expectedToken) throws ParseException {
        if (currentPosition().getToken().equals(expectedToken)) {
            position++;
        } else {
            throw new ParseException("Expected '" + expectedToken + "'", currentPosition().getLine(), currentPosition().getColumn());
        }
    }
}