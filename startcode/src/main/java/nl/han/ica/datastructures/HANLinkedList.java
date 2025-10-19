package nl.han.ica.datastructures;

import java.util.NoSuchElementException;

public class HANLinkedList<T> implements IHANLinkedList<T> {

    private static final class Knoop<E> {
        E waarde;
        Knoop<E> volgende;
        Knoop(E waarde, Knoop<E> volgende) { this.waarde = waarde; this.volgende = volgende; }
    }

    private Knoop<T> hoofd;      // eerste element (null als leeg)
    private int grootte;         // aantal elementen

    @Override
    public void addFirst(T waarde) {
        hoofd = new Knoop<>(waarde, hoofd);
        grootte++;
    }

    @Override
    public void clear() {
        hoofd = null;
        grootte = 0;
    }

    @Override
    public void insert(int index, T waarde) {
        controleerIndexInclusiefEinde(index);
        if (index == 0) {
            addFirst(waarde);
            return;
        }
        Knoop<T> vorige = knoopOpIndex(index - 1);
        vorige.volgende = new Knoop<>(waarde, vorige.volgende);
        grootte++;
    }

    @Override
    public void delete(int positie) {
        controleerIndex(positie);
        if (positie == 0) {
            removeFirst();
            return;
        }
        Knoop<T> vorige = knoopOpIndex(positie - 1);
        if (vorige.volgende == null) throw new NoSuchElementException("Lege positie.");
        vorige.volgende = vorige.volgende.volgende;
        grootte--;
    }

    @Override
    public T get(int positie) {
        controleerIndex(positie);
        return knoopOpIndex(positie).waarde;
    }

    @Override
    public void removeFirst() {
        if (hoofd == null) throw new NoSuchElementException("Lijst is leeg.");
        hoofd = hoofd.volgende;
        grootte--;
    }

    @Override
    public T getFirst() {
        if (hoofd == null) throw new NoSuchElementException("Lijst is leeg.");
        return hoofd.waarde;
    }

    @Override
    public int getSize() {
        return grootte;
    }

    // ---------- hulpfuncties ----------
    private Knoop<T> knoopOpIndex(int index) {
        Knoop<T> lopend = hoofd;
        for (int i = 0; i < index; i++) {
            if (lopend == null) throw new NoSuchElementException("Index buiten bereik.");
            lopend = lopend.volgende;
        }
        return lopend;
    }

    private void controleerIndex(int index) {
        if (index < 0 || index >= grootte) {
            throw new IndexOutOfBoundsException("Index " + index + " buiten bereik [0.." + (grootte - 1) + "]");
        }
    }

    private void controleerIndexInclusiefEinde(int index) {
        if (index < 0 || index > grootte) {
            throw new IndexOutOfBoundsException("Index " + index + " buiten bereik [0.." + grootte + "]");
        }
    }
}

