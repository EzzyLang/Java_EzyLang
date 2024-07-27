package io.github._3xhaust.exception;

/**
 * Represents a parsing error encountered during the compilation process.
 */
public class ParseException extends Exception {
    private final String fileName;
    private final int line;
    private final int column;
    private final String errorMessage;
    private final String errorLine;

    public ParseException(String fileName, String errorMessage, int line, int column, String errorLine) {
        super(errorMessage);
        this.fileName = fileName;
        this.errorMessage = errorMessage;
        this.line = line;
        this.column = column;
        this.errorLine = errorLine;
    }

    /**
     * Returns a formatted error message that includes the file name, line and column number,
     * the error message, the line of code where the error occurred, and a caret pointing to the error location.
     *
     * @return A formatted error message string.
     */
    public String getFormattedMessage() {
        return String.format("%s:%d:%d: error: %s\n%s\n%s^",
                fileName, line, column, errorMessage, errorLine, " ".repeat(column - 1));
    }
}