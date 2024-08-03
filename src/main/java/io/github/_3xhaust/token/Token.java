package io.github._3xhaust.token;

/**
 * Represents a single token in the source code.
 * A token is the smallest meaningful unit of the language.
 */
public class Token {

    // Arithmetic Operators
    public static final String PLUS = "+";
    public static final String MINUS = "-";
    public static final String ASTERISK = "*";
    public static final String SLASH = "/";
    public static final String PERCENT = "%";

    // Relational Operators
    public static final String EQUAL_EQUAL = "==";
    public static final String NOT_EQUAL = "!=";
    public static final String LESS_THAN = "<";
    public static final String GREATER_THAN = ">";
    public static final String LESS_THAN_OR_EQUAL = "<=";
    public static final String GREATER_THAN_OR_EQUAL = ">=";

    // Logical Operators
    public static final String AND = "&&";
    public static final String OR = "||";
    public static final String BANG = "!";

    // Assignment Operators
    public static final String EQUAL = "=";
    public static final String PLUS_EQUAL = "+=";
    public static final String MINUS_EQUAL = "-=";
    public static final String ASTERISK_EQUAL = "*=";
    public static final String SLASH_EQUAL = "/=";
    public static final String PERCENT_EQUAL = "%=";

    // Increment/Decrement Operators
    public static final String PLUS_PLUS = "++";
    public static final String MINUS_MINUS = "--";

    // Bitwise Operators
    public static final String BITWISE_AND = "&";
    public static final String BITWISE_OR = "|";
    public static final String BITWISE_XOR = "^";
    public static final String BITWISE_NOT = "~";
    public static final String LEFT_SHIFT = "<<";
    public static final String RIGHT_SHIFT = ">>";

    // Data Types
    public static final String NUMBER = "number";
    public static final String CHAR = "char";
    public static final String STRING = "string";
    public static final String BOOLEAN = "boolean";
    public static final String ARRAY = "array";
    public static final String NULL = "null";
    public static final String VOID = "void";

    // Literals
    public static final String IDENTIFIER = "identifier"; // Represents a variable or function name
    public static final String NUMBER_LITERAL = "number";
    public static final String CHAR_LITERAL = "char";
    public static final String STRING_LITERAL = "string";
    public static final String BOOLEAN_LITERAL = "boolean";
    public static final String VARIABLE_LITERAL = "variable"; // Represents a variable within a string literal, like "Hello, ${name}!"

    // Functions
    public static final String PRINT = "print";
    public static final String PRINTLN = "println";

    // Control Flow
    public static final String IF = "if";
    public static final String ELSE = "else";
    public static final String ELSE_IF = "else if";
    public static final String WHILE = "while";
    public static final String FOR = "for";
    public static final String BREAK = "break";
    public static final String CONTINUE = "continue";
    public static final String RETURN = "return";
    public static final String IS = "is"; // Type checking operator
    public static final String AS = "as"; // Type casting operator

    // Definition
    public static final String FUNC = "func";

    // Loop
    public static final String IN = "in"; // Used in for-in loops

    // Symbols
    public static final String LEFT_PAREN = "(";
    public static final String RIGHT_PAREN = ")";
    public static final String LEFT_BRACE = "{";
    public static final String RIGHT_BRACE = "}";
    public static final String LEFT_BRACKET = "[";
    public static final String RIGHT_BRACKET = "]";
    public static final String DOLLAR = "$"; // Used for string interpolation
    public static final String ARROW = "->"; // Used in lambda expressions
    public static final String COMMA = ",";
    public static final String DOT = ".";
    public static final String DOT_DOT = ".."; // Range operator (e.g., 1..5)
    public static final String COLON = ":";
    public static final String SEMICOLON = ";";

    // End of File
    public static final String EOF = "EOF";

    private final String type; // The type of the token (e.g., IDENTIFIER, NUMBER_LITERAL, PLUS)
    private final String value; // The actual value of the token (e.g., "myVariable", "123", "+")
    private final int line; // Line number where the token is found in the source code
    private final int column; // Column number where the token starts on the given line

    /**
     * Constructs a new Token object.
     *
     * @param type  The type of the token.
     * @param value The value of the token.
     * @param line  The line number of the token.
     * @param column The column number of the token.
     */
    public Token(String type, String value, int line, int column) {
        this.type = type;
        this.value = value != null ? value : "";
        this.line = line;
        this.column = column;
    }

    // Getters for the token's properties
    public String getToken() {
        return type;
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

    /**
     * Returns a string representation of the token.
     * Useful for debugging.
     */
    @Override
    public String toString() {
        return "Token{" +
                "type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", line=" + line +
                ", column=" + column +
                '}';
    }
}