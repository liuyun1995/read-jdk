package jdk7.util.collection;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class HashMap<K,V> extends AbstractMap<K,V> implements Map<K,V>, Cloneable, Serializable {

	//默认初始容量
	static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;
	
	//默认最大容量
	static final int MAXIMUM_CAPACITY = 1 << 30;
	
	//默认加载因子, 指哈希表可以达到多满的尺度
	static final float DEFAULT_LOAD_FACTOR = 0.75f;
	
	//空的哈希表
	static final Entry<?,?>[] EMPTY_TABLE = {};
	
	//实际使用的哈希表
	transient Entry<K,V>[] table = (Entry<K,V>[]) EMPTY_TABLE;
	
	//HashMap大小, 即HashMap存储的键值对数量
	transient int size;
	
	//键值对的阈值, 用于判断是否需要扩增哈希表容量
	int threshold;
	
	//加载因子
	final float loadFactor;
	
	//修改次数, 用于fail-fast机制
	transient int modCount;
	
	//使用替代哈希的默认阀值
	static final int ALTERNATIVE_HASHING_THRESHOLD_DEFAULT = Integer.MAX_VALUE;

    //替代哈希阀值的持有类
    private static class Holder {

    	//切换使用替代哈希时的阀值
        static final int ALTERNATIVE_HASHING_THRESHOLD;

        static {
        	//从系统属性中获取阀值
            String altThreshold = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("jdk.map.althashing.threshold"));

            int threshold;
            
            try {
            	//如果系统属性中没有就使用Integer.MAX_VALUE
                threshold = (null != altThreshold)
                        ? Integer.parseInt(altThreshold)
                        : ALTERNATIVE_HASHING_THRESHOLD_DEFAULT;

                //系统属性默认为-1, 也就是不开启替代哈希
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }
                //如果阀值小于0就抛出异常
                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch(IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }
            //替代哈希的阀值
            ALTERNATIVE_HASHING_THRESHOLD = threshold;
        }
    }

    //随机的哈希种子, 有助于减少哈希碰撞的次数
    transient int hashSeed = 0;

	//构造器, 传入初始化容量和加载因子
	public HashMap(int initialCapacity, float loadFactor) {
	    if (initialCapacity < 0) {
	    	throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
	    }
	    //如果初始化容量大于最大容量, 就把它设为最大容量
	    if (initialCapacity > MAXIMUM_CAPACITY) {
	    	initialCapacity = MAXIMUM_CAPACITY;
	    }
	    //如果加载因子小于0或者加载因子不是浮点数, 则抛出异常
	    if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
	    	throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
	    }
	    //设置加载因子
	    this.loadFactor = loadFactor;
	    //阈值为初始化容量
	    threshold = initialCapacity;
	    init();
	}
	
	//构造器, 只传入初始化容量
	public HashMap(int initialCapacity) {
	    this(initialCapacity, DEFAULT_LOAD_FACTOR);
	}
	
	//构造器,使用默认初始化容量和加载因子
	public HashMap() {
	    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
	}
	
	//构造器, 通过传入的Map构造
	public HashMap(Map<? extends K, ? extends V> m) {
		//调用构造器, 初始化容量为传入map容量和默认容量的大的一方
	    this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
	    //初始化哈希表
	    inflateTable(threshold);
	    putAllForCreate(m);
	}

    //调整到2的幂
    private static int roundUpToPowerOf2(int number) {
        return number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
    }

	//初始化哈希表, 会对哈希表容量进行膨胀, 因为有可能传入的容量不是2的幂
	private void inflateTable(int toSize) {
		//哈希表容量必须是2的次幂
	    int capacity = roundUpToPowerOf2(toSize);
	    //设置阀值, 这里一般是取capacity*loadFactor
	    threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
	    //新建指定容量的哈希表
	    table = new Entry[capacity];
	    //初始化哈希种子
	    initHashSeedAsNeeded(capacity);
	}

    //init方法在HashMap中没有实际实现，不过在其子类如 linkedHashMap中就会有对应实现
    void init() {}

    //初始化哈希种子
    final boolean initHashSeedAsNeeded(int capacity) {
    	//是否存在备用哈希
        boolean currentAltHashing = hashSeed != 0;
        //是否使用备用哈希
        boolean useAltHashing = sun.misc.VM.isBooted() && (capacity >= Holder.ALTERNATIVE_HASHING_THRESHOLD);
        //判断是否使用备用哈希, 相同为true, 不同为false
        boolean switching = currentAltHashing ^ useAltHashing;
        //设置哈希种子
        if (switching) {
            hashSeed = useAltHashing ? sun.misc.Hashing.randomHashSeed(this) : 0;
        }
        return switching;
    }

	//生成hash码的函数
	final int hash(Object k) {
	    int h = hashSeed;
	    //key是String类型的就使用另外的哈希算法
	    if (0 != h && k instanceof String) {
	        return sun.misc.Hashing.stringHash32((String) k);
	    }
	    h ^= k.hashCode();
	    //扰动函数
	    h ^= (h >>> 20) ^ (h >>> 12);
	    return h ^ (h >>> 7) ^ (h >>> 4);
	}

	//返回哈希码对应的数组下标
	static int indexFor(int h, int length) {
		//      length: 00000000 00000000 00000000 00010000
		//    length-1: 00000000 00000000 00000000 00001111
		//       如果h为: 01111111 01010110 11011101 10101010
		//h&(length-1): 00000000 00000000 00000000 00001010
	    return h & (length-1);
	}

    //返回map中键值对的数量
    public int size() {
        return size;
    }

    //判断map包含的键值对是否为空
    public boolean isEmpty() {
        return size == 0;
    }

    //根据key获取值
    public V get(Object key) {
    	//如果key为null, 则直接获取null对应的value
        if (key == null) {
        	return getForNullKey();
        }
        //否则先获取key所对应的Entry
        Entry<K,V> entry = getEntry(key);
        //如果Entry不为空就返回Entry的value值
        return null == entry ? null : entry.getValue();
    }

    //获取null对应的value
    private V getForNullKey() {
        if (size == 0) {
            return null;
        }
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
        	//当key为null时是没有hash码的,因此在这里不对hash码作比较
            if (e.key == null) {
            	return e.value;
            }
        }
        return null;
    }

    //判断HashMap中是否包含指定key对应的值
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    //获取key对应的Entry
    final Entry<K,V> getEntry(Object key) {
        if (size == 0) {
            return null;
        }
        //当key为null时hash为0, 否则计算key的哈希码
        int hash = (key == null) ? 0 : hash(key);
        for (Entry<K,V> e = table[indexFor(hash, table.length)]; e != null; e = e.next) {
            Object k;
            //对hash码和key进行比较
            //不同对象计算出来的hash值可能相同也可能不同
            //相同对象计算出来的hash值可能相同也可能不同
            //取决于怎样定义equals函数和hashCode函数
            if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
            	return e;
            }
        }
        return null;
    }

	//放置key-value键值对到HashMap中
	public V put(K key, V value) {
		//如果哈希表没有初始化就进行初始化
	    if (table == EMPTY_TABLE) {
	    	//初始化哈希表
	        inflateTable(threshold);
	    }
	    if (key == null) {
	    	return putForNullKey(value);
	    }
	    //计算key的hash码
	    int hash = hash(key);
	    //根据hash码定位在哈希表的位置
	    int i = indexFor(hash, table.length);
	    for (Entry<K,V> e = table[i]; e != null; e = e.next) {
	        Object k;
	        //如果对应的key已经存在, 就替换它的value值, 并返回原先的value值
	        if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
	            V oldValue = e.value;
	            e.value = value;
	            e.recordAccess(this);
	            return oldValue;
	        }
	    }
	    modCount++;
	    //如果没有对应的key就添加Entry到HashMap中
	    addEntry(hash, key, value, i);
	    //添加成功返回null
	    return null;
	}

    //放置key为null时的value值
    private V putForNullKey(V value) {
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }
        modCount++;
        addEntry(0, null, value, 0);
        return null;
    }

    //放置的时候新建一个Entry
    private void putForCreate(K key, V value) {
    	//获取key的哈希码
        int hash = null == key ? 0 : hash(key);
        //找出哈希码在哈希表对应的位置
        int i = indexFor(hash, table.length);

        //遍历哈希表对应槽位的链表
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                e.value = value;
                return;
            }
        }
        //创建新的Entry
        createEntry(hash, key, value, i);
    }

    //将另一个map的值全部放入
    private void putAllForCreate(Map<? extends K, ? extends V> m) {
    	//遍历map, 一个一个Entry赋值
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
        	putForCreate(e.getKey(), e.getValue());
        }
    }

	//对哈希表进行扩容
	void resize(int newCapacity) {
	    Entry[] oldTable = table;
	    int oldCapacity = oldTable.length;
	    //如果当前已经是最大容量就只能增大阀值了
	    if (oldCapacity == MAXIMUM_CAPACITY) {
	        threshold = Integer.MAX_VALUE;
	        return;
	    }
	    //否则就进行扩容
	    Entry[] newTable = new Entry[newCapacity];
	    //迁移哈希表的方法
	    transfer(newTable, initHashSeedAsNeeded(newCapacity));
	    //将当前哈希表设置为新的哈希表
	    table = newTable;
	    //更新哈希表阈值
	    threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
	}

    //迁移哈希表的方法
    void transfer(Entry[] newTable, boolean rehash) {
        int newCapacity = newTable.length;
        //遍历哈希表
        for (Entry<K,V> e : table) {
            while(null != e) {
                Entry<K,V> next = e.next;
                //是否再哈希
                if (rehash) {
                	//为每个key重新计算hash码
                    e.hash = null == e.key ? 0 : hash(e.key);
                }
                //重新分配位置
                int i = indexFor(e.hash, newCapacity);
                //将一下个节点置空, 会打乱之前的链表
                e.next = newTable[i];
                newTable[i] = e;
                //下一个
                e = next;
            }
        }
    }

    //从指定的HashMap中复制所有映射到当前的Map
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;

        if (table == EMPTY_TABLE) {
        	//初始化哈希表
            inflateTable((int) Math.max(numKeysToBeAdded * loadFactor, threshold));
        }

        /*
         * Expand the map if the map if the number of mappings to be added
         * is greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourself
         * to at most one extra resize.
         */
        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = table.length;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        //遍历所有Entry依次进行放入
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
        	put(e.getKey(), e.getValue());
        }
    }

    //移除指定key对应的值, 方法返回value
    public V remove(Object key) {
        Entry<K,V> e = removeEntryForKey(key);
        return (e == null ? null : e.value);
    }

    //移除指定key对应的Entry, 方法返回Entry
    final Entry<K,V> removeEntryForKey(Object key) {
        if (size == 0) {
            return null;
        }
        //获取key对应哈希码
        int hash = (key == null) ? 0 : hash(key);
        //获取哈希码对应的在哈希表中的槽位
        int i = indexFor(hash, table.length);
        //获得指定槽位的链表的头结点
        Entry<K,V> prev = table[i];
        Entry<K,V> e = prev;

        //遍历链表
        while (e != null) {
        	//获取当前节点的下一个节点
            Entry<K,V> next = e.next;
            Object k;
            //当前节点的哈希码与指定哈希码相等, 并且当前节点的key与指定的key相等
            if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
            	//修改次数加一
                modCount++;
                //HashMap大小减一
                size--;
                if (prev == e)
                    table[i] = next;
                else
                    prev.next = next;
                e.recordRemoval(this);
                return e;
            }
            prev = e;
            e = next;
        }

        return e;
    }

    /**
     * Special version of remove for EntrySet using {@code Map.Entry.equals()}
     * for matching.
     */
    final Entry<K,V> removeMapping(Object o) {
        if (size == 0 || !(o instanceof Map.Entry))
            return null;

        Map.Entry<K,V> entry = (Map.Entry<K,V>) o;
        Object key = entry.getKey();
        int hash = (key == null) ? 0 : hash(key);
        int i = indexFor(hash, table.length);
        Entry<K,V> prev = table[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (e.hash == hash && e.equals(entry)) {
                modCount++;
                size--;
                if (prev == e)
                    table[i] = next;
                else
                    prev.next = next;
                e.recordRemoval(this);
                return e;
            }
            prev = e;
            e = next;
        }

        return e;
    }

    //清空HashMap
    public void clear() {
        modCount++;
        Arrays.fill(table, null);
        size = 0;
    }

    //判断是否包含某个指定的值
    public boolean containsValue(Object value) {
        if (value == null)
            return containsNullValue();

        Entry[] tab = table;
        for (int i = 0; i < tab.length ; i++)
            for (Entry e = tab[i] ; e != null ; e = e.next)
                if (value.equals(e.value))
                    return true;
        return false;
    }

    //判断是否包含空值
    private boolean containsNullValue() {
        Entry[] tab = table;
        for (int i = 0; i < tab.length ; i++) {
        	for (Entry e = tab[i] ; e != null ; e = e.next) {
        		//遍历每个节点, 判断值是否为空
        		if (e.value == null) {
        			return true;
        		}
        	}
        }
        return false;
    }

    //返回HashMap的浅拷贝
    public Object clone() {
        HashMap<K,V> result = null;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // assert false;
        }
        if (result.table != EMPTY_TABLE) {
            result.inflateTable(Math.min(
                (int) Math.min(
                    size * Math.min(1 / loadFactor, 4.0f),
                    // we have limits...
                    HashMap.MAXIMUM_CAPACITY),
               table.length));
        }
        result.entrySet = null;
        result.modCount = 0;
        result.size = 0;
        result.init();
        result.putAllForCreate(this);

        return result;
    }

    
	static class Entry<K,V> implements Map.Entry<K,V> {
	    final K key;      //键
	    V value;          //值
	    Entry<K,V> next;  //下一个Entry的引用
	    int hash;         //哈希码
	
	    Entry(int h, K k, V v, Entry<K,V> n) {
	        value = v;
	        next = n;
	        key = k;
	        hash = h;
	    }
	
	    public final K getKey() {
	        return key;
	    }
	
	    public final V getValue() {
	        return value;
	    }
	
	    public final V setValue(V newValue) {
	        V oldValue = value;
	        value = newValue;
	        return oldValue;
	    }
	
	    public final boolean equals(Object o) {
	        if (!(o instanceof Map.Entry))
	            return false;
	        Map.Entry e = (Map.Entry)o;
	        Object k1 = getKey();
	        Object k2 = e.getKey();
	        if (k1 == k2 || (k1 != null && k1.equals(k2))) {
	            Object v1 = getValue();
	            Object v2 = e.getValue();
	            if (v1 == v2 || (v1 != null && v1.equals(v2)))
	                return true;
	        }
	        return false;
	    }
	
	    public final int hashCode() {
	        return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
	    }
	
	    public final String toString() {
	        return getKey() + "=" + getValue();
	    }
	
	    //该方法会在value被覆盖时调用
	    void recordAccess(HashMap<K,V> m) {}
	
	    //该方法会在Entry被移除时调用
	    void recordRemoval(HashMap<K,V> m) {}
	}

	//添加Entry方法, 先判断是否要扩容
	void addEntry(int hash, K key, V value, int bucketIndex) {
		//如果HashMap的大小大于阀值并且哈希表对应槽位的值不为空
	    if ((size >= threshold) && (null != table[bucketIndex])) {
	    	//因为HashMap的大小大于阀值, 表明即将发生哈希冲突, 所以进行扩容
	        resize(2 * table.length);
	        hash = (null != key) ? hash(key) : 0;
	        bucketIndex = indexFor(hash, table.length);
	    }
	    //在这里表明HashMap的大小没有超过阀值, 所以不需要扩容
	    createEntry(hash, key, value, bucketIndex);
	}

    //新建Entry方法
    void createEntry(int hash, K key, V value, int bucketIndex) {
    	//取出哈希表中指定槽位的Entry
        Entry<K,V> e = table[bucketIndex];
        //新建一个Entry放入槽位中
        table[bucketIndex] = new Entry<>(hash, key, value, e);
        //将HashMap的大小加1
        size++;
    }

    //哈希迭代器, 是key迭代器, value迭代器, entry迭代器的父类
    private abstract class HashIterator<E> implements Iterator<E> {
        Entry<K,V> next;        // 下一个要返回的entry
        int expectedModCount;   // 用于fast-fail机制
        int index;              // 当前槽位
        Entry<K,V> current;     // 当前entry

        HashIterator() {
            expectedModCount = modCount;
            if (size > 0) { // advance to first entry
                Entry[] t = table;
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        //返回下一个entry
        final Entry<K,V> nextEntry() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            Entry<K,V> e = next;
            if (e == null)
                throw new NoSuchElementException();

            if ((next = e.next) == null) {
                Entry[] t = table;
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
            current = e;
            return e;
        }

        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            Object k = current.key;
            current = null;
            HashMap.this.removeEntryForKey(k);
            expectedModCount = modCount;
        }
    }

    //值的迭代器
    private final class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    //key的迭代器
    private final class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    //Entry迭代器
    private final class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    //新建key迭代器
    Iterator<K> newKeyIterator()   {
        return new KeyIterator();
    }
    
    //新建value迭代器
    Iterator<V> newValueIterator()   {
        return new ValueIterator();
    }
    
    //新建Entry迭代器
    Iterator<Map.Entry<K,V>> newEntryIterator()   {
        return new EntryIterator();
    }


    //HashMap的所有Entry集合
    private transient Set<Map.Entry<K,V>> entrySet = null;

    //返回HashMap中所有的key
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    //key的集合
    private final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return newKeyIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return HashMap.this.removeEntryForKey(o) != null;
        }
        public void clear() {
            HashMap.this.clear();
        }
    }

    //返回HashMap中所有的值的集合
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }

    //值的集合
    private final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return newValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            HashMap.this.clear();
        }
    }

    //返回HashMap中所有Entry的集合
    public Set<Map.Entry<K,V>> entrySet() {
        return entrySet0();
    }

    //返回HashMap中所有Entry的集合
    private Set<Map.Entry<K,V>> entrySet0() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    //Entry的集合
    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return newEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(Object o) {
            return removeMapping(o) != null;
        }
        public int size() {
            return size;
        }
        public void clear() {
            HashMap.this.clear();
        }
    }

    //将HashMap实例写入到流中
    private void writeObject(jdk7.io.ObjectOutputStream s) throws IOException {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out number of buckets
        if (table==EMPTY_TABLE) {
            s.writeInt(roundUpToPowerOf2(threshold));
        } else {
           s.writeInt(table.length);
        }

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        if (size > 0) {
            for(Map.Entry<K,V> e : entrySet0()) {
                s.writeObject(e.getKey());
                s.writeObject(e.getValue());
            }
        }
    }

    private static final long serialVersionUID = 362498820763181265L;

    //从流中读取HashMap实例
    private void readObject(jdk7.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " + loadFactor);
        }

        // set other fields that need values
        table = (Entry<K,V>[]) EMPTY_TABLE;

        // Read in number of buckets
        s.readInt(); // ignored.

        // Read number of mappings
        int mappings = s.readInt();
        if (mappings < 0) {
        	throw new InvalidObjectException("Illegal mappings count: " + mappings);
        }

        // capacity chosen by number of mappings and desired load (if >= 0.25)
        int capacity = (int) Math.min(mappings * Math.min(1 / loadFactor, 4.0f), HashMap.MAXIMUM_CAPACITY);

        // allocate the bucket array;
        if (mappings > 0) {
            inflateTable(capacity);
        } else {
            threshold = capacity;
        }

        init();  // Give subclass a chance to do its thing.

        // Read the keys and values, and put the mappings in the HashMap
        for (int i = 0; i < mappings; i++) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            putForCreate(key, value);
        }
    }

    // These methods are used when serializing HashSets
    int   capacity()     { return table.length; }
    float loadFactor()   { return loadFactor;   }
}
