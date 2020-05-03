package jdk7.util.concurrent;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import jdk7.io.ObjectInputStream;
import jdk7.util.concurrent.locks.ReentrantLock;

public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {
	
    private static final long serialVersionUID = 7249069246763182397L;

	//默认初始化容量
	static final int DEFAULT_INITIAL_CAPACITY = 16;
	
	//默认加载因子
	static final float DEFAULT_LOAD_FACTOR = 0.75f;
	
	//默认并发级别
	static final int DEFAULT_CONCURRENCY_LEVEL = 16;
	
	//集合最大容量
	static final int MAXIMUM_CAPACITY = 1 << 30;
	
	//分段锁的最小数量
	static final int MIN_SEGMENT_TABLE_CAPACITY = 2;
	
	//分段锁的最大数量
	static final int MAX_SEGMENTS = 1 << 16;
	
	//加锁前的重试次数
	static final int RETRIES_BEFORE_LOCK = 2;
	
	//分段锁的掩码值
	final int segmentMask;
	
	//分段锁的移位值
	final int segmentShift;
	
	//分段锁数组
	final Segment<K,V>[] segments;
    
    //哈希种子
    private transient final int hashSeed = randomHashSeed(this);

    transient Set<K> keySet;                  //键的集合
    transient Set<Map.Entry<K,V>> entrySet;   //entry集合
    transient Collection<V> values;           //值的集合

    private static class Holder {
        static final boolean ALTERNATIVE_HASHING;
        static {
            String altThreshold = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("jdk.map.althashing.threshold"));
            int threshold;
            try {
                threshold = (null != altThreshold) ? Integer.parseInt(altThreshold) : Integer.MAX_VALUE;
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }
                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch(IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }
            ALTERNATIVE_HASHING = threshold <= MAXIMUM_CAPACITY;
        }
    }

    private static int randomHashSeed(ConcurrentHashMap instance) {
        if (sun.misc.VM.isBooted() && Holder.ALTERNATIVE_HASHING) {
            return sun.misc.Hashing.randomHashSeed(instance);
        }
        return 0;
    }
    

    static final class HashEntry<K,V> {
        final int hash;
        final K key;
        volatile V value;
        volatile HashEntry<K,V> next;

        HashEntry(int hash, K key, V value, HashEntry<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
        
        final void setNext(HashEntry<K,V> n) {
            UNSAFE.putOrderedObject(this, nextOffset, n);
        }

        static final sun.misc.Unsafe UNSAFE;
        static final long nextOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class k = HashEntry.class;
                nextOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }
    
    //返回哈希表中指定下标的元素
    @SuppressWarnings("unchecked")
    static final <K,V> HashEntry<K,V> entryAt(HashEntry<K,V>[] tab, int i) {
        return (tab == null) ? null : (HashEntry<K,V>) UNSAFE.getObjectVolatile(tab, ((long)i << TSHIFT) + TBASE);
    }
    
    //设置哈希表中指定下标的元素
    static final <K,V> void setEntryAt(HashEntry<K,V>[] tab, int i, HashEntry<K,V> e) {
        UNSAFE.putOrderedObject(tab, ((long)i << TSHIFT) + TBASE, e);
    }
    
    //哈希函数
    private int hash(Object k) {
        int h = hashSeed;
        if ((0 != h) && (k instanceof String)) {
            return sun.misc.Hashing.stringHash32((String) k);
        }
        h ^= k.hashCode();
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);
        return h ^ (h >>> 16);
    }

	//分段锁
	static final class Segment<K,V> extends ReentrantLock implements Serializable {
	
	    private static final long serialVersionUID = 2249069246763182397L;
	
	    //自旋最大次数
	    static final int MAX_SCAN_RETRIES = Runtime.getRuntime().availableProcessors() > 1 ? 64 : 1;
	
	    //哈希表
	    transient volatile HashEntry<K,V>[] table;
	
	    //元素总数
	    transient int count;
	
	    //修改次数
	    transient int modCount;
	    
	    //元素阀值
	    transient int threshold;
	    
	    //加载因子
	    final float loadFactor;

        Segment(float lf, int threshold, HashEntry<K,V>[] tab) {
            this.loadFactor = lf;
            this.threshold = threshold;
            this.table = tab;
        }

		//添加键值对
		final V put(K key, int hash, V value, boolean onlyIfAbsent) {
			//尝试获取锁, 若失败则进行自旋
		    HashEntry<K,V> node = tryLock() ? null : scanAndLockForPut(key, hash, value);
		    V oldValue;
		    try {
		        HashEntry<K,V>[] tab = table;
		        //计算元素在数组中的下标
		        int index = (tab.length - 1) & hash;
		        //根据下标获取链表头结点
		        HashEntry<K,V> first = entryAt(tab, index);
		        for (HashEntry<K,V> e = first;;) {
		        	//遍历链表寻找该元素, 找到则进行替换
		            if (e != null) {
		                K k;
		                if ((k = e.key) == key || (e.hash == hash && key.equals(k))) {
		                    oldValue = e.value;
		                    //根据参数决定是否替换旧值
		                    if (!onlyIfAbsent) {
		                        e.value = value;
		                        ++modCount;
		                    }
		                    break;
		                }
		                e = e.next;
		            //没找到则在链表添加一个结点
		            } else {
		            	//将node结点插入链表头部
		                if (node != null) {
		                	node.setNext(first);
		                } else {
		                	node = new HashEntry<K,V>(hash, key, value, first);
		                }
		                //插入结点后将元素总是加1
		                int c = count + 1;
		                //元素超过阀值则进行扩容
		                if (c > threshold && tab.length < MAXIMUM_CAPACITY) {
		                	rehash(node);
		                //否则就将哈希表指定下标替换为node结点
		                } else {
		                	setEntryAt(tab, index, node);
		                }
		                ++modCount;
		                count = c;
		                oldValue = null;
		                break;
		            }
		        }
		    } finally {
		        unlock();
		    }
		    return oldValue;
		}
        
		//再哈希
		@SuppressWarnings("unchecked")
		private void rehash(HashEntry<K,V> node) {
			//获取旧哈希表的引用
		    HashEntry<K,V>[] oldTable = table;
		    //获取旧哈希表的容量
		    int oldCapacity = oldTable.length;
		    //计算新哈希表的容量(为旧哈希表的2倍)
		    int newCapacity = oldCapacity << 1;
		    //计算新的元素阀值
		    threshold = (int)(newCapacity * loadFactor);
		    //新建一个HashEntry数组
		    HashEntry<K,V>[] newTable = (HashEntry<K,V>[]) new HashEntry[newCapacity];
		    //生成新的掩码值
		    int sizeMask = newCapacity - 1;
		    //遍历旧表的所有元素
		    for (int i = 0; i < oldCapacity ; i++) {
		    	//取得链表头结点
		        HashEntry<K,V> e = oldTable[i];
		        if (e != null) {
		            HashEntry<K,V> next = e.next;
		            //计算元素在新表中的索引
		            int idx = e.hash & sizeMask;
		            //next为空表明链表只有一个结点
		            if (next == null) {
		            	//直接把该结点放到新表中
		            	newTable[idx] = e;
		            }else {
		                HashEntry<K,V> lastRun = e;
		                int lastIdx = idx;
		                //定位lastRun结点, 将lastRun之后的结点直接放到新表中
		                for (HashEntry<K,V> last = next; last != null; last = last.next) {
		                    int k = last.hash & sizeMask;
		                    if (k != lastIdx) {
		                        lastIdx = k;
		                        lastRun = last;
		                    }
		                }
		                newTable[lastIdx] = lastRun;
		                //遍历在链表lastRun结点之前的元素, 将它们依次复制到新表中
		                for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
		                    V v = p.value;
		                    int h = p.hash;
		                    int k = h & sizeMask;
		                    HashEntry<K,V> n = newTable[k];
		                    newTable[k] = new HashEntry<K,V>(h, p.key, v, n);
		                }
		            }
		        }
		    }
		    //计算传入结点在新表中的下标
		    int nodeIndex = node.hash & sizeMask;
		    //将传入结点添加到链表头结点
		    node.setNext(newTable[nodeIndex]);
		    //将新表指定下标元素换成传入结点
		    newTable[nodeIndex] = node;
		    //将哈希表引用指向新表
		    table = newTable;
		}
        
		//自旋等待获取锁(put操作)
		private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value) {
			//根据哈希码获取头结点
		    HashEntry<K,V> first = entryForHash(this, hash);
		    HashEntry<K,V> e = first;
		    HashEntry<K,V> node = null;
		    int retries = -1;
		    //在while循环内自旋
		    while (!tryLock()) {
		        HashEntry<K,V> f;
		        if (retries < 0) {
		        	//如果头结点为空就新建一个node
		            if (e == null) {
		                if (node == null) {
		                	node = new HashEntry<K,V>(hash, key, value, null);
		                }
		                retries = 0;
		            //否则就遍历链表定位该结点
		            } else if (key.equals(e.key)) {
		            	retries = 0;
		            } else {
		            	e = e.next;
		            }
		          //retries每次在这加1, 并判断是否超过最大值
		        } else if (++retries > MAX_SCAN_RETRIES) {
		            lock();
		            break;
		          //retries为偶数时去判断first有没有改变
		        } else if ((retries & 1) == 0 && (f = entryForHash(this, hash)) != first) {
		            e = first = f;
		            retries = -1;
		        }
		    }
		    return node;
		}
		
		//自旋等待获取锁(remove和replace操作)
		private void scanAndLock(Object key, int hash) {
			//根据哈希码获取链表头结点
		    HashEntry<K,V> first = entryForHash(this, hash);
		    HashEntry<K,V> e = first;
		    int retries = -1;
		    //在while循环里自旋
		    while (!tryLock()) {
		        HashEntry<K,V> f;
		        if (retries < 0) {
		        	//遍历链表定位到该结点
		            if (e == null || key.equals(e.key)) {
		            	retries = 0;
		            } else {
		            	e = e.next;
		            }
		          //retries每次在这加1, 并判断是否超过最大值
		        } else if (++retries > MAX_SCAN_RETRIES) {
		            lock();
		            break;
		          //retries为偶数时去判断first有没有改变
		        } else if ((retries & 1) == 0 && (f = entryForHash(this, hash)) != first) {
		            e = first = f;
		            retries = -1;
		        }
		    }
		}
        
        
		//删除指定元素
		final V remove(Object key, int hash, Object value) {
			//尝试获取锁, 若失败则进行自旋
		    if (!tryLock()) {
		    	scanAndLock(key, hash);
		    }
		    V oldValue = null;
		    try {
		        HashEntry<K,V>[] tab = table;
		        //计算元素在数组中的下标
		        int index = (tab.length - 1) & hash;
		        //根据下标取得数组元素(链表头结点)
		        HashEntry<K,V> e = entryAt(tab, index);
		        HashEntry<K,V> pred = null;
		        //遍历链表寻找要删除的元素
		        while (e != null) {
		            K k;
		            //next指向当前结点的后继结点
		            HashEntry<K,V> next = e.next;
		            //根据key和hash寻找对应结点
		            if ((k = e.key) == key || (e.hash == hash && key.equals(k))) {
		                V v = e.value;
		                //传入的value不等于v就跳过, 其他情况就进行删除操作
		                if (value == null || value == v || value.equals(v)) {
		                	//如果pred为空则代表要删除的结点为头结点
		                    if (pred == null) {
		                    	//重新设置链表头结点
		                    	setEntryAt(tab, index, next);
		                    } else {
		                    	//设置pred结点的后继为next结点
		                    	pred.setNext(next);
		                    }
		                    ++modCount;
		                    --count;
		                    //记录元素删除之前的值
		                    oldValue = v;
		                }
		                break;
		            }
		            //若e不是要找的结点就将pred引用指向它
		            pred = e;
		            //检查下一个结点
		            e = next;
		        }
		    } finally {
		        unlock();
		    }
		    return oldValue;
		}

		//替换元素操作(CAS操作)
		final boolean replace(K key, int hash, V oldValue, V newValue) {
			//尝试获取锁, 若失败则进行自旋
		    if (!tryLock()) {
		    	scanAndLock(key, hash);
		    }
		    boolean replaced = false;
		    try {
		        HashEntry<K,V> e;
		        //通过hash直接找到头结点然后对链表遍历
		        for (e = entryForHash(this, hash); e != null; e = e.next) {
		            K k;
		            //根据key和hash找到要替换的结点
		            if ((k = e.key) == key || (e.hash == hash && key.equals(k))) {
		            	//如果指定的当前值正确则进行替换
		                if (oldValue.equals(e.value)) {
		                    e.value = newValue;
		                    ++modCount;
		                    replaced = true;
		                }
		                //否则不进行任何操作直接返回
		                break;
		            }
		        }
		    } finally {
		        unlock();
		    }
		    return replaced;
		}

		//替换元素操作
		final V replace(K key, int hash, V value) {
			//尝试获取锁, 若失败则进行自旋
		    if (!tryLock()) {
		    	scanAndLock(key, hash);
		    }
		    V oldValue = null;
		    try {
		        HashEntry<K,V> e;
		        //通过hash直接找到头结点然后对链表遍历
		        for (e = entryForHash(this, hash); e != null; e = e.next) {
		            K k;
		            //根据key和hash找到要替换的结点, 并直接替换
		            if ((k = e.key) == key || (e.hash == hash && key.equals(k))) {
		                oldValue = e.value;
		                e.value = value;
		                ++modCount;
		                break;
		            }
		        }
		    } finally {
		        unlock();
		    }
		    return oldValue;
		}

        //清空哈希表
        final void clear() {
            lock();
            try {
                HashEntry<K,V>[] tab = table;
                for (int i = 0; i < tab.length ; i++) {
                	setEntryAt(tab, i, null);
                }
                ++modCount;
                count = 0;
            } finally {
                unlock();
            }
        }
    }
    
    //根据下标获取分段锁
    @SuppressWarnings("unchecked")
    static final <K,V> Segment<K,V> segmentAt(Segment<K,V>[] ss, int j) {
        long u = (j << SSHIFT) + SBASE;
        return ss == null ? null : (Segment<K,V>) UNSAFE.getObjectVolatile(ss, u);
    }
    
    @SuppressWarnings("unchecked")
    private Segment<K,V> ensureSegment(int k) {
    	//获取当前分段锁数组
        final Segment<K,V>[] ss = this.segments;
        //计算下标k在数组中的偏移量
        long u = (k << SSHIFT) + SBASE;
        Segment<K,V> seg;
        //第一次获取为空
        if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {
        	//获取模版对象, 根据模版对象的值来新建Segment
            Segment<K,V> proto = ss[0];
            int cap = proto.table.length;
            float lf = proto.loadFactor;
            int threshold = (int)(cap * lf);
            //新建一个HashEntry数组
            HashEntry<K,V>[] tab = (HashEntry<K,V>[])new HashEntry[cap];
            //第二次获取为空
            if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {
            	//新建一个Segment对象
                Segment<K,V> s = new Segment<K,V>(lf, threshold, tab);
                //第三次获取为空
                while ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {
                	//使用CAS操作设置该位置的Segment
                    if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s)) {
                    	break;
                    }
                }
            }
        }
        return seg;
    }
    
	//根据哈希码获取分段锁
	@SuppressWarnings("unchecked")
	private Segment<K,V> segmentForHash(int h) {
	    long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
	    return (Segment<K,V>) UNSAFE.getObjectVolatile(segments, u);
	}
	
	//根据哈希码获取元素
	@SuppressWarnings("unchecked")
	static final <K,V> HashEntry<K,V> entryForHash(Segment<K,V> seg, int h) {
	    HashEntry<K,V>[] tab;
	    return (seg == null || (tab = seg.table) == null) ? null :
	    (HashEntry<K,V>) UNSAFE.getObjectVolatile(tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
	}
    
	//核心构造器
	@SuppressWarnings("unchecked")
	public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
	    if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0) {
	    	throw new IllegalArgumentException();
	    }
	    //确保并发级别不大于限定值
	    if (concurrencyLevel > MAX_SEGMENTS) {
	    	concurrencyLevel = MAX_SEGMENTS;
	    }
	    int sshift = 0;
	    int ssize = 1;
	    //保证ssize为2的幂, 且是最接近的大于等于并发级别的数
	    while (ssize < concurrencyLevel) {
	        ++sshift;
	        ssize <<= 1;
	    }
	    //计算分段锁的移位值
	    this.segmentShift = 32 - sshift;
	    //计算分段锁的掩码值
	    this.segmentMask = ssize - 1;
	    //总的初始容量不能大于限定值
	    if (initialCapacity > MAXIMUM_CAPACITY) {
	    	initialCapacity = MAXIMUM_CAPACITY;
	    }
	    //获取每个分段锁的初始容量
	    int c = initialCapacity / ssize;
	    //分段锁容量总和不小于初始总容量
	    if (c * ssize < initialCapacity) {
	    	++c;
	    }
	    int cap = MIN_SEGMENT_TABLE_CAPACITY;
	    //保证cap为2的幂, 且是最接近的大于等于c的数
	    while (cap < c) {
	    	cap <<= 1;
	    }
	    //新建一个Segment对象模版
	    Segment<K,V> s0 = new Segment<K,V>(loadFactor, (int)(cap * loadFactor), (HashEntry<K,V>[])new HashEntry[cap]);
	    //新建指定大小的分段锁数组
	    Segment<K,V>[] ss = (Segment<K,V>[])new Segment[ssize];
	    //使用UnSafe给数组第0个元素赋值
	    UNSAFE.putOrderedObject(ss, SBASE, s0);
	    this.segments = ss;
	}
    
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
    }
    
    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }
    
    public ConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }
    
    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_INITIAL_CAPACITY),
             DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
        putAll(m);
    }
    
	//向集合添加键值对(若存在则替换)
	@SuppressWarnings("unchecked")
	public V put(K key, V value) {
	    Segment<K,V> s;
	    //传入的value不能为空
	    if (value == null) throw new NullPointerException();
	    //使用哈希函数计算哈希码
	    int hash = hash(key);
	    //根据哈希码计算分段锁的下标
	    int j = (hash >>> segmentShift) & segmentMask;
	    //根据下标去尝试获取分段锁
	    if ((s = (Segment<K,V>)UNSAFE.getObject(segments, (j << SSHIFT) + SBASE)) == null) {
	    	//获得的分段锁为空就去构造一个
	    	s = ensureSegment(j);
	    }
	    //调用分段锁的put方法
	    return s.put(key, hash, value, false);
	}
	
	//向集合添加键值对(不存在才添加)
	@SuppressWarnings("unchecked")
	public V putIfAbsent(K key, V value) {
	    Segment<K,V> s;
	    //传入的value不能为空
	    if (value == null) throw new NullPointerException();
	    //使用哈希函数计算哈希码
	    int hash = hash(key);
	    //根据哈希码计算分段锁的下标
	    int j = (hash >>> segmentShift) & segmentMask;
	    //根据下标去尝试获取分段锁
	    if ((s = (Segment<K,V>)UNSAFE.getObject(segments, (j << SSHIFT) + SBASE)) == null) {
	    	//获得的分段锁为空就去构造一个
	    	s = ensureSegment(j);
	    }
	    //调用分段锁的put方法
	    return s.put(key, hash, value, true);
	}
    
    //批量添加键值对
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }
    
	//删除指定元素(找到对应元素后直接删除)
	public V remove(Object key) {
		//使用哈希函数计算哈希码
	    int hash = hash(key);
	    //根据哈希码获取分段锁的索引
	    Segment<K,V> s = segmentForHash(hash);
	    //调用分段锁的remove方法
	    return s == null ? null : s.remove(key, hash, null);
	}
	
	//删除指定元素(查找值等于给定值才删除)
	public boolean remove(Object key, Object value) {
		//使用哈希函数计算哈希码
	    int hash = hash(key);
	    Segment<K,V> s;
	    //确保分段锁不为空才调用remove方法
	    return value != null && (s = segmentForHash(hash)) != null && s.remove(key, hash, value) != null;
	}
    
	//替换指定元素(CAS操作)
	public boolean replace(K key, V oldValue, V newValue) {
		//使用哈希函数计算哈希码
	    int hash = hash(key);
	    //保证oldValue和newValue不为空
	    if (oldValue == null || newValue == null) throw new NullPointerException();
	    //根据哈希码获取分段锁的索引
	    Segment<K,V> s = segmentForHash(hash);
	    //调用分段锁的replace方法
	    return s != null && s.replace(key, hash, oldValue, newValue);
	}
    
	//替换指定元素
	public V replace(K key, V value) {
		//使用哈希函数计算哈希码
	    int hash = hash(key);
	    //保证value不为空
	    if (value == null) throw new NullPointerException();
	    //根据哈希码获取分段锁的索引
	    Segment<K,V> s = segmentForHash(hash);
	    //调用分段锁的replace方法
	    return s == null ? null : s.replace(key, hash, value);
	}
    
	//根据key获取value
	public V get(Object key) {
	    Segment<K,V> s;
	    HashEntry<K,V>[] tab;
	    //使用哈希函数计算哈希码
	    int h = hash(key);
	    //根据哈希码计算分段锁的索引
	    long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
	    //获取分段锁和对应的哈希表
	    if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null && (tab = s.table) != null) {
	        //根据哈希码获取链表头结点, 再对链表进行遍历
	    	for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
	                 (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
	             e != null; e = e.next) {
	            K k;
	            //根据key和hash找到对应元素后返回value值
	            if ((k = e.key) == key || (e.hash == h && key.equals(k))) {
	            	return e.value;
	            }
	        }
	    }
	    return null;
	}

    //判断集合是否为空
    public boolean isEmpty() {
        long sum = 0L;
        final Segment<K,V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K,V> seg = segmentAt(segments, j);
            if (seg != null) {
                if (seg.count != 0)
                    return false;
                sum += seg.modCount;
            }
        }
        if (sum != 0L) {
            for (int j = 0; j < segments.length; ++j) {
                Segment<K,V> seg = segmentAt(segments, j);
                if (seg != null) {
                    if (seg.count != 0)
                        return false;
                    sum -= seg.modCount;
                }
            }
            if (sum != 0L)
                return false;
        }
        return true;
    }
    
    //获取集合元素大小
    public int size() {
        final Segment<K,V>[] segments = this.segments;
        int size;
        boolean overflow;
        long sum;
        long last = 0L;
        int retries = -1;
        try {
            for (;;) {
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j) {
                    	ensureSegment(j).lock();
                    }
                }
                sum = 0L;
                size = 0;
                overflow = false;
                for (int j = 0; j < segments.length; ++j) {
                    Segment<K,V> seg = segmentAt(segments, j);
                    if (seg != null) {
                        sum += seg.modCount;
                        int c = seg.count;
                        if (c < 0 || (size += c) < 0) {
                        	overflow = true;
                        }
                    }
                }
                if (sum == last) {
                	break;
                }
                last = sum;
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j) {
                	segmentAt(segments, j).unlock();
                }
            }
        }
        return overflow ? Integer.MAX_VALUE : size;
    }
    
    //查询是否存在指定的key
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        Segment<K,V> s;
        HashEntry<K,V>[] tab;
        int h = hash(key);
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null && (tab = s.table) != null) {
            for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
                     (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null; e = e.next) {
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k))) {
                	return true;
                }
            }
        }
        return false;
    }
    
    
    //查询是否存在指定的value
    public boolean contains(Object value) {
        return containsValue(value);
    }
    
    //查询是否存在指定的value
    public boolean containsValue(Object value) {
        if (value == null) throw new NullPointerException();
        final Segment<K,V>[] segments = this.segments;
        boolean found = false;
        long last = 0;
        int retries = -1;
        try {
            outer: 
            for (;;) {
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j) {
                    	ensureSegment(j).lock();
                    }
                }
                long hashSum = 0L;
                int sum = 0;
                for (int j = 0; j < segments.length; ++j) {
                    HashEntry<K,V>[] tab;
                    Segment<K,V> seg = segmentAt(segments, j);
                    if (seg != null && (tab = seg.table) != null) {
                        for (int i = 0 ; i < tab.length; i++) {
                            HashEntry<K,V> e;
                            for (e = entryAt(tab, i); e != null; e = e.next) {
                                V v = e.value;
                                if (v != null && value.equals(v)) {
                                    found = true;
                                    break outer;
                                }
                            }
                        }
                        sum += seg.modCount;
                    }
                }
                if (retries > 0 && sum == last) {
                	break;
                }
                last = sum;
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j) {
                	segmentAt(segments, j).unlock();
                }
            }
        }
        return found;
    }
    
    //清空所有元素
    public void clear() {
        final Segment<K,V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K,V> s = segmentAt(segments, j);
            if (s != null) s.clear();
        }
    }
    
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }
    
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }
    
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }
    
    public Enumeration<K> keys() {
        return new KeyIterator();
    }
    
    public Enumeration<V> elements() {
        return new ValueIterator();
    }
    
    /********************************************迭代器支持操作**********************************************/

    abstract class HashIterator {
        int nextSegmentIndex;
        int nextTableIndex;
        HashEntry<K,V>[] currentTable;
        HashEntry<K, V> nextEntry;
        HashEntry<K, V> lastReturned;

        HashIterator() {
            nextSegmentIndex = segments.length - 1;
            nextTableIndex = -1;
            advance();
        }
        
        final void advance() {
            for (;;) {
                if (nextTableIndex >= 0) {
                    if ((nextEntry = entryAt(currentTable, nextTableIndex--)) != null) {
                    	break;
                    }
                } else if (nextSegmentIndex >= 0) {
                    Segment<K,V> seg = segmentAt(segments, nextSegmentIndex--);
                    if (seg != null && (currentTable = seg.table) != null) {
                    	nextTableIndex = currentTable.length - 1;
                    }
                } else {
                	break;
                }
                    
            }
        }

        final HashEntry<K,V> nextEntry() {
            HashEntry<K,V> e = nextEntry;
            if (e == null) throw new NoSuchElementException();
            lastReturned = e;
            if ((nextEntry = e.next) == null) {
            	advance();
            }
            return e;
        }

        public final boolean hasNext() { return nextEntry != null; }
        public final boolean hasMoreElements() { return nextEntry != null; }

        public final void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            ConcurrentHashMap.this.remove(lastReturned.key);
            lastReturned = null;
        }
    }

    final class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
        public final K next()        { return super.nextEntry().key; }
        public final K nextElement() { return super.nextEntry().key; }
    }

    final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
        public final V next()        { return super.nextEntry().value; }
        public final V nextElement() { return super.nextEntry().value; }
    }

    final class WriteThroughEntry extends AbstractMap.SimpleEntry<K,V> {
        WriteThroughEntry(K k, V v) {
            super(k,v);
        }
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = super.setValue(value);
            ConcurrentHashMap.this.put(getKey(), value);
            return v;
        }
    }

    final class EntryIterator extends HashIterator implements Iterator<Entry<K,V>> {
        public Map.Entry<K,V> next() {
            HashEntry<K,V> e = super.nextEntry();
            return new WriteThroughEntry(e.key, e.value);
        }
    }

    final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return ConcurrentHashMap.this.remove(o) != null;
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsValue(o);
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            V v = ConcurrentHashMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            return ConcurrentHashMap.this.remove(e.getKey(), e.getValue());
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    /**************************************序列化支持操作*********************************************/

    private void writeObject(jdk7.io.ObjectOutputStream s) throws IOException {
        for (int k = 0; k < segments.length; ++k) {
        	ensureSegment(k);
        }
        s.defaultWriteObject();

        final Segment<K,V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segmentAt(segments, k);
            seg.lock();
            try {
                HashEntry<K,V>[] tab = seg.table;
                for (int i = 0; i < tab.length; ++i) {
                    HashEntry<K,V> e;
                    for (e = entryAt(tab, i); e != null; e = e.next) {
                        s.writeObject(e.key);
                        s.writeObject(e.value);
                    }
                }
            } finally {
                seg.unlock();
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }
    
    @SuppressWarnings("unchecked")
    private void readObject(jdk7.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField oisFields = s.readFields();
        final Segment<K,V>[] oisSegments = (Segment<K,V>[])oisFields.get("segments", null);

        final int ssize = oisSegments.length;
        if (ssize < 1 || ssize > MAX_SEGMENTS || (ssize & (ssize-1)) != 0 ) {
        	throw new java.io.InvalidObjectException("Bad number of segments:" + ssize);
        }
        int sshift = 0, ssizeTmp = ssize;
        while (ssizeTmp > 1) {
            ++sshift;
            ssizeTmp >>>= 1;
        }
        UNSAFE.putIntVolatile(this, SEGSHIFT_OFFSET, 32 - sshift);
        UNSAFE.putIntVolatile(this, SEGMASK_OFFSET, ssize - 1);
        UNSAFE.putObjectVolatile(this, SEGMENTS_OFFSET, oisSegments);
        UNSAFE.putIntVolatile(this, HASHSEED_OFFSET, randomHashSeed(this));

        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        final Segment<K,V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segments[k];
            if (seg != null) {
                seg.threshold = (int)(cap * seg.loadFactor);
                seg.table = (HashEntry<K,V>[]) new HashEntry[cap];
            }
        }
        
        for (;;) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }
    
    private static final sun.misc.Unsafe UNSAFE;
    private static final long SBASE;      //Segment数组对象头偏移量
    private static final int SSHIFT;      //Segment数组元素偏移量
    private static final long TBASE;      //HashEntry数组对象头偏移量
    private static final int TSHIFT;      //HashEntry数组元素偏移量
    private static final long HASHSEED_OFFSET;
    private static final long SEGSHIFT_OFFSET;
    private static final long SEGMASK_OFFSET;
    private static final long SEGMENTS_OFFSET;

    static {
        int ss, ts;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class tc = HashEntry[].class;
            Class sc = Segment[].class;
            TBASE = UNSAFE.arrayBaseOffset(tc);  //获取数组对象头的偏移量
            SBASE = UNSAFE.arrayBaseOffset(sc);  //获取数组对象头的偏移量
            ts = UNSAFE.arrayIndexScale(tc);     //获取数组元素的大小
            ss = UNSAFE.arrayIndexScale(sc);     //获取数组元素的大小
            HASHSEED_OFFSET = UNSAFE.objectFieldOffset(ConcurrentHashMap.class.getDeclaredField("hashSeed"));
            SEGSHIFT_OFFSET = UNSAFE.objectFieldOffset(ConcurrentHashMap.class.getDeclaredField("segmentShift"));
            SEGMASK_OFFSET = UNSAFE.objectFieldOffset(ConcurrentHashMap.class.getDeclaredField("segmentMask"));
            SEGMENTS_OFFSET = UNSAFE.objectFieldOffset(ConcurrentHashMap.class.getDeclaredField("segments"));
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((ss & (ss-1)) != 0 || (ts & (ts-1)) != 0) {
        	throw new Error("data type scale not a power of two");
        }
        SSHIFT = 31 - Integer.numberOfLeadingZeros(ss);
        TSHIFT = 31 - Integer.numberOfLeadingZeros(ts);
    }

}
