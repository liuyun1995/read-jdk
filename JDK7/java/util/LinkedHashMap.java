package jdk7.util.collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {

    private static final long serialVersionUID = 3801124242820219131L;

    //双向链表头结点
    private transient Entry<K,V> header;

    //是否按访问顺序排序
    private final boolean accessOrder;

    
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    public LinkedHashMap() {
        super();
        accessOrder = false;
    }

    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super(m);
        accessOrder = false;
    }

	public LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder) {
	    super(initialCapacity, loadFactor);
	    this.accessOrder = accessOrder;
	}

    //HashMap构造器会调用该方法, 用于初始化双向链表
    @Override
    void init() {
    	//头结点引用指向这个新建的Entry
        header = new Entry<>(-1, null, null, null);
        header.before = header.after = header;
    }

    //将元素迁移到新的哈希表
    @Override
    void transfer(HashMap.Entry[] newTable, boolean rehash) {
    	//获取新哈希表的容量
        int newCapacity = newTable.length;
        //从头结点开始遍历双向链表
        for (Entry<K,V> e = header.after; e != header; e = e.after) {
        	//判断是否需要再哈希
            if (rehash) {
            	e.hash = (e.key == null) ? 0 : hash(e.key);
            }
            //根据hash码获取在新的数组中的下标
            int index = indexFor(e.hash, newCapacity);
            //将结点在单向链表中的下一个结点的引用置空
            e.next = newTable[index];
            //将新数组中下标为index槽位的结点引用指向当前结点
            newTable[index] = e;
        }
    }


    //判断集合中是否包含给定的value
    public boolean containsValue(Object value) {
        if (value==null) {
            for (Entry e = header.after; e != header; e = e.after) {
            	if (e.value==null) {
                	return true;
                }
            }
        } else {
        	//从头结点开始遍历双向链表
            for (Entry e = header.after; e != header; e = e.after) {
            	 if (value.equals(e.value)) {
                 	return true;
                 }
            }
        }
        return false;
    }

	//根据key获取value值
	public V get(Object key) {
		//调用父类方法获取key对应的Entry
	    Entry<K,V> e = (Entry<K,V>)getEntry(key);
	    if (e == null) {
	    	return null;
	    }
	    //如果是按访问顺序排序的话, 会将每次使用后的结点放到双向链表的尾部
	    e.recordAccess(this);
	    return e.value;
	}

    //清空全部元素
    public void clear() {
    	//将哈希表的元素清空
        super.clear();
        //将双向链表头结点置空
        header.before = header.after = header;
    }

	private static class Entry<K,V> extends HashMap.Entry<K,V> {
		//当前结点在双向链表中的前继结点的引用
	    Entry<K,V> before;
	    //当前结点在双向链表中的后继结点的引用
	    Entry<K,V> after;
	
	    Entry(int hash, K key, V value, HashMap.Entry<K,V> next) {
	        super(hash, key, value, next);
	    }
	
	    //从双向链表中移除该结点
	    private void remove() {
	        before.after = after;
	        after.before = before;
	    }
	
	    //将当前结点插入到双向链表中一个已存在的结点前面
	    private void addBefore(Entry<K,V> existingEntry) {
	    	//当前结点的下一个结点的引用指向给定结点
	        after  = existingEntry;
	        //当前结点的上一个结点的引用指向给定结点的上一个结点
	        before = existingEntry.before;
	        //给定结点的上一个结点的下一个结点的引用指向当前结点
	        before.after = this;
	        //给定结点的上一个结点的引用指向当前结点
	        after.before = this;
	    }
	
	    //按访问顺序排序时, 记录每次获取的操作
	    void recordAccess(HashMap<K,V> m) {
	        LinkedHashMap<K,V> lm = (LinkedHashMap<K,V>)m;
	        //如果是按访问顺序排序
	        if (lm.accessOrder) {
	            lm.modCount++;
	            //先将自己从双向链表中移除
	            remove();
	            //将自己放到双向链表尾部
	            addBefore(lm.header);
	        }
	    }
	    
	    void recordRemoval(HashMap<K,V> m) {
	        remove();
	    }
	}

    //双向顺序链表的迭代器
    private abstract class LinkedHashIterator<T> implements Iterator<T> {
    	//要返回的下一个结点的引用
        Entry<K,V> nextEntry    = header.after;
        //当前返回结点的引用
        Entry<K,V> lastReturned = null;
        
        int expectedModCount = modCount;

        //判断是否有下一个元素
        public boolean hasNext() {
            return nextEntry != header;
        }

        //移除结点
        public void remove() {
            if (lastReturned == null) {
            	throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
            	throw new ConcurrentModificationException();
            }
            //将当前返回的结点从哈希表中移除
            LinkedHashMap.this.remove(lastReturned.key);
            //将引用置空
            lastReturned = null;
            expectedModCount = modCount;
        }

        //返回顺序链表中的下一个结点
        Entry<K,V> nextEntry() {
            if (modCount != expectedModCount) {
            	throw new ConcurrentModificationException();
            }
            if (nextEntry == header) {
            	throw new NoSuchElementException();
            }
            //先返回第一个结点
            Entry<K,V> e = lastReturned = nextEntry;
            //再指向下一个结点
            nextEntry = e.after;
            return e;
        }
    }

    private class KeyIterator extends LinkedHashIterator<K> {
        public K next() { return nextEntry().getKey(); }
    }

    private class ValueIterator extends LinkedHashIterator<V> {
        public V next() { return nextEntry().value; }
    }

    private class EntryIterator extends LinkedHashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() { return nextEntry(); }
    }

    Iterator<K> newKeyIterator()   { return new KeyIterator();   }
    Iterator<V> newValueIterator() { return new ValueIterator(); }
    Iterator<Map.Entry<K,V>> newEntryIterator() { return new EntryIterator(); }

	//父类put方法中会调用的该方法
	void addEntry(int hash, K key, V value, int bucketIndex) {
		//调用父类的addEntry方法
	    super.addEntry(hash, key, value, bucketIndex);
	    //下面操作是方便LRU缓存的实现, 如果缓存容量不足, 就移除最老的元素
	    Entry<K,V> eldest = header.after;
	    if (removeEldestEntry(eldest)) {
	        removeEntryForKey(eldest.key);
	    }
	}
	
	//父类的addEntry方法中会调用该方法
	void createEntry(int hash, K key, V value, int bucketIndex) {
		//先获取HashMap的Entry
	    HashMap.Entry<K,V> old = table[bucketIndex];
	    //包装成LinkedHashMap自身的Entry
	    Entry<K,V> e = new Entry<>(hash, key, value, old);
	    table[bucketIndex] = e;
	    //将当前结点插入到双向链表的尾部
	    e.addBefore(header);
	    size++;
	}

	//是否删除最老的元素
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return false;
    }
}
