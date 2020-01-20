package file;
import entity.Lexeme;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Scanner;

public class LexemeInput {

    Scanner scanner;

    public LexemeInput(String path){
        try {
            scanner = new Scanner(new FileReader(path));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Lexeme getLexeme(){
        String[] numbers = scanner.nextLine().split(",");
        return new Lexeme(Integer.valueOf(numbers[0]), Integer.valueOf(numbers[1]));
    }

    public boolean isAvailable() {
        return scanner.hasNext();
    }
}
