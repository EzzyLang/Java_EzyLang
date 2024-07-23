package io.github._3xhaust;

import io.github._3xhaust.parser.Parser;
import io.github._3xhaust.lexer.Lexer;
import io.github._3xhaust.token.Token;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if(args.length != 1) {
            System.out.println("Usage: java .jar <source file>");
            System.exit(1);
        }

        String fileName = args[0];

        if(!fileName.endsWith(".ezy")) throw new IOException("Invalid file extension");

        BufferedReader reader = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8));
        StringBuilder input = new StringBuilder();

        String readerString;
        while ((readerString = reader.readLine()) != null) input.append(readerString).append("\n");
        reader.close();

        Lexer lexer = new Lexer(input.toString());
        List<Token> tokens = lexer.tokenize();

        //for(Token token : tokens) System.out.println(token);

        Parser parser = new Parser(tokens);
        parser.parse();
    }
}