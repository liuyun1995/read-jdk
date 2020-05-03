package jdk7.lang.reflect;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import jdk6.util.concurrent.ConcurrentHashMap;

final class WeakCache<K, P, V> {

	//Reference引用队列
	private final ReferenceQueue<K> refQueue = new ReferenceQueue<>();
	//缓存的底层实现, key为一级缓存, value为二级缓存。 为了支持null, map的key类型设置为Object
	private final ConcurrentMap<Object, ConcurrentMap<Object, Supplier<V>>> 
	                                                       map = new ConcurrentHashMap<>();
	//reverseMap记录了所有代理类生成器是否可用, 这是为了实现缓存的过期机制
	private final ConcurrentMap<Supplier<V>, Boolean> reverseMap = new ConcurrentHashMap<>();
	//生成二级缓存key的工厂, 这里传入的是KeyFactory
	private final BiFunction<K, P, ?> subKeyFactory;
	//生成二级缓存value的工厂, 这里传入的是ProxyClassFactory
	private final BiFunction<K, P, V> valueFactory;
	
	//构造器, 传入生成二级缓存key的工厂和生成二级缓存value的工厂
	public WeakCache(BiFunction<K, P, ?> subKeyFactory, BiFunction<K, P, V> valueFactory) {
	    this.subKeyFactory = Objects.requireNonNull(subKeyFactory);
	    this.valueFactory = Objects.requireNonNull(valueFactory);
	}

    
	public V get(K key, P parameter) {
		//这里要求实现的接口不能为空
	    Objects.requireNonNull(parameter);
	    //清除过期的缓存
	    expungeStaleEntries();
	    //将ClassLoader包装成CacheKey, 作为一级缓存的key
	    Object cacheKey = CacheKey.valueOf(key, refQueue);
	    //获取得到二级缓存
	    ConcurrentMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
	    //如果根据ClassLoader没有获取到对应的值
	    if (valuesMap == null) {
	    	//以CAS方式放入, 如果不存在则放入，否则返回原先的值
	        ConcurrentMap<Object, Supplier<V>> oldValuesMap = map.putIfAbsent(cacheKey, 
	        		valuesMap = new ConcurrentHashMap<>());
	        //如果oldValuesMap有值, 说明放入失败
	        if (oldValuesMap != null) {
	            valuesMap = oldValuesMap;
	        }
	    }
	    //根据代理类实现的接口数组来生成二级缓存key, 分为key0, key1, key2, keyx
	    Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
	    //这里通过subKey获取到二级缓存的值
	    Supplier<V> supplier = valuesMap.get(subKey);
	    Factory factory = null;
	    //这个循环提供了轮询机制, 如果条件为假就继续重试直到条件为真为止
	    while (true) {
	    	//如果通过subKey取出来的值不为空
	        if (supplier != null) {
	        	//在这里supplier可能是一个Factory也可能会是一个CacheValue
	        	//在这里不作判断, 而是在Supplier实现类的get方法里面进行验证
	            V value = supplier.get();
	            if (value != null) {
	                return value;
	            }
	        }
	        if (factory == null) {
	        	//新建一个Factory实例作为subKey对应的值
	            factory = new Factory(key, parameter, subKey, valuesMap);
	        }
	        if (supplier == null) {
	        	//到这里表明subKey没有对应的值, 就将factory作为subKey的值放入
	            supplier = valuesMap.putIfAbsent(subKey, factory);
	            if (supplier == null) {
	                //到这里表明成功将factory放入缓存
	                supplier = factory;
	            }
	            //否则, 可能期间有其他线程修改了值, 那么就不再继续给subKey赋值, 而是取出来直接用
	        } else {
	        	//期间可能其他线程修改了值, 那么就将原先的值替换
	            if (valuesMap.replace(subKey, supplier, factory)) {
	            	//成功将factory替换成新的值
	                supplier = factory;
	            } else {
	            	//替换失败, 继续使用原先的值
	                supplier = valuesMap.get(subKey);
	            }
	        }
	    }
	}

    /**
     * Checks whether the specified non-null value is already present in this
     * {@code WeakCache}. The check is made using identity comparison regardless
     * of whether value's class overrides {@link Object#equals} or not.
     *
     * @param value the non-null value to check
     * @return true if given {@code value} is already cached
     * @throws NullPointerException if value is null
     */
    public boolean containsValue(V value) {
        Objects.requireNonNull(value);

        expungeStaleEntries();
        return reverseMap.containsKey(new LookupValue<>(value));
    }

    /**
     * Returns the current number of cached entries that
     * can decrease over time when keys/values are GC-ed.
     */
    public int size() {
        expungeStaleEntries();
        return reverseMap.size();
    }

    //移除过期缓存的方法
    private void expungeStaleEntries() {
        CacheKey<K> cacheKey;
        //遍历引用队列逐个进行检验清除
        while ((cacheKey = (CacheKey<K>)refQueue.poll()) != null) {
            cacheKey.expungeFrom(map, reverseMap);
        }
    }

    
private final class Factory implements Supplier<V> {
	//一级缓存key, 根据ClassLoader生成
    private final K key;
    //代理类实现的接口数组
    private final P parameter;
    //二级缓存key, 根据接口数组生成
    private final Object subKey;
    //二级缓存
    private final ConcurrentMap<Object, Supplier<V>> valuesMap;

    Factory(K key, P parameter, Object subKey,
            ConcurrentMap<Object, Supplier<V>> valuesMap) {
        this.key = key;
        this.parameter = parameter;
        this.subKey = subKey;
        this.valuesMap = valuesMap;
    }

    @Override
    public synchronized V get() {
        //这里再一次去二级缓存里面获取Supplier, 用来验证是否是Factory本身
        Supplier<V> supplier = valuesMap.get(subKey);
        if (supplier != this) {
        	//在这里验证supplier是否是Factory实例本身, 如果不则返回null让调用者继续轮询重试
        	//期间supplier可能替换成了CacheValue, 或者由于生成代理类失败被从二级缓存中移除了
            return null;
        }
        V value = null;
        try {
        	//委托valueFactory去生成代理类, 这里会通过传入的ProxyClassFactory去生成代理类
            value = Objects.requireNonNull(valueFactory.apply(key, parameter));
        } finally {
        	//如果生成代理类失败, 就将这个二级缓存删除
            if (value == null) {
                valuesMap.remove(subKey, this);
            }
        }
        //只有value的值不为空才能到达这里
        assert value != null;
        //使用弱引用包装生成的代理类
        CacheValue<V> cacheValue = new CacheValue<>(value);
        //将包装后的cacheValue放入二级缓存中, 这个操作必须成功, 否则就报错
        if (valuesMap.replace(subKey, this, cacheValue)) {
        	//将cacheValue成功放入二级缓存后, 再对它进行标记
            reverseMap.put(cacheValue, Boolean.TRUE);
        } else {
            throw new AssertionError("Should not reach here");
        }
        //最后返回没有被弱引用包装的代理类
        return value;
    }
}

    /**
     * Common type of value suppliers that are holding a referent.
     * The {@link #equals} and {@link #hashCode} of implementations is defined
     * to compare the referent by identity.
     */
    //Value继承了提供者, 提供者也可以是Factory
    private interface Value<V> extends Supplier<V> {}

    /**
     * An optimized {@link Value} used to look-up the value in
     * {@link WeakCache#containsValue} method so that we are not
     * constructing the whole {@link CacheValue} just to look-up the referent.
     */
    //一个最优化的手段去寻找值在containsValue方法中, 可以不用构造整个CacheValue就找到referent
    private static final class LookupValue<V> implements Value<V> {
        private final V value;

        LookupValue(V value) {
            this.value = value;
        }

        @Override
        public V get() {
            return value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value); // compare by identity
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this ||
                   obj instanceof Value &&
                   this.value == ((Value<?>) obj).get();  // compare by identity
        }
    }

    /**
     * A {@link Value} that weakly references the referent.
     */
    //CacheValue继承自弱引用
    private static final class CacheValue<V> extends WeakReference<V> implements Value<V> {
        
    	private final int hash;

        CacheValue(V value) {
            super(value);
            this.hash = System.identityHashCode(value); // compare by identity
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            V value;
            return obj == this ||
                   obj instanceof Value &&
                   // cleared CacheValue is only equal to itself
                   (value = get()) != null &&
                   value == ((Value<?>) obj).get(); // compare by identity
        }
    }

    /**
     * CacheKey containing a weakly referenced {@code key}. It registers
     * itself with the {@code refQueue} so that it can be used to expunge
     * the entry when the {@link WeakReference} is cleared.
     */
    //CacheKey继承自弱引用
    //CacheKey包含了一个弱引用, 它会将自己注册进refQueue, 所以它可以用来删除实体, 当这个弱引用被清除
    private static final class CacheKey<K> extends WeakReference<K> {
    	
    	//当key为null时,替代key作为cache key
        private static final Object NULL_KEY = new Object();

        static <K> Object valueOf(K key, ReferenceQueue<K> refQueue) {
        	//空的key意味着我们不能对它进行弱引用, 所以用单例的NULL_KEY作为cache key
        	//如果key不为空就用弱引用包装它
            return key == null ? NULL_KEY: new CacheKey<>(key, refQueue);
        }

        private final int hash;

        private CacheKey(K key, ReferenceQueue<K> refQueue) {
            super(key, refQueue);
            this.hash = System.identityHashCode(key);  // compare by identity
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            K key;
            return obj == this ||
                   obj != null &&
                   obj.getClass() == this.getClass() &&
                   // cleared CacheKey is only equal to itself
                   (key = this.get()) != null &&
                   // compare key by identity
                   key == ((CacheKey<K>) obj).get();
        }

        void expungeFrom(ConcurrentMap<?, ? extends ConcurrentMap<?, ?>> map,
                         ConcurrentMap<?, Boolean> reverseMap) {
            // removing just by key is always safe here because after a CacheKey
            // is cleared and enqueue-ed it is only equal to itself
            // (see equals method)...
        	//传进来的map就是缓存map
            ConcurrentMap<?, ?> valuesMap = map.remove(this);
            //如果移除的一级缓存有二级缓存
            if (valuesMap != null) {
            	//如果二级缓存不为空就遍历它的所有value,  
                for (Object cacheValue : valuesMap.values()) {
                    reverseMap.remove(cacheValue);
                }
            }
        }
    }
}
