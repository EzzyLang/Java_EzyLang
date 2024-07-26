package io.github._3xhaust.token;

public class Token {
    // 대입 연산자
    public static final String EQUAL = "=";

    // 복합 대입 연산자
    public static final String PLUS_EQUAL = "+=";
    public static final String MINUS_EQUAL = "-=";
    public static final String ASTERISK_EQUAL = "*=";
    public static final String SLASH_EQUAL = "/=";
    public static final String PERCENT_EQUAL = "%=";

    // 산술 연산자
    public static final String PLUS = "+";
    public static final String MINUS = "-";
    public static final String ASTERISK = "*";
    public static final String SLASH = "/";
    public static final String PERCENT = "%";

    // 관계 연산자
    public static final String EQUAL_EQUAL = "==";
    public static final String NOT_EQUAL = "!=";
    public static final String LESS_THAN = "<";
    public static final String GREATER_THAN = ">";
    public static final String LESS_THAN_OR_EQUAL = "<=";
    public static final String GREATER_THAN_OR_EQUAL = ">=";

    // 논리 연산자
    public static final String AND = "&&";
    public static final String OR = "||";
    public static final String BANG = "!";

    // 증감 연산자
    public static final String PLUS_PLUS = "++";
    public static final String MINUS_MINUS = "--";

    // 비트 연산자
    public static final String BITWISE_AND = "&";
    public static final String BITWISE_OR = "|";
    public static final String BITWISE_XOR = "^";
    public static final String BITWISE_NOT = "~";
    public static final String LEFT_SHIFT = "<<";
    public static final String RIGHT_SHIFT = ">>";

    // 데이터타입
    public static final String NUMBER = "number";
    public static final String CHAR = "char";
    public static final String STRING = "string";
    public static final String BOOLEAN = "boolean";
    public static final String NUMBER_ARRAY = "number array";
    public static final String CHAR_ARRAY = "char array";
    public static final String STRING_ARRAY = "string array";
    public static final String BOOLEAN_ARRAY = "boolean array";
    public static final String ARRAY = "array";
    public static final String NULL = "null";
    public static final String VOID = "void";

    // 리터럴 - 값을 가질 수 있음
    public static final String IDENTIFIER = "identifier";
    public static final String NUMBER_LITERAL = "number";
    public static final String CHAR_LITERAL = "char";
    public static final String STRING_LITERAL = "string";
    public static final String BOOLEAN_LITERAL = "boolean";
    public static final String VARIABLE_LITERAL = "VARIABLE_LITERAL";

    // 함수
    public static final String PRINT = "print";
    public static final String PRINTLN = "println";
    public static final String FOR_EACH = "forEach";

    // 제어문
    public static final String IF = "if";
    public static final String ELSE = "else";
    public static final String ELSE_IF = "else if";
    public static final String WHILE = "while";
    public static final String FOR = "for";
    public static final String BREAK = "break";
    public static final String CONTINUE = "continue";
    public static final String RETURN = "return";
    public static final String IS = "is";

    public static final String AS = "as";

    // 정의
    public static final String FUNC = "func";

    // 루프
    public static final String IN = "in";
    public static final String DOT_LENGTH = ".length";

    // 기호
    public static final String LEFT_PAREN = "(";
    public static final String RIGHT_PAREN = ")";
    public static final String LEFT_BRACE = "{";
    public static final String RIGHT_BRACE = "}";
    public static final String LEFT_BRACKET = "[";
    public static final String RIGHT_BRACKET = "]";
    public static final String DOLLAR = "$";
    public static final String ARROW = "->";
    public static final String COMMA = ",";
    public static final String DOT = ".";
    public static final String DOT_DOT = "..";
    public static final String COLON = ":";
    public static final String SEMICOLON = ";";

    // End of File
    public static final String EOF = "";

    private final String token;
    private String value;
    private final int line;
    private final int column;

    public Token(String token, String value, int line, int column) {
        this.token = token;
        this.value = value != null ? value : ""; // Ensure value is not null
        this.line = line;
        this.column = column;
    }

    public String getToken() {
        return token;
    }

    public String getValue() {
        return value;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

}