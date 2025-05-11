package br.ufscar.dc.compiladores;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) {
        try {
        CharStream cs = CharStreams.fromFileName(args[0]);
        JanderLexer lex = new JanderLexer(cs);
        String arquivoSaida = args[1];
        PrintWriter pw = new PrintWriter(arquivoSaida);
        CommonTokenStream tokens = new CommonTokenStream(lex);
        JanderParser parser = new JanderParser(tokens);

        MyCustomErrorListener mcel = new MyCustomErrorListener(pw);
        parser.addErrorListener(mcel);

        parser.programa();

        pw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}