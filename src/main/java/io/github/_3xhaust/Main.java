package io.github._3xhaust;

import io.github._3xhaust.exception.ParseException;
import io.github._3xhaust.parser.Parser;
import io.github._3xhaust.lexer.Lexer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The main class for the EzyLang interpreter.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -jar ezylang-<version>.jar <source file>");
            System.exit(1);
        }

        String fileName = args[0];
        try {
            if (!fileName.endsWith(".ezy")) throw new IOException("Invalid file extension: Must be '.ezy'");

            String input = readFile(fileName);
            Lexer lexer = new Lexer(input);
            var tokens = lexer.tokenize(); // Use var for type inference

            Parser parser = new Parser(tokens, fileName, input);
            parser.parse();

        } catch (IOException | ParseException e) {
            System.err.println(e.getMessage()); // Print the formatted error
            System.exit(1);
        }
    }

    /**
     * Reads the content of a file and returns it as a string.
     *
     * @param fileName The path to the file to read.
     * @return The content of the file as a string.
     * @throws IOException If an error occurs during file reading.
     */
    private static String readFile(String fileName) throws IOException {
        StringBuilder input = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                input.append(line).append("\n");
            }
        }
        return input.toString();
    }
}