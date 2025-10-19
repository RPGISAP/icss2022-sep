package nl.han.ica.datastructures;

import java.util.NoSuchElementException;

public class HANStack<T> implements IHANStack<T> {

    private final HANLinkedList<T> lijst = new HANLinkedList<>();

    @Override
    public void push(T waarde) {
        lijst.addFirst(waarde);
    }

    @Override
    public T pop() {
        T boven = peek();
        lijst.removeFirst();
        return boven;
    }

    @Override
    public T peek() {
        try {
            return lijst.getFirst();
        } catch (NoSuchElementException e) {
            throw new NoSuchElementException("Stack is leeg.");
        }
    }
}

