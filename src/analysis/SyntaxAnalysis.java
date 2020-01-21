package analysis;

import entity.Identifier;
import entity.Lexeme;
import exception.AlreadyDefinedException;
import exception.NotDefinedException;
import exception.SemanticsException;
import exception.TypesMismatchException;
import file.LexemeInput;
import file.Table;

import java.util.*;

public class SyntaxAnalysis {

    private static Lexeme next;

    private static LexemeInput lexemeInput;

    private static Table words;

    private static Table delimiters;

    private static Table numbers;

    private static Lexeme buffer;

    private static List<Identifier> identifiers;

    private static int line = 1;

    private static int column = 0;

    private static String lastError;

    private static final int MAX_COMPLEX_ITERATION = 20;

    private static int complexIteration = 0;

    private static boolean needGetNext = true;

    private static boolean doubleCheck = true;

    public static String run(LexemeInput lexemeInput) {
        SyntaxAnalysis.lexemeInput = lexemeInput;

        words = new Table("tables/1.txt", 16);
        words.load();
        delimiters = new Table("tables/2.txt", 19);
        delimiters.load();
        numbers = new Table("tables/3.txt", 16);
        numbers.load();

        identifiers = new ArrayList<>();

        String result;

        try{
            if(program()){
                result = "Ok";
            } else {
                result = SyntaxAnalysis.lastError;
            }
        } catch (AlreadyDefinedException e){
            result = "Defined variable was defined again";
        } catch (NotDefinedException e){
            result = "Not defined variable was used";
        } catch (TypesMismatchException e){
            result = "Types mismatch at";
        } catch (RuntimeException re) {
            result = re.getMessage();
        } catch (Exception e){
            result = "General error";
        }
        System.out.println(line + ":" + column);
        return result;
    }

    private static boolean isNext(String lexeme) {
        char firstSymbol = lexeme.charAt(0);
        int table;
        int number;
        if (firstSymbol >= 97 && firstSymbol <= 122) {
            table = 1;
            number = words.look(lexeme);
        } else {
            table = 2;
            number = delimiters.look(lexeme);
        }
        return table == next.getTable() && number == next.getNumber();
    }

    private static boolean program() throws SemanticsException {
        getNext();
        readNewLines();
        if(!isNext("{")) {
            return false;
        } else {
            do {
                getNext();
            }
            while(isNext("\\n"));
        }
        do {
            if (!(description() || operator())) {
                return false;
            }
            if ((isNext("\\n"))) {
                line++;
            }
            column = 0;
/*            if(needGetNext) {
                //getNext();
            } else {
                needGetNext = true;
            }*/
            readNewLines();
        } while (!isNext("}"));
        return true;
    }

    private static boolean isVariableDefined(Identifier identifier) {
        return SyntaxAnalysis.identifiers.stream().anyMatch(id -> id.getId() == identifier.getId());
    }

    private static boolean isNextSymbol(String symbol, boolean doGetNext) {
        if(isNext(symbol)) {
            if(doGetNext) getNext();
            return lexemeInput.isAvailable();
        }
        return false;
    }

    private static boolean isNextSymbol(String symbol) {
        return isNextSymbol(symbol, true);
    }

    private static boolean isNextNewLine(boolean doGetNext) {
        return isNextSymbol("\\n", doGetNext);
    }

    private static boolean isNextNewLine() {
        return isNextNewLine(true);
    }

    private static boolean isNextVariable(boolean doGetNext) {
        if(identifiers.contains(new Identifier(next.getNumber()))) {
            if(doGetNext) getNext();
            return true;
        } else {
            return false;
        }
    }

    private static boolean isNextVariable() {
        return isNextVariable(true);
    }

    private static boolean isNextComma(boolean doGetNext) {
        return isNextSymbol(",", doGetNext);
    }

    private static boolean isNextComma() {
        return isNextComma(true);
    }

    private static boolean isNextCloseBracket(boolean doGetNext) {
        return isNextSymbol(";", doGetNext);
    }

    private static boolean isNextCloseBracket() {
        return isNextCloseBracket(true);
    }

    private static boolean addVariable(List<Identifier> described, int currentId, boolean doGetNext) {
        if(describedIdentifier()) {
            Identifier identifier = new Identifier(currentId);
            if(isVariableDefined(identifier)) return setLastError("Variable defined again");
            described.add(identifier);
            if(doGetNext) getNext();
            return true;
        } else return setLastError("Unexpected identificator");
    }

    private static boolean addVariable(List<Identifier> described, int currentId) {
        return addVariable(described, currentId, true);
    }


    private static void addVariables(List<Identifier> identifiers, int type, boolean defaultId) {
        identifiers.forEach(identifier -> {
            if(defaultId)identifier.setId(SyntaxAnalysis.identifiers.size());
            identifier.setType(type);
            SyntaxAnalysis.identifiers.add(identifier);
        });
    }

    private static void addVariables(List<Identifier> identifiers, int type) {
        addVariables(identifiers, type, false);
    }

    private static boolean setLastError(String lastError) {
        SyntaxAnalysis.lastError = lastError;
        return false;
    }

    private static void readNewLines() {
        while(isNextNewLine());
    }

    private static boolean description() throws AlreadyDefinedException {
        List<Identifier> described = new ArrayList<>();
        if (!describedIdentifier())
            return false;
        buffer = next;
        if(!addVariable(described,next.getNumber())) return false;
        if (isNext("ass")){
            return setLastError("Attempt to assign not defined variable");
        }
        do {
            if (isNextComma()) {
                if(!addVariable(described, next.getNumber())) return false;
            }
        } while (!isNextSymbol(":"));
        if(!type()) return setLastError("There is unknown type of variables");
        int type = next.getNumber();
        getNext();
        if(!isNextCloseBracket()) return setLastError("Unclosed variables description");
        for(Identifier identifier : described) {
            if(isVariableDefined(identifier)) throw new AlreadyDefinedException();
        }
        addVariables(described, type, false);
        return true;
    }

    private static void addIdentifier(List<Identifier> described, Lexeme lexeme) throws AlreadyDefinedException {
        Identifier identifier = new Identifier(lexeme.getNumber());
        if(identifiers.contains(identifier))
            throw new AlreadyDefinedException();
        if(described.contains(identifier))
            throw new AlreadyDefinedException();
        described.add(identifier);
    }

    private static boolean describedIdentifier() {
        return next.getTable() == 4;
    }

    private static boolean type(){
        return isNext("integer") || isNext("real") || isNext("boolean");
    }

    private static boolean operator() throws NotDefinedException, TypesMismatchException {
        return complex() || assign() || condition() || fixedCycle() ||
                conditionalCycle() || input() || output();
    }

    private static boolean complexLowPriorityOperator() throws NotDefinedException, TypesMismatchException {
        return assign() || condition() || fixedCycle() ||
                conditionalCycle() || input() || output() || (!isNext(";") && complex());
    }

    private static boolean complex() throws NotDefinedException, TypesMismatchException {
        doubleCheck = !doubleCheck;
        if(doubleCheck) {
            return false;
        }
        if(isNextNewLine(false)) return setLastError("[complex] Expected operator, found new line");
        if(complexIteration >= MAX_COMPLEX_ITERATION) return setLastError("[complex] Not expected symbol");
        complexIteration++;
        if(!operator()) {
            complexIteration--;
            return false;
        }
        if (!isNext(":") && !isNext("\\n")) {
            complexIteration--;
            return true;
        } else {
            getNext();
            if (!operator()) {
                complexIteration--;
                return setLastError("[complex] Expected operator");
            }
        }
        complexIteration--;
        return true;
    }

    private static boolean assign() throws NotDefinedException, TypesMismatchException {
        if (!identifier())
            return false;
        String type = words.get(identifiers.get(next.getNumber()).getType());
        if(buffer != null){
            next = buffer;
            buffer = null;
        }
        getNext();
        if (!isNext("ass"))
            return false;
        getNext();
        if(!expression())
            return false;
        checkAssignType(type, expressionStack.pop());
/*        if(isNextSymbol("to", false) || isNextNewLine(false)) {
            return true;
        }
        return setLastError("Unexpected end of the line, while assigning a variable");*/
        return true;
    }

    private static void checkAssignType(String left, String right) throws TypesMismatchException {
        if(left.equals(BOOLEAN) && !right.equals(BOOLEAN))
            throw new TypesMismatchException();
        if(left.equals(INTEGER) && !right.equals(INTEGER))
            throw new TypesMismatchException();
        if(left.equals(REAL) && right.equals(BOOLEAN))
            throw new TypesMismatchException();
    }

    private static boolean condition() throws NotDefinedException, TypesMismatchException {
        if (!isNext("if"))
            return false;
        getNext();
        if (!isNext("("))
            return false;
        getNext();
        if (!expression())
            return false;
        if(!expressionStack.pop().equals(BOOLEAN))
            throw new TypesMismatchException();
        if (!isNext(")"))
            return false;
        getNext();
        if (!operator())
            return false;
        if (isNext("else")) {
            getNext();
            return operator();
        }
        return true;
    }

    private static boolean fixedCycle() throws NotDefinedException, TypesMismatchException {
        if (!isNext("for"))
            return false;
        getNext();
        if (!assign())
            return setLastError("[for] Expected variable assign");
        if (!isNext("to"))
            return setLastError("[for] Expected to which variable");
        getNext();
        if (!expression())
            return setLastError("[for] Expected expression");
        if(!expressionStack.pop().equals(BOOLEAN))
            throw new TypesMismatchException();
        if(!isNextSymbol("do"))
            return setLastError("[for] Expected do");
        if (!operator())
            return setLastError("[for] Expected operator");
        return true;
    }

    private static boolean conditionalCycle() throws NotDefinedException, TypesMismatchException {
        if (!isNext("while"))
            return false;
        getNext();
        if (!expression())
            return setLastError("[while] Expected expression but there is no");
        if(!expressionStack.pop().equals(BOOLEAN))
            throw new TypesMismatchException();
        if (!isNext("do"))
            return setLastError("[while] Expected do but there is no");
        getNext();
        return operator();
    }

    private static boolean input() throws NotDefinedException {
        if (!isNext("read"))
            return false;
        getNext();
        if (!identifier())
            return false;
        getNext();
        while (isNext(",")) {
            getNext();
            if (!identifier())
                return false;
            getNext();
        }
        return true;
    }

    private static boolean output() throws NotDefinedException, TypesMismatchException {
        if (!isNext("write"))
            return false;
        getNext();
        if (!expression())
            return false;
        while (isNext(",")) {
            getNext();
            if (!expression())
                return false;
            getNext();
        }
        return true;
    }

    private static boolean identifier() throws NotDefinedException {
        if(next.getTable() != 4)
            return false;
        if(!identifiers.contains(new Identifier(next.getNumber())))
            throw new NotDefinedException();
        pushIdentifier();
        return true;
    }

    private static Stack<String> expressionStack = new Stack<>();

    private static boolean expression() throws NotDefinedException, TypesMismatchException {
        expressionStack.clear();
        if (!operand())
            return false;
        while (relationshipOperation()) {
            getNext();
            if (!operand())
                return false;
        }
        checkTypes();
        return true;
    }

    private static void checkTypes() throws TypesMismatchException {
        while(expressionStack.size() > 1){
            String operand2 = expressionStack.pop();
            String operation = expressionStack.pop();
            if(operation.equals("not")){
                checkUnary(operand2);
            } else {
                String operand1 = expressionStack.pop();
                checkOperation(operand2, operation, operand1);
            }
        }
    }

    private static final String BOOLEAN = "boolean";
    private static final String INTEGER = "integer";
    private static final String REAL = "real";

    private static void checkOperation(String operand2, String operation, String operand1) throws TypesMismatchException {
        if(operation.equals("+") || operation.equals("-") || operation.equals("*") || operation.equals("/")){
            if(operand1.equals(BOOLEAN) || operand2.equals(BOOLEAN)){
                throw new TypesMismatchException();
            }
            if(operation.equals("/")) {
                expressionStack.push(REAL);
            }
            else if(operand1.equals(REAL) || operand2.equals(REAL)){
                expressionStack.push(REAL);
            } else {
                expressionStack.push(INTEGER);
            }
        } else if(operation.equals("||") || operation.equals("&&")) {
            if(!operand1.equals(BOOLEAN) || !operand2.equals(BOOLEAN)){
                throw new TypesMismatchException();
            }
            expressionStack.push(BOOLEAN);
        } else {
            if(operand1.equals(BOOLEAN) || operand2.equals(BOOLEAN)){
                throw new TypesMismatchException();
            }
            expressionStack.push(BOOLEAN);
        }
    }

    private static void checkUnary(String operand) throws TypesMismatchException {
        if(operand.equals(BOOLEAN)){
            expressionStack.push(BOOLEAN);
        } else {
            throw new TypesMismatchException();
        }
    }

    private static boolean operand() throws NotDefinedException, TypesMismatchException {
        if (!term())
            return false;
        while (additionOperation()) {
            getNext();
            if (!term())
                return false;
        }
        return true;
    }

    private static boolean term() throws NotDefinedException, TypesMismatchException {
        if (!multiplier())
            return false;
        getNext();
        while (multiplicationOperation()) {
            getNext();
            if (!multiplier())
                return false;
            getNext();
            needGetNext = false;
        }
        return true;
    }

    private static boolean multiplier() throws NotDefinedException, TypesMismatchException {
        if (unary()) {
            getNext();
            return multiplier();
        } else if (isNext("(")) {
            getNext();
            if (!expression())
                return false;
            //getNext();
            return isNext(")");
        } else if(identifier() || number() || logical()){
            return true;
        } else {
            return false;
        }
    }

    private static boolean number() {
        if(next.getTable() == 3){
            pushNumber();
            return true;
        } else {
            return false;
        }
    }

    private static boolean logical() {
        if(isNext("true") || isNext("false")){
            pushLogical();
            return true;
        } else {
            return false;
        }
    }

    private static boolean unary() {
        if(isNext("not")){
            pushOperation();
            return true;
        } else {
            return false;
        }
    }

    private static boolean relationshipOperation() {
        if(isNext("!=") || isNext("=") || isNext("<") ||
                isNext("<=") || isNext(">") || isNext(">=")){
            pushOperation();
            return true;
        } else {
            return false;
        }
    }

    private static boolean additionOperation() {
        if(isNext("+") || isNext("-") || isNext("or")){
            pushOperation();
            return true;
        } else {
            return false;
        }
    }

    private static boolean multiplicationOperation() {
        if(isNext("*") || isNext("/") || isNext("and")){
            pushOperation();
            return true;
        } else {
            return false;
        }
    }

    private static void pushOperation(){
        expressionStack.push(delimiters.get(next.getNumber()));
    }

    private static void pushNumber(){
        String number = numbers.get(next.getNumber());
        try{
            Integer.parseInt(number);
            expressionStack.push(INTEGER);
        } catch (NumberFormatException e){
            expressionStack.push(REAL);
        }
    }

    private static void pushIdentifier(){
        expressionStack.push(words.get(identifiers.get(next.getNumber()).getType()));
    }

    private static void pushLogical(){
        expressionStack.push(BOOLEAN);
    }

    private static void getNext() {
        try {
            next = lexemeInput.getLexeme();
            needGetNext = true;
            column++;
        } catch (Exception ex) {
            //ex.printStackTrace();
        }
    }
}
