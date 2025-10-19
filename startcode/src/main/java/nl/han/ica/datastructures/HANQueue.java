package nl.han.ica.datastructures;

import java.util.NoSuchElementException;

public class HANQueue<T> implements IHANQueue<T> {

    private static final class Knoop<E> {
        E waarde;
        Knoop<E> volgende;
        Knoop(E waarde) { this.waarde = waarde; }
    }

    private Knoop<T> kop;   // vooraan (dequeue/peek)
    private Knoop<T> staart; // achteraan (enqueue)
    private int grootte;

    @Override
    public void clear() {
        kop = staart = null;
        grootte = 0;
    }

    @Override
    public boolean isEmpty() {
        return grootte == 0;
    }

    @Override
    public void enqueue(T waarde) {
        Knoop<T> nieuw = new Knoop<>(waarde);
        if (staart == null) {
            kop = staart = nieuw;
        } else {
            staart.volgende = nieuw;
            staart = nieuw;
        }
        grootte++;
    }

    @Override
    public T dequeue() {
        if (kop == null) throw new NoSuchElementException("Queue is leeg.");
        T waarde = kop.waarde;
        kop = kop.volgende;
        if (kop == null) staart = null;
        grootte--;
        return waarde;
    }

    @Override
    public T peek() {
        if (kop == null) throw new NoSuchElementException("Queue is leeg.");
        return kop.waarde;
    }

    @Override
    public int getSize() {
        return grootte;
    }
}

