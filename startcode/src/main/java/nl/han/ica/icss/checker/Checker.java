package nl.han.ica.icss.checker;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.types.ExpressionType;

import java.util.HashMap;

public class Checker {
    private IHANLinkedList<HashMap<String, ExpressionType>> variabeleTypen;

    public void check(AST ast) {
        variabeleTypen = new HANLinkedList<>();
    }
}

