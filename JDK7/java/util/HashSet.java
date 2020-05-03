package jdk7.util.collection;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {
    
	static final long serialVersionUID = -5024744406713321676L;

    private transient HashMap<E,Object> map;

    //这个值是作为map的value放入的
    private static final Object PRESENT = new Object();

    //构造器
    public HashSet() {
        map = new HashMap<>();
    }

    //构造器
    public HashSet(Collection<? extends E> c) {
        map = new HashMap<>(Math.max((int) (c.size()/.75f) + 1, 16));
        addAll(c);
    }

    //构造器
    public HashSet(int initialCapacity, float loadFactor) {
        map = new HashMap<>(initialCapacity, loadFactor);
    }

    //构造器
    public HashSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    //构造器, LinkedHashSet调用了该构造器
    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<>(initialCapacity, loadFactor);
    }

    //迭代器
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    //集合大小
    public int size() {
        return map.size();
    }

    //是否为空
    public boolean isEmpty() {
        return map.isEmpty();
    }

    //是否包含某个元素
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    //添加一个元素
    public boolean add(E e) {
    	//将元素作为key放入map中
        return map.put(e, PRESENT) == null;
    }

    //移除指定元素
    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    //清空集合
    public void clear() {
        map.clear();
    }

    //克隆集合
    public Object clone() {
        try {
            HashSet<E> newSet = (HashSet<E>) super.clone();
            newSet.map = (HashMap<E, Object>) map.clone();
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    //写入到流
    private void writeObject(jdk7.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeInt(map.capacity());
        s.writeFloat(map.loadFactor());
        s.writeInt(map.size());
        for (E e : map.keySet()) {
        	s.writeObject(e);
        }
    }

    //从流中读取
    private void readObject(jdk7.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        int capacity = s.readInt();
        float loadFactor = s.readFloat();
        map = (((HashSet)this) instanceof LinkedHashSet ?
               new LinkedHashMap<E,Object>(capacity, loadFactor) :
               new HashMap<E,Object>(capacity, loadFactor));
        int size = s.readInt();
        for (int i=0; i<size; i++) {
            E e = (E) s.readObject();
            map.put(e, PRESENT);
        }
    }
}
